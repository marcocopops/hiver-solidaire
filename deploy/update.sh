#!/usr/bin/env bash
# Mise à jour en pull, lancée par cron sur le homelab (deploy/README.md, « Mise à jour automatique ») :
#   */10 * * * * /opt/hiver-solidaire/update.sh >> /var/log/hiver-solidaire/update.log 2>&1
#
#   update.sh          tire l'image d'APP_TAG, relance ce qui a changé, puis compare les fichiers du serveur à ceux de
#                      la version en service
#   update.sh --diff   montre seulement les écarts (diff -u), sans rien tirer ni relancer
#
# Aucune clé du homelab n'est stockée dans GitHub : c'est le serveur qui tire l'image. Pendant la saison, APP_TAG
# épingle une version (sha-<commit>@sha256:<empreinte>) : rien ne change sans un geste volontaire (runbook).
#
# Fichiers de déploiement : l'image contient ceux de sa version (/app/deploy). Un écart avec ceux du serveur est
# signalé (log, ping d'échec à healthchecks.io) mais jamais corrigé ici : remplacer automatiquement un script qui
# tourne avec les droits Docker donnerait l'hôte à quiconque contrôle l'image (ADR-0015).
#
# Silencieux tant que rien ne change : une ligne quand une version est déployée, quand l'écart change, ou en cas
# d'erreur.
set -euo pipefail
umask 077

script_dir=$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)
cd "$script_dir"
# shellcheck source=deploy/lib.sh
. "$script_dir/lib.sh"
export COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"

# Dernier écart signalé : il n'est réécrit dans le log que lorsqu'il change.
readonly DRIFT_STATE="${HS_UPDATE_STATE:-$script_dir/.update-drift}"
# Code de sortie quand la mise à jour a réussi mais que des fichiers divergent : ping d'échec, donc alerte.
readonly DRIFT_STATUS=3

reference_dir=""

# Fichiers de déploiement de l'image en service, copiés depuis le conteneur app (qui n'est pas modifié).
fetch_reference() {
    local app_id
    app_id=$(docker compose ps -q app)
    [[ -n $app_id ]] || die "conteneur app introuvable : impossible de comparer les fichiers de déploiement"
    reference_dir=$(mktemp -d)
    docker cp --quiet "$app_id:/app/deploy/." "$reference_dir/" \
        || die "cette version de l'image ne contient pas /app/deploy (antérieure à S6)"
}

# Fichiers de l'image absents ou différents sur le serveur, un par ligne. Seuls des fichiers ordinaires sont comparés :
# un lien symbolique venu de l'image ferait lire un fichier de l'hôte (et l'afficher par --diff). Il est signalé.
drifted_files() {
    local file name
    for file in "$reference_dir"/*; do
        name=$(basename "$file")
        if [[ -L $file || ! -f $file ]]; then
            printf '%s (pas un fichier ordinaire : ne pas copier)\n' "$name"
        elif [[ ! -f $name ]] || ! cmp -s "$file" "$name"; then
            printf '%s\n' "$name"
        fi
    done
}

if [[ ${1:-} == --diff && $# -eq 1 ]]; then
    trap '[[ -z $reference_dir ]] || rm -rf -- "$reference_dir"' EXIT
    fetch_reference
    drift=$(drifted_files)
    if [[ -z $drift ]]; then
        echo "Aucun écart : les fichiers du serveur sont ceux de la version en service."
        exit 0
    fi
    while IFS= read -r name; do
        [[ -f $reference_dir/$name && ! -L $reference_dir/$name ]] || { printf '%s\n' "$name"; continue; }
        diff -u --label "serveur/$name" --label "version en service/$name" \
            "$( [[ -f $name ]] && echo "$name" || echo /dev/null)" "$reference_dir/$name" || true
    done <<<"$drift"
    exit 0
elif [[ $# -gt 0 ]]; then
    die "usage : update.sh [--diff]"
fi

ping_url=${UPDATE_PING_URL:-$(env_value UPDATE_PING_URL .env)}

finish() {
    local status=$?
    [[ -z $reference_dir ]] || rm -rf -- "$reference_dir"
    if ((status != 0 && status != DRIFT_STATUS)); then
        log "ÉCHEC de la mise à jour (code ${status})"
    fi
    ping_monitor "$ping_url" "$status"
    exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

# Une seule mise à jour à la fois ; restore.sh --production prend aussi ce verrou, pour que l'appli ne soit pas
# relancée pendant une restauration.
exec 9<"${BASH_SOURCE[0]}"
if ! flock -n 9; then
    exit 0
fi

# Conteneurs en service et leur image, pour savoir si quelque chose a changé.
running_images() {
    docker compose ps -q | sort | xargs -r docker inspect --format '{{.Name}} {{.Image}}'
}

before=$(running_images)
if ! output=$(docker compose pull --quiet 2>&1); then
    # Un incident passager du registre ne doit pas alerter : un second essai, puis l'échec.
    sleep 30
    output=$(docker compose pull --quiet 2>&1) || { printf '%s\n' "$output"; die "image impossible à tirer"; }
fi
output=$(docker compose up -d --remove-orphans 2>&1) || { printf '%s\n' "$output"; die "docker compose up a échoué"; }
after=$(running_images)
if [[ $before != "$after" ]]; then
    log "Conteneurs relancés (APP_TAG=$(env_value APP_TAG .env)) :"
    printf '%s\n' "$after"
fi
docker image prune -f >/dev/null

fetch_reference
drift=$(drifted_files)
previous=$(cat "$DRIFT_STATE" 2>/dev/null || true)
if [[ $drift != "$previous" ]]; then
    if [[ -n $drift ]]; then
        log "ÉCART : fichiers du serveur différents de la version en service (voir : ./update.sh --diff) :"
        while IFS= read -r name; do
            printf '  %s\n' "$name"
        done <<<"$drift"
    else
        log "Plus d'écart : les fichiers du serveur sont ceux de la version en service."
    fi
    printf '%s' "$drift" >"$DRIFT_STATE"
fi
if [[ -n $drift ]]; then
    exit "$DRIFT_STATUS"
fi
