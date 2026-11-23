# shellcheck shell=bash
# Fonctions communes à backup.sh, restore.sh et update.sh. Sourcé par ces scripts, jamais exécuté seul.
# Voir deploy/README.md (« Sauvegardes ») et docs/exploitation/runbook.md.

# Constantes lues par les scripts qui sourcent ce fichier.
# Nom d'hôte fixe des sauvegardes : restic regroupe les sauvegardes par hôte pour « forget » ; le nom aléatoire du
# conteneur créerait un groupe par sauvegarde, et rien ne serait jamais oublié.
# shellcheck disable=SC2034
readonly BACKUP_HOST=hiver-solidaire
# Chemin du dump dans le dépôt restic (monté en lecture seule dans le conteneur restic).
# shellcheck disable=SC2034
readonly DUMP_PATH=/dump/hiver-solidaire.dump

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"
}

die() {
    log "ERREUR : $*" >&2
    exit 1
}

# Valeur d'une variable d'un fichier « CLÉ=valeur » (.env), lue sans exécuter le fichier.
env_value() {
    local key=$1 file=$2
    [[ -r $file ]] || return 0
    sed -n "s/^${key}=//p" "$file" | tail -n 1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/"
}

# Ping d'un service de surveillance des tâches (healthchecks.io) : « start », puis le code de sortie (0 = succès).
# Seul le code part, jamais de log. Sans URL configurée, rien n'est envoyé. L'URL n'est jamais écrite dans le log.
ping_monitor() {
    local url=$1 suffix=$2
    [[ -n $url ]] || return 0
    curl -fsS -m 10 --retry 3 -o /dev/null "${url%/}/${suffix}" \
        || log "Avertissement : ping de surveillance impossible (${suffix})"
}

# Conteneur de la base de production (service « db »). HS_DB_CONTAINER le remplace dans les tests.
db_container() {
    if [[ -n ${HS_DB_CONTAINER:-} ]]; then
        printf '%s' "$HS_DB_CONTAINER"
        return
    fi
    local id
    id=$(docker compose ps -q db)
    [[ -n $id ]] || die "conteneur db introuvable : la base tourne-t-elle ? (docker compose ps)"
    printf '%s' "$id"
}

# Commande dans le conteneur de la base (DB_CONTAINER, fixé par l'appelant). Les variables POSTGRES_* sont celles du
# conteneur : le script ne lit jamais le mot de passe de la base. db_exec ne lit rien sur l'entrée standard (elle
# garde la réponse aux confirmations) ; db_pipe lui transmet son entrée.
db_exec() {
    docker exec "$DB_CONTAINER" "$@" </dev/null
}

db_pipe() {
    docker exec -i "$DB_CONTAINER" "$@"
}

# psql sur la base de production (requêtes sur l'entrée standard), en tant que son propriétaire, par le socket local.
db_psql() {
    # shellcheck disable=SC2016 # variables développées dans le conteneur
    db_pipe sh -c 'exec psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 "$@"' psql "$@"
}

# restic dans son conteneur (service « restic » de compose.prod.yaml, profil « backup »), sous l'utilisateur du
# script. Les options de « docker compose run » (montages) viennent avant « -- », les arguments de restic après.
# Aucune commande restic des scripts ne lit l'entrée standard : elle reste aux confirmations.
restic_run() {
    local run_options=()
    while [[ $# -gt 0 && $1 != -- ]]; do
        run_options+=("$1")
        shift
    done
    [[ ${1:-} == -- ]] && shift
    docker compose --profile backup run --rm --no-deps -T --quiet-pull --user "$(id -u):$(id -g)" \
        "${run_options[@]}" restic "$@" </dev/null
}
