#!/usr/bin/env bash
# Restauration d'une sauvegarde restic (cahier des charges §8, ADR-0016, runbook « Restaurer la base »).
#
#   restore.sh [SAUVEGARDE]                 contrôle : restaure dans une base jetable, vérifie, efface (par défaut)
#   restore.sh --production [SAUVEGARDE]    remplace la base de production, après confirmation et sauvegarde préalable
#
# SAUVEGARDE : identifiant restic (« restic snapshots », voir le runbook), « latest » par défaut.
#
# Le contrôle restaure dans un PostgreSQL jetable (même image que la production, sans réseau, données en mémoire,
# supprimé à la fin) : la production n'est pas touchée. Le dump passe de restic à pg_restore par un tube, sans
# jamais être écrit sur le disque.
set -euo pipefail
umask 077

script_dir=$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)
cd "$script_dir"
# shellcheck source=deploy/lib.sh
. "$script_dir/lib.sh"
export COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"

readonly CHECK_SQL="$script_dir/restore-check.sql"
# Au-delà, la dernière sauvegarde est trop vieille : la sauvegarde de la nuit n'a pas tourné.
readonly MAX_LATEST_AGE_HOURS=26

production=false
if [[ ${1:-} == --production ]]; then
    production=true
    shift
fi
[[ $# -le 1 && ${1:-} != -* ]] || die "usage : restore.sh [--production] [SAUVEGARDE]"
requested=${1:-latest}

scratch=""
app_stopped=false
database_dropped=false

finish() {
    local status=$?
    if [[ -n $scratch ]]; then
        docker rm -f "$scratch" >/dev/null 2>&1 || true
    fi
    if ((status != 0)) && $database_dropped; then
        log "ÉCHEC après l'effacement de la base de production : l'appli reste ARRÊTÉE."
        log "Relancer la restauration (runbook « Restaurer la base ») avec une autre sauvegarde, par exemple ${pre_backup:-la précédente}."
    elif ((status != 0)) && $app_stopped; then
        log "ÉCHEC : la base n'a pas été effacée, redémarrage de l'appli."
        docker compose up -d app || true
    fi
    exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

DB_CONTAINER=$(db_container)

# Sauvegarde demandée, résolue en identifiant fixe : la sauvegarde préalable du mode production deviendrait sinon
# « latest ».
snapshot_json=$(restic_run -- snapshots --json --host "$BACKUP_HOST" "$requested")
snapshot=$(grep -o '"short_id":"[^"]*"' <<<"$snapshot_json" | head -n 1 | cut -d'"' -f4)
snapshot_time=$(grep -o '"time":"[^"]*"' <<<"$snapshot_json" | head -n 1 | cut -d'"' -f4)
[[ -n $snapshot ]] || die "sauvegarde introuvable : ${requested}"
age_hours=$((($(date +%s) - $(date -d "$snapshot_time" +%s)) / 3600))
log "Sauvegarde ${snapshot} du ${snapshot_time} (il y a ${age_hours} h)"

# Mesures de restore-check.sql, « mesure=valeur », sur la base de production ou sur la base jetable.
production_report() {
    db_psql -At -F= <"$CHECK_SQL"
}
scratch_report() {
    docker exec -i "$scratch" psql -X -q -U restore -d restore -v ON_ERROR_STOP=1 -At -F= <"$CHECK_SQL"
}

# Compare les mesures et refuse une base sans historique Flyway, avec une migration en échec ou sans soirée.
verify() {
    local restored=$1 reference=$2 reference_label=$3
    printf '%-16s %12s %12s\n' mesure sauvegarde "$reference_label"
    local key value
    while IFS='=' read -r key value; do
        printf '%-16s %12s %12s\n' "$key" "$value" "$(sed -n "s/^${key}=//p" <<<"$reference")"
    done <<<"$restored"
    [[ -n $(sed -n 's/^flyway_version=//p' <<<"$restored") ]] || die "aucune migration Flyway dans la sauvegarde"
    [[ $(sed -n 's/^flyway_failed=//p' <<<"$restored") == 0 ]] || die "migration Flyway en échec dans la sauvegarde"
    (($(sed -n 's/^evenings=//p' <<<"$restored") > 0)) || die "aucune soirée dans la sauvegarde"
}

restore_into_scratch() {
    local image
    image=$(docker inspect --format '{{.Config.Image}}' "$DB_CONTAINER")
    scratch="hiver-solidaire-restore-check-$$"
    log "Base jetable ${scratch} (${image}), sans réseau"
    # Sans réseau ni volume, détruite à la fin : l'authentification « trust » n'ouvre rien.
    docker run -d --rm --name "$scratch" --network none --read-only \
        --tmpfs /tmp --tmpfs /run/postgresql --user postgres \
        --cap-drop ALL --security-opt no-new-privileges --memory 512m --pids-limit 256 \
        -e PGDATA=/tmp/pgdata -e POSTGRES_USER=restore -e POSTGRES_DB=restore -e POSTGRES_HOST_AUTH_METHOD=trust \
        "$image" >/dev/null
    local attempt
    for attempt in $(seq 1 60); do
        # TCP local : le serveur provisoire de l'initialisation n'écoute que sur le socket.
        if docker exec "$scratch" pg_isready -q -h 127.0.0.1 -U restore -d restore; then
            break
        fi
        ((attempt < 60)) || die "la base jetable ne démarre pas"
        sleep 1
    done
    log "Restauration dans la base jetable"
    restic_run -- dump --host "$BACKUP_HOST" "$snapshot" "$DUMP_PATH" \
        | docker exec -i "$scratch" pg_restore -U restore -d restore --no-owner --no-privileges \
            --exit-on-error --single-transaction
}

if ! $production; then
    restore_into_scratch
    verify "$(scratch_report)" "$(production_report)" production
    if [[ $requested == latest ]] && ((age_hours > MAX_LATEST_AGE_HOURS)); then
        die "la dernière sauvegarde a ${age_hours} h : la sauvegarde de la nuit tourne-t-elle ?"
    fi
    log "Contrôle réussi : la sauvegarde ${snapshot} se restaure. Noter la date dans le runbook (« Tests de restauration »)."
    exit 0
fi

# --- Production ---------------------------------------------------------------------------------------------------
# shellcheck disable=SC2016 # variable développée dans le conteneur
database=$(db_exec sh -c 'printf %s "$POSTGRES_DB"')
log "ATTENTION : la base de production « ${database} » va être REMPLACÉE par la sauvegarde ${snapshot}."
log "Tout ce qui a été fait depuis le ${snapshot_time} sera perdu (inscriptions, comptes, envois)."
read -r -p "Pour confirmer, tapez le nom de la base (${database}) : " answer
[[ $answer == "$database" ]] || die "confirmation incorrecte, rien n'a été modifié"

log "Sauvegarde préalable de la base actuelle"
pre_backup=""
# Sans rétention : « forget » oublierait la sauvegarde du jour qu'on va restaurer.
if "$script_dir/backup.sh" --no-forget; then
    pre_backup=$(restic_run -- snapshots --json --host "$BACKUP_HOST" latest \
        | grep -o '"short_id":"[^"]*"' | head -n 1 | cut -d'"' -f4)
    log "Sauvegarde préalable : ${pre_backup}"
else
    log "La sauvegarde préalable a échoué (base illisible ?)."
    read -r -p "Pour restaurer quand même, sans sauvegarde de l'état actuel, tapez RESTAURER SANS SAUVEGARDE : " answer
    [[ $answer == "RESTAURER SANS SAUVEGARDE" ]] || die "abandon, rien n'a été modifié"
fi

log "Arrêt de l'appli"
app_stopped=true
docker compose stop app

log "Recréation de la base « ${database} »"
database_dropped=true
# Deux instructions hors transaction (DROP DATABASE l'exige). Nom pris dans le conteneur, cité par psql (:"db").
# shellcheck disable=SC2016 # variables développées dans le conteneur
db_pipe sh -c 'exec psql -X -q -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -v db="$POSTGRES_DB"' <<'SQL'
DROP DATABASE IF EXISTS :"db" WITH (FORCE);
CREATE DATABASE :"db";
SQL

log "Restauration de la sauvegarde ${snapshot}"
# shellcheck disable=SC2016 # variables développées dans le conteneur
restic_run -- dump --host "$BACKUP_HOST" "$snapshot" "$DUMP_PATH" \
    | db_pipe sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --single-transaction'
verify "$(production_report)" "" ""

log "Redémarrage de l'appli"
docker compose up -d app
database_dropped=false
app_stopped=false
app_id=$(docker compose ps -q app)
for attempt in $(seq 1 90); do
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$app_id")
    [[ $health == healthy ]] && break
    ((attempt < 90)) || die "l'appli n'est pas « healthy » après 3 minutes (docker compose logs app)"
    sleep 2
done
log "Restauration terminée : base revenue au ${snapshot_time}, appli en service. Sauvegarde préalable : ${pre_backup:-aucune}."
log "À faire : prévenir les coordinateurs (runbook), vérifier le planning et /sante."
