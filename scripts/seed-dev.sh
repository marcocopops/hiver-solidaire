#!/usr/bin/env bash
# Remplit la base de DÉVELOPPEMENT avec un jeu de données factice (lot 1) : une vingtaine de bénévoles et environ 60 %
# des soirées des 4 premières semaines de la saison. Voir README.md, section « Données de test ».
#
#   scripts/seed-dev.sh           ajoute les données (idempotent : relancé, il ne double rien)
#   scripts/seed-dev.sh --reset   vide d'abord inscriptions, alertes, journal d'audit et bénévoles factices
#
# Garde-fous : ne s'exécute que dans le conteneur db du projet Compose hiver-solidaire-dev, et seulement sur une base
# migrée (contrôlé par le SQL). Ne lit jamais .env : psql prend POSTGRES_USER et POSTGRES_DB dans le conteneur.
set -euo pipefail

readonly EXPECTED_PROJECT="hiver-solidaire-dev"

usage() {
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
}

reset=false
case "${1:-}" in
    "") ;;
    --reset) reset=true ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
if [[ $# -gt 1 ]]; then
    usage >&2
    exit 2
fi

cd "$(dirname "$0")/.."

fail() {
    echo "seed-dev : $*" >&2
    exit 1
}

# Le conteneur dans lequel on va écrire, et non le seul fichier Compose : une variable COMPOSE_PROJECT_NAME ou un
# autre répertoire ne peut pas faire viser une autre base.
container=$(docker compose ps -q db) || fail "docker compose a échoué (message ci-dessus ; .env copié depuis .env.example ?)"
[[ -n "$container" ]] || fail "le conteneur db n'est pas lancé (docker compose up -d, puis démarrer l'appli une fois pour migrer la base)"
[[ $(wc -l <<<"$container") -eq 1 ]] || fail "plusieurs conteneurs db trouvés, refus"
project=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")
[[ "$project" == "$EXPECTED_PROJECT" ]] \
    || fail "projet Compose « $project » au lieu de « $EXPECTED_PROJECT » : refus (base de développement seulement)"

files=(scripts/seed-dev.sql)
if $reset; then
    files=(scripts/seed-dev-reset.sql scripts/seed-dev.sql)
fi

# Une seule transaction (reset compris) : un garde-fou ou un contrôle en échec annule tout. Les messages de psql sont
# affichés sans leur préfixe « psql:<stdin>:N: ».
set +e
cat "${files[@]}" | docker compose exec -T db sh -c \
    'exec psql -X -q -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>&1 \
    | sed -E 's/^psql:<stdin>:[0-9]+: //'
status=${PIPESTATUS[1]}
set -e
[[ $status -eq 0 ]] || fail "échec, aucune donnée écrite"
echo "seed-dev : terminé$($reset && echo ' (après remise à zéro)')."
