#!/usr/bin/env bash
# Mise à jour en pull, lancée par cron sur le homelab (voir deploy/README.md).
# Aucune clé du homelab n'est stockée dans GitHub : c'est le serveur qui tire l'image.
set -euo pipefail

cd "$(dirname "$0")"

docker compose -f compose.prod.yaml pull
docker compose -f compose.prod.yaml up -d --remove-orphans
docker image prune -f >/dev/null
