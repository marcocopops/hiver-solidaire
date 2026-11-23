# ADR-0015 — Signaler la divergence des fichiers de déploiement plutôt que les remplacer

- **Statut** : proposé
- **Date** : 2026-11-13

## Contexte

Le serveur tire l'image de l'appli depuis GHCR (`update.sh`, en pull, [ADR-0004](0004-homelab-et-cloudflare-tunnel-plutot-que-vps.md)), mais les fichiers de `deploy/` (`compose.prod.yaml`, scripts) y sont copiés à la main. Ils divergent sans qu'on le voie : en S5b, la nouvelle version attendait `TURNSTILE_SITE_KEY` et `TURNSTILE_SECRET_KEY`, que l'ancien `compose.prod.yaml` du serveur ne transmettait pas, et la connexion est restée « momentanément indisponible ».

Deux questions : contre quoi comparer les fichiers du serveur, et que faire d'un écart.

Contraintes : aucune clé du homelab dans GitHub, pas de runner auto-hébergé, `update.sh` tourne par cron avec les droits Docker (équivalents à root sur l'hôte), et pendant la saison la version est épinglée (`APP_TAG`) : `main` peut avancer sans être déployé.

## Options envisagées

### Référence

1. **`main` sur GitHub** (`raw.githubusercontent.com`) : un accès réseau de plus, une seconde source de confiance, et pendant l'épinglage un écart signalé dès qu'un fichier change sur `main`, même si la version en service ne l'attend pas.
2. **Les fichiers embarqués dans l'image en service** : le `Dockerfile` copie les fichiers de `deploy/` (liste blanche de `.dockerignore`) dans `/app/deploy`, en lecture seule ; `update.sh` les copie depuis le conteneur `app` et les compare. Même version que l'appli qui tourne, aucun accès réseau, aucune autre source de confiance que l'image déjà exécutée.

### Que faire d'un écart

1. **Remplacer automatiquement** les fichiers du serveur : plus de copie à la main. Mais `update.sh` exécuterait au cycle suivant un script venu de l'image, avec les droits Docker. Aujourd'hui, qui contrôle l'image (dépôt ou CI compromis, image GHCR remplacée, menace M17) exécute du code dans un conteneur durci (non-root, lecture seule, aucune capability, réseau dédié) ; avec le remplacement automatique, il exécuterait du code sur l'hôte en root. Le même raisonnement vaut pour `compose.prod.yaml`, qui peut monter n'importe quel chemin de l'hôte ou passer un conteneur en `privileged`.
2. **Signaler sans remplacer** : `update.sh` écrit les fichiers en écart dans son log (une fois, puis de nouveau seulement s'ils changent), sort avec un code d'échec qui part chez healthchecks.io (alerte), et `update.sh --diff` montre les différences. Le porteur du projet relit et copie.

## Décision

Comparer avec **les fichiers embarqués dans l'image en service** et **signaler sans remplacer**.

La frontière entre « code de l'image » (conteneur durci) et « code de l'hôte » (scripts relus par un humain) reste intacte ; l'écart ne passe plus inaperçu ; la comparaison suit la version épinglée, pas `main`.

## Conséquences

- Chaque mise à jour qui touche `deploy/` demande un geste : `./update.sh --diff`, relire, copier. Le runbook le rappelle dans « Déployer une correction ».
- Tant qu'un écart existe, le check « Mise à jour » de healthchecks.io reste en échec : une alerte à l'apparition de l'écart, une à sa disparition, rien entre les deux.
- Un fichier ajouté à `deploy/` doit entrer dans la liste blanche de `.dockerignore` (un test le vérifie) ; aucun autre fichier, et donc aucun secret, n'entre dans l'image.
- `restore.sh --production` prend le verrou d'`update.sh` : le cron ne relance pas l'appli au milieu d'une restauration.
- Une image antérieure à S6 n'a pas `/app/deploy` : `update.sh` le signale comme une erreur, jusqu'au premier déploiement d'une version S6.
