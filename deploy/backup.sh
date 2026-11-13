#!/usr/bin/env bash
# Sauvegarde quotidienne de la base, chiffrée par restic vers Cloudflare R2 (cahier des charges §8, ADR-0016).
# Lancée par cron sur le serveur, après la purge de 4 h 30 (deploy/README.md, « Sauvegardes ») :
#   15 5 * * * /opt/hiver-solidaire/backup.sh >> /var/log/hiver-solidaire/backup.log 2>&1
#
#   backup.sh          dump, envoi, rétention (7 quotidiennes, 4 hebdomadaires) ; vérification du dépôt le dimanche
#   backup.sh --init   crée le dépôt restic (une seule fois, à l'installation)
#   backup.sh --no-forget   sauvegarde sans rétention (avant une restauration : « forget » oublierait sinon la
#                           sauvegarde du jour qu'on va restaurer ; la sauvegarde de la nuit suivante fera le tri)
#
# Le dump en clair n'existe que dans /dev/shm (mémoire), le temps de l'envoi, et n'est jamais écrit sur le disque.
# Un échec, quel qu'il soit, envoie le code de sortie au service de surveillance (BACKUP_PING_URL dans .env).
# Secrets de restic et de R2 : backup.env (chmod 600), lu par le seul conteneur restic.
set -euo pipefail
umask 077

script_dir=$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)
cd "$script_dir"
# shellcheck source=deploy/lib.sh
. "$script_dir/lib.sh"
export COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"

# Vérification de l'intégrité du dépôt : « auto » le dimanche, « always » ou « never » (tests).
check_mode=${HS_BACKUP_CHECK:-auto}

forget=true
if [[ ${1:-} == --init && $# -eq 1 ]]; then
    log "Création du dépôt restic"
    restic_run -- init
    exit 0
elif [[ ${1:-} == --no-forget && $# -eq 1 ]]; then
    forget=false
elif [[ $# -gt 0 ]]; then
    die "usage : backup.sh [--init | --no-forget]"
fi

ping_url=${BACKUP_PING_URL:-$(env_value BACKUP_PING_URL .env)}
dump_dir=""

finish() {
    local status=$?
    if [[ -n $dump_dir ]]; then
        rm -rf -- "$dump_dir"
    fi
    if ((status == 0)); then
        log "Sauvegarde terminée"
    else
        log "ÉCHEC de la sauvegarde (code ${status})"
    fi
    ping_monitor "$ping_url" "$status"
    exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

[[ -n $ping_url ]] || log "Avertissement : BACKUP_PING_URL absent de .env, aucun échec ne sera signalé"
ping_monitor "$ping_url" start

# Une seule sauvegarde à la fois (verrou sur le script lui-même).
exec 9<"${BASH_SOURCE[0]}"
flock -n 9 || die "une sauvegarde est déjà en cours"

[[ $(stat -f -c %T /dev/shm) == tmpfs ]] \
    || die "/dev/shm n'est pas en mémoire (tmpfs) : le dump en clair irait sur le disque"
dump_dir=$(mktemp -d /dev/shm/hiver-solidaire-backup.XXXXXX)
dump_file="$dump_dir/$(basename "$DUMP_PATH")"

DB_CONTAINER=$(db_container)

log "Dump de la base"
# shellcheck disable=SC2016 # variables développées dans le conteneur
db_exec sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' >"$dump_file"
# Un dump tronqué ne doit pas devenir la dernière sauvegarde : on relit sa table des matières avant l'envoi.
db_pipe pg_restore --list <"$dump_file" >/dev/null || die "dump illisible par pg_restore"
log "Dump lu sans erreur ($(stat -c %s "$dump_file") octets)"

log "Envoi chiffré"
restic_run -v "$dump_dir:$(dirname "$DUMP_PATH"):ro" -- \
    backup --quiet --host "$BACKUP_HOST" --tag pg_dump "$DUMP_PATH"

if $forget; then
    # restic garde aussi la plus ancienne sauvegarde tant que 7 jours et 4 semaines ne sont pas remplis : au plus
    # 4 semaines civiles en arrière, sous les 30 jours annoncés par la notice.
    log "Rétention : 7 quotidiennes, 4 hebdomadaires"
    restic_run -- forget --quiet --host "$BACKUP_HOST" --keep-daily 7 --keep-weekly 4 --prune
fi

if [[ $check_mode == always || ($check_mode == auto && $(date +%u) == 7) ]]; then
    log "Vérification du dépôt (10 % des données relues)"
    restic_run -- check --read-data-subset=10%
fi
