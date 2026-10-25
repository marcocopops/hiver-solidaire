# Déploiement sur le homelab

L'application tourne dans Docker Compose, derrière le conteneur `cloudflared` existant (Cloudflare Tunnel, aucun port entrant). La mise à jour se fait **en pull** : le serveur tire l'image publiée par la CI sur GHCR. Aucune clé du homelab n'est stockée dans GitHub.

```text
Internet → Cloudflare → tunnel sortant → cloudflared ──(réseau edge)──► app:8080 ──(réseau interne)──► db
```

## Prérequis

- Docker Engine et le plugin Docker Compose v2.
- Le conteneur `cloudflared` du tunnel existant, rattaché à un réseau Docker nommé `edge`.
- Le domaine `marcantoine-meunier.com` géré par Cloudflare.

## 1. Réseau `edge`

Le réseau est partagé avec `cloudflared` et créé une seule fois. S'il existe déjà, ne rien faire.

```bash
docker network inspect edge >/dev/null 2>&1 || docker network create edge
docker network connect edge <nom-du-conteneur-cloudflared>   # seulement s'il n'y est pas déjà
```

Le réseau `edge` est partagé avec d'autres applications. L'appli y est donc joignable sous l'alias **`hiver-solidaire-app`** (et non `app`, trop générique) : c'est ce nom qu'il faut utiliser dans la route du tunnel.

## 2. Fichier `.env` (sur le serveur uniquement)

```bash
sudo mkdir -p /opt/hiver-solidaire && cd /opt/hiver-solidaire
# Copier deploy/compose.prod.yaml, deploy/update.sh et .env.example à cet endroit
cp .env.example .env && chmod 600 .env
```

Renseigner dans `.env` : `POSTGRES_DB`, `POSTGRES_USER` et un `POSTGRES_PASSWORD` long et aléatoire (par exemple `openssl rand -base64 32`). Ce fichier ne quitte jamais le serveur et n'est jamais versionné. Les variables `DB_*` du modèle ne servent qu'au développement local : le compose de production les déduit des `POSTGRES_*`.

## 3. Accès à l'image GHCR

Image : `ghcr.io/marcocopops/hiver-solidaire`, tags `main` (dernière version) et `sha-<commit>`.

- **Package public** (recommandé, le dépôt est public) : GitHub → Packages → `hiver-solidaire` → *Package settings* → *Change visibility* → Public. Aucune authentification nécessaire pour tirer l'image.
- **Package privé** : créer un jeton (classic) avec le seul droit `read:packages`, puis `echo "$TOKEN" | docker login ghcr.io -u <utilisateur> --password-stdin`.

Le premier push de la CI crée le package : il est privé par défaut tant qu'on ne l'a pas rendu public.

## 4. Premier démarrage

```bash
cd /opt/hiver-solidaire
docker compose -f compose.prod.yaml pull
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps      # app et db doivent être « healthy »
```

Flyway applique les migrations au démarrage de l'appli. Aucun port n'est publié sur l'hôte : tester depuis le réseau `edge` :

```bash
docker run --rm --network edge --entrypoint wget ghcr.io/marcocopops/hiver-solidaire:main \
  -q -O /dev/null -S http://hiver-solidaire-app:8080/
```

## 5. Cloudflare

1. **Route du tunnel** (Zero Trust → Networks → Tunnels → le tunnel → *Public hostname*) : `hiver-solidaire.marcantoine-meunier.com` → service `HTTP` `hiver-solidaire-app:8080`. Cloudflare crée l'enregistrement DNS associé. Ne jamais router le port 8081 (management).
2. **Redirection** (Rules → Redirect Rules) : si l'URL correspond à `marcantoine-meunier.com/hiver-solidaire*`, rediriger en 301 vers `https://hiver-solidaire.marcantoine-meunier.com/` (voir ADR-0001).
3. **HSTS** (SSL/TLS → Edge Certificates → HTTP Strict Transport Security) : activer pour le domaine. L'application ne pose pas cet en-tête.
4. Vérifier que le portfolio ne pose jamais de cookie avec `Domain=marcantoine-meunier.com` (ADR-0001).

## 6. Mise à jour automatique (pull)

`deploy/update.sh` tire la nouvelle image puis relance les conteneurs modifiés. Le lancer par cron, par exemple toutes les 10 minutes :

```bash
chmod +x /opt/hiver-solidaire/update.sh
crontab -e
# */10 * * * * /opt/hiver-solidaire/update.sh >> /var/log/hiver-solidaire-update.log 2>&1
```

Pendant la saison, une mise à jour automatique sur `main` est un choix à reconsidérer (voir plus bas : épingler un tag `sha-…`).

## 7. Retour arrière

Chaque commit de `main` publie un tag immuable `sha-<commit>`. Pour revenir à une version précédente, ajouter dans `.env` :

```bash
APP_TAG=sha-<commit-précédent>
```

puis `docker compose -f compose.prod.yaml up -d`. Tant que `APP_TAG` n'est pas `main`, `update.sh` ne change pas de version : retirer la ligne pour reprendre les mises à jour automatiques. Les migrations Flyway ne se défont pas : un retour arrière de l'image ne revient pas sur le schéma (on corrige en avant, par une nouvelle migration).

## Connexion des bénévoles (depuis S3)

- **Origine des liens** : `APP_BASE_URL` (par défaut `https://hiver-solidaire.marcantoine-meunier.com` dans `compose.prod.yaml`). Le lien envoyé par e-mail est construit sur cette valeur, jamais sur l'en-tête `Host` de la requête.
- **E-mails** : aucun `MAIL_HOST` avant S5 (Brevo). L'appli démarre et la page de connexion affiche « momentanément indisponible » ; le planning public reste servi. Ne jamais pointer la production vers Mailpit.
- **Sessions** : stockées en base (table `spring_session`, ADR-0007), elles survivent aux mises à jour. Pour déconnecter un bénévole de tous ses appareils : `DELETE FROM spring_session WHERE principal_name = '<identifiant du bénévole>';`.
- **Limitation des demandes de lien** : 3 par adresse toutes les 15 minutes, 10 par IP par heure, **en mémoire**. Valable pour une instance unique : les compteurs repartent de zéro à chaque redémarrage et ne seraient pas partagés entre deux instances. L'IP vient de l'en-tête transmis par cloudflared (`forward-headers-strategy: native`) : l'appli ne doit être joignable que par cloudflared. La limitation de débit de Cloudflare reste la première barrière.
- **`APP_COORDINATORS_PHONE`** (depuis S4a) : téléphone des coordinateurs, affiché dans « Mes inscriptions » après la limite de désinscription (la veille à 23 h 59). À renseigner dans le `.env` du serveur ; vide, l'appli affiche « contactez un coordinateur ». Une valeur qui n'est pas un numéro empêche le démarrage.
- **Comptes supprimés** : la suppression par le bénévole (« Mon compte ») anonymise sa ligne, annule ses inscriptions à venir et supprime ses sessions et ses liens en cours (ADR-0008). Aucune action manuelle à prévoir.
- **`APP_CLOCK_OFFSET`** : réservé au développement, ne jamais le définir en production (un avertissement apparaît au démarrage s'il l'est).

## Contrôles utiles

```bash
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml logs --tail=100 app
docker inspect --format '{{.State.Health.Status}}' hiver-solidaire-app-1
```

La sonde externe interroge `https://hiver-solidaire.marcantoine-meunier.com/`. Le healthcheck Docker utilise le port de management 8081, interne au conteneur.
