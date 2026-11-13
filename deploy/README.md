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
# Copier à cet endroit : .env.example et, depuis deploy/, compose.prod.yaml, lib.sh, update.sh, backup.sh,
# restore.sh, restore-check.sql, backup.env.example (liste à jour : « Fichiers du serveur » plus bas)
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
5. **Cloudflare Access sur le back-office** (depuis S4b, cahier des charges §8) : Zero Trust → Access → Applications → *Add an application* → *Self-hosted*.
   - Domaine : `hiver-solidaire.marcantoine-meunier.com`, chemin `admin` (couvre `/admin` et `/admin/*`).
   - Politique *Allow* : *Include* → *Emails* → les adresses des coordinateurs (les mêmes que `APP_COORDINATOR_EMAILS`), méthode de connexion « One-time PIN » (code par e-mail) ou le fournisseur d'identité choisi.
   - Durée de session Access : 8 h au plus, comme la session coordinateur de l'appli.
   - Ne pas ajouter de règle *Bypass* ni de jeton de service.

   Access est une première barrière, pas la seule : l'appli ne lui fait pas confiance et vérifie elle-même le rôle coordinateur sur chaque route `/admin/**` (défense en profondeur, modèle de menaces M7). Un coordinateur passe donc deux contrôles : Access (code reçu par e-mail) puis la connexion par lien magique de l'appli. Vérifier après la mise en place : `https://hiver-solidaire.marcantoine-meunier.com/admin` affiche la page d'Access dans une fenêtre privée, et le planning public reste accessible sans Access.

## 6. Mise à jour automatique (pull)

`update.sh` tire l'image d'`APP_TAG`, relance les conteneurs modifiés, puis compare les fichiers du serveur à ceux de la version en service ([ADR-0015](../docs/adr/0015-signaler-la-divergence-des-fichiers-de-deploiement.md)). Il ne remplace jamais un fichier : il signale l'écart. Il n'écrit dans son log que lorsque quelque chose change (version déployée, écart apparu ou disparu, erreur).

```bash
cd /opt/hiver-solidaire
chmod 700 update.sh
mkdir -p /var/log/hiver-solidaire
cp logrotate.conf /etc/logrotate.d/hiver-solidaire     # rotation des logs des scripts (5 Mo, 8 semaines)
crontab -e     # crontab de root
# */10 * * * * /opt/hiver-solidaire/update.sh >> /var/log/hiver-solidaire/update.log 2>&1
```

Alerte : sur healthchecks.io (voir « Sauvegardes »), un second check « Mise à jour », *Period* **10 minutes**, *Grace time* **30 minutes**, et son adresse dans `.env` : `UPDATE_PING_URL=https://hc-ping.com/<uuid>`. Le check passe en échec si la mise à jour échoue, si des fichiers divergent (code 3), ou si le cron ne tourne plus.

Écart signalé (`ÉCART : fichiers du serveur différents de la version en service`) :

```bash
./update.sh --diff      # diff -u entre les fichiers du serveur et ceux de la version en service
```

Relire le diff (une nouvelle variable à ajouter dans `.env` ? un script modifié ?), puis copier les fichiers depuis le dépôt, à la même version que l'image (`git checkout <commit>` sur le poste, ou le diff lui-même). `update.sh` écrit `Plus d'écart` au passage suivant. Ne jamais copier un fichier sans l'avoir relu : c'est ce qui sépare le code de l'image (conteneur durci) du code qui tourne en root sur l'hôte.

Les logs Docker de chaque service sont limités à 3 fichiers de 10 Mo (`logging` dans `compose.prod.yaml`).

## 7. Épingler la version, déployer une correction, revenir en arrière

Chaque commit de `main` publie un tag `sha-<commit>` ; l'image porte son commit (`org.opencontainers.image.revision`). **À l'ouverture des inscriptions (14 décembre), la production cesse de suivre `main`** : un déploiement devient un geste volontaire.

### Épingler la version en service

```bash
cd /opt/hiver-solidaire
image=$(docker inspect --format '{{.Image}}' "$(docker compose -f compose.prod.yaml ps -q app)")
docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image"   # commit
docker image inspect --format '{{index .RepoDigests 0}}' "$image"                                       # empreinte
```

Dans `.env` :

```bash
APP_TAG=sha-<commit>@sha256:<empreinte>
```

L'empreinte protège aussi contre une image remplacée sur GHCR après validation (modèle de menaces, M17) : Docker refuse une image dont l'empreinte diffère. Puis `docker compose -f compose.prod.yaml up -d` (rien ne redémarre si c'est déjà la version en service). `update.sh` continue de tourner : il ne change plus de version, et surveille toujours les fichiers.

### Déployer une correction

1. PR fusionnée dans `main`, CI verte : le tag `sha-<nouveau commit>` existe sur GHCR.
2. Image et empreinte :

   ```bash
   new=ghcr.io/marcocopops/hiver-solidaire:sha-<nouveau commit>
   docker pull "$new" && docker image inspect --format '{{index .RepoDigests 0}}' "$new"
   ```

3. Fichiers de déploiement de la nouvelle version, avant de basculer (copiés sans lancer l'image) :

   ```bash
   id=$(docker create "$new") && docker cp "$id:/app/deploy" /tmp/deploy-nouvelle-version && docker rm "$id"
   for f in /tmp/deploy-nouvelle-version/*; do diff -u "$(basename "$f")" "$f"; done
   ```

   Relire, copier ce qui change, compléter `.env` si besoin, puis `rm -r /tmp/deploy-nouvelle-version`.
4. Si la version contient une migration Flyway : `./backup.sh` juste avant.
5. Noter l'`APP_TAG` actuel (retour arrière), puis le remplacer dans `.env` par `sha-<nouveau commit>@sha256:<empreinte>`, et `docker compose -f compose.prod.yaml up -d`.
6. Vérifier : `docker compose -f compose.prod.yaml ps` (« healthy »), `/sante` à 200, le planning, une connexion. Consigner la version dans le runbook (« Versions déployées »).

### Revenir en arrière

Remettre l'`APP_TAG` précédent dans `.env`, puis `docker compose -f compose.prod.yaml up -d`. Les migrations Flyway ne se défont pas :

- une ancienne version démarre sur un schéma plus récent tant que la migration n'a fait qu'ajouter (Flyway ignore les migrations « futures ») ;
- si la migration a supprimé ou renommé une colonne, l'ancienne version refuse de démarrer (`ddl-auto: validate`) : soit corriger en avant (nouvelle version), soit restaurer la sauvegarde faite avant le déploiement (`./restore.sh --production <identifiant>`), en perdant ce qui a été saisi depuis.

## Fichiers du serveur

Dans `/opt/hiver-solidaire`, tous copiés depuis `deploy/` à la version en service (sauf les secrets) :

| Fichier | Rôle |
|---|---|
| `compose.prod.yaml` | services `app`, `db` et `restic` (profil `backup`) |
| `lib.sh` | fonctions communes aux scripts |
| `update.sh` | mise à jour en pull, signalement des écarts (cron toutes les 10 minutes) |
| `backup.sh`, `restore.sh`, `restore-check.sql` | sauvegarde (cron à 5 h 15), restauration |
| `logrotate.conf` | copié aussi dans `/etc/logrotate.d/hiver-solidaire` |
| `backup.env.example` | modèle de `backup.env` |
| `.env` | secrets de l'appli et adresses de ping (chmod 600, jamais versionné) |
| `backup.env` | secrets de restic et de R2 (chmod 600, jamais versionné) |

`update.sh` compare les fichiers de la liste blanche de `.dockerignore` (ceux de l'image), pas `.env` ni `backup.env`.

## Connexion des bénévoles (depuis S3)

- **Origine des liens** : `APP_BASE_URL` (par défaut `https://hiver-solidaire.marcantoine-meunier.com` dans `compose.prod.yaml`). Le lien envoyé par e-mail est construit sur cette valeur, jamais sur l'en-tête `Host` de la requête.
- **E-mails** : voir « E-mails (Brevo, depuis S5a) » ci-dessous. Sans `MAIL_HOST`, l'appli démarre et la page de connexion affiche « momentanément indisponible » ; le planning public reste servi. Ne jamais pointer la production vers Mailpit.
- **Sessions** : stockées en base (table `spring_session`, ADR-0007), elles survivent aux mises à jour. Pour déconnecter un bénévole de tous ses appareils : `DELETE FROM spring_session WHERE principal_name = '<identifiant du bénévole>';`.
- **Limitation des demandes de lien** : 3 par adresse toutes les 15 minutes, 10 par IP par heure, **en mémoire**. Valable pour une instance unique : les compteurs repartent de zéro à chaque redémarrage et ne seraient pas partagés entre deux instances. L'IP vient de l'en-tête transmis par cloudflared (`forward-headers-strategy: native`) : l'appli ne doit être joignable que par cloudflared. La limitation de débit de Cloudflare reste la première barrière.
- **Contact des coordinateurs** (depuis le lot 2) : un numéro de téléphone français ou une adresse e-mail, saisi dans le back-office (« Saison ») et affiché aux bénévoles après la limite de désinscription (la veille à 23 h 59). Aucune variable d'environnement : `APP_COORDINATORS_PHONE` n'est plus lue, la retirer du `.env` du serveur. Sans contact, les bénévoles lisent « Contactez un coordinateur. », sans lien, et le tableau de bord le signale. La migration V13 conserve le téléphone déjà saisi dans la saison. La saison d'exemple ne se modifie pas : son contact se renseigne en créant la vraie saison.
- **`APP_COORDINATOR_EMAILS`** (depuis S4b, [ADR-0010](../docs/adr/0010-coordinateurs-par-variable-d-environnement.md)) : adresses des coordinateurs, séparées par des virgules, dans le `.env` du serveur. À la connexion par lien magique, une adresse de la liste reçoit le rôle coordinateur (back-office `/admin`), en plus du rôle bénévole. Une entrée qui n'est pas une adresse empêche le démarrage. Pour retirer un coordinateur : enlever son adresse, puis `docker compose -f compose.prod.yaml up -d` ; il perd le rôle à sa prochaine connexion, et au plus tard 8 h après la précédente (durée d'une session coordinateur). Pour l'en priver tout de suite, supprimer aussi ses sessions : `DELETE FROM spring_session WHERE principal_name = '<identifiant>';`. Mettre à jour la politique Cloudflare Access en même temps.
- **Journal d'audit** (depuis S4b) : table `audit_log`, en ajout seul (un déclencheur refuse `UPDATE` et `DELETE`, sauf la suppression des lignes de plus de 6 mois par la purge, depuis S5b). Elle ne contient que des identifiants techniques et des dates : pour savoir qui a fait quoi, rapprocher `coordinator_id` de `volunteer.id`.
- **Comptes supprimés** : la suppression par le bénévole (« Mon compte ») anonymise sa ligne, annule ses inscriptions à venir et supprime ses sessions et ses liens en cours (ADR-0008). Aucune action manuelle à prévoir.
- **Turnstile** (depuis S5b) : voir « Turnstile » ci-dessous. Sans clés, la page de connexion affiche « momentanément indisponible ».
- **`APP_CLOCK_OFFSET`** : réservé au développement, ne jamais le définir en production (un avertissement apparaît au démarrage s'il l'est).

## E-mails (Brevo, depuis S5a)

L'appli envoie par SMTP le lien de connexion et les e-mails automatiques ([cahier des charges](../docs/cahier-des-charges.md) §6, [ADR-0012](../docs/adr/0012-table-d-envois-plutot-qu-envoi-dans-la-requete.md)) : confirmations, rappel de 18 h, alertes immédiates et récap de 20 h aux adresses d'`APP_COORDINATOR_EMAILS`.

1. **Chez Brevo** : domaine d'envoi authentifié (SPF, DKIM, DMARC) sans toucher aux MX existants. Créer une clé SMTP dédiée à l'appli.
2. **Suivi désactivé** : vérifier dans les réglages des e-mails transactionnels de Brevo que le suivi des ouvertures et des clics est **désactivé**. Sinon, Brevo réécrit les liens et ajoute un pixel, ce que le cahier des charges exclut (§6, « Aucun suivi »). Contrôle : dans un e-mail reçu, les liens commencent par `https://hiver-solidaire.marcantoine-meunier.com/`, et la source du message ne contient aucune image.
3. **Dans le `.env` du serveur** (valeurs réelles, jamais dans le dépôt) :

   ```bash
   MAIL_HOST=smtp-relay.brevo.com
   MAIL_PORT=587
   MAIL_USERNAME=<identifiant SMTP Brevo>
   MAIL_PASSWORD=<clé SMTP Brevo>
   MAIL_SMTP_AUTH=true
   MAIL_STARTTLS=true
   MAIL_FROM=<adresse du domaine authentifié>
   ```

   Avec `MAIL_HOST`, `MAIL_FROM` est obligatoire : sans lui, l'appli refuse de démarrer. Le `.env.example` contient des valeurs de développement (Mailpit, `MAIL_HOST=localhost`) : ne pas les garder en production.
4. `docker compose -f compose.prod.yaml up -d`, puis vérifier : un lien de connexion arrive, et les logs n'indiquent que des identifiants (`Mail delivery <uuid> (CONFIRMATION) sent`), jamais une adresse.

À savoir :

- **Une seule instance** de l'appli : la tâche des e-mails tourne toutes les 30 secondes dans l'appli, sans verrou distribué (ADR-0012). Ne pas lancer deux conteneurs `app` sur la même base.
- **Heures** : rappel la veille à 18 h, récap à 20 h, heure de Paris. Si l'appli est arrêtée à ce moment-là, le rappel est rattrapé jusqu'à 22 h et le récap jusqu'au lendemain 8 h ; ensuite, ils sont perdus (jamais d'e-mail la nuit ni sur une soirée passée).
- **Échecs** (Brevo indisponible, quota de 300 par jour) : 6 essais sur environ 2 heures, puis abandon. État des envois : `SELECT kind, count(*) FILTER (WHERE sent_at IS NOT NULL) AS envoyes, count(*) FILTER (WHERE abandon_reason = 'FAILED') AS echecs FROM mail_delivery WHERE created_at > now() - interval '1 day' GROUP BY kind;`. La table ne contient que des identifiants et des dates.
- **`APP_MAIL_DEV_RUN_NOW`**, **`APP_MAIL_CONFIRMATION_DELAY`** et **`APP_MAIL_ALLOW_RESERVED_DOMAINS`** : réservés au développement, ne jamais les définir en production (`compose.prod.yaml` ne les transmet pas). En production, aucun e-mail ne part vers un domaine réservé aux tests (`example.org`, `.test`, `.invalid`…) : il est abandonné (`abandon_reason = 'RESERVED_DOMAIN'`).

## Turnstile (depuis S5b)

La demande de lien de connexion est protégée par Cloudflare Turnstile ([ADR-0013](../docs/adr/0013-turnstile-exception-a-la-regle-sans-cdn.md)).

1. **Chez Cloudflare** : tableau de bord → Turnstile → « Add widget ». Nom : « Hiver Solidaire » ; nom d'hôte : `hiver-solidaire.marcantoine-meunier.com` (seulement lui) ; mode : **Managed** ; pas de pré-autorisation (« pre-clearance ») nécessaire. Noter la clé de site (publique) et la clé secrète.
2. **Dans le `.env` du serveur** (valeurs réelles, jamais dans le dépôt) :

   ```bash
   TURNSTILE_SITE_KEY=<clé de site du widget>
   TURNSTILE_SECRET_KEY=<clé secrète du widget>
   ```

   Les clés de test de `.env.example` (`1x000…`) ne sont acceptées qu'avec une `APP_BASE_URL` locale : en production, elles rendent la connexion indisponible (avertissement au démarrage : `Cloudflare test keys are only allowed on localhost`).
3. `docker compose -f compose.prod.yaml up -d`, puis vérifier : la page `/connexion` affiche la case de vérification, sans erreur de CSP dans la console du navigateur, et un lien de connexion arrive.

À savoir :

- L'appli appelle `https://challenges.cloudflare.com/turnstile/v0/siteverify` à chaque demande de lien (sortie HTTPS depuis le conteneur, déjà nécessaire pour Brevo). Si Cloudflare ne répond pas en 5 secondes, la demande est refusée, avec un message qui invite à réessayer.
- Logs : `Turnstile check failed [invalid-input-response]` pour un refus (codes de Cloudflare seulement), `Turnstile check unavailable (…)` si Cloudflare ne répond pas. Jamais de jeton, d'IP ni d'adresse.
- Changer de clés : modifier le `.env`, puis `docker compose -f compose.prod.yaml up -d`.

## Purge des données (depuis S5b)

Chaque jour à 4 h 30 (heure de Paris), et au démarrage, l'appli applique les durées de conservation ([cahier des charges](../docs/cahier-des-charges.md) §7, [ADR-0014](../docs/adr/0014-purge-par-anonymisation-et-journal-d-audit-borne.md)) : comptes sans profil supprimés après 7 jours, bénévoles sans accord anonymisés un mois après la fin de la saison, avec accord après 3 ans sans participation, envois d'e-mails, alertes et journal d'audit de plus de 6 mois supprimés. Aucune action manuelle.

Contrôle : `docker compose -f compose.prod.yaml logs app | grep "Data retention run"` donne, pour chaque exécution, des comptes seulement. Une seule instance de l'appli : la purge n'a pas de verrou distribué.

## Sauvegardes (depuis S6)

Chaque nuit, `backup.sh` sauvegarde la base, chiffrée par restic, dans un bucket Cloudflare R2 ([ADR-0016](../docs/adr/0016-sauvegardes-restic-vers-r2-dans-un-conteneur.md)) : 7 sauvegardes quotidiennes et 4 hebdomadaires, soit 30 jours au plus (durée annoncée par la notice), et une vérification du dépôt le dimanche. Le dump en clair n'existe qu'en mémoire (`/dev/shm`), le temps de l'envoi. restic tourne dans un conteneur (service `restic`, profil `backup`, de `compose.prod.yaml`) qui ne voit ni la base ni l'appli. Un échec, ou une nuit sans sauvegarde, déclenche une alerte de healthchecks.io.

### 1. Bucket et clé R2 (chez Cloudflare)

1. R2 → *Create bucket* : nom `hiver-solidaire-sauvegardes`, *Location* → **Specify jurisdiction** → **European Union (EU)**. Aucun accès public, aucun domaine personnalisé.
2. R2 → *Manage R2 API Tokens* → *Create API token* : permission **Object Read & Write**, *Specify bucket(s)* → le seul `hiver-solidaire-sauvegardes`, sans date d'expiration (ou une date notée dans le runbook). Noter l'*Access Key ID* et la *Secret Access Key* (affichée une seule fois). Repérer l'*Account ID* du compte.

### 2. Secrets sur le serveur

```bash
cd /opt/hiver-solidaire
cp backup.env.example backup.env && chmod 600 backup.env
openssl rand -base64 32      # mot de passe restic
```

Renseigner dans `backup.env` : `RESTIC_REPOSITORY` (avec l'Account ID et l'adresse `.eu.`), `RESTIC_PASSWORD`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`. Ce fichier n'est lu que par le conteneur restic et ne quitte jamais le serveur.

**Le mot de passe restic doit aussi être gardé hors du serveur, dans un gestionnaire de mots de passe (avec l'Account ID, le nom du bucket et la clé R2). Sans lui, les sauvegardes sont illisibles : si le serveur est perdu, elles le sont aussi.**

### 3. Alerte (healthchecks.io)

1. Créer un compte sur [healthchecks.io](https://healthchecks.io) (offre gratuite), avec l'adresse du porteur du projet, et un projet « Hiver Solidaire ».
2. *Add Check* : nom « Sauvegarde », *Schedule* **Cron** `15 5 * * *`, fuseau `Europe/Paris`, *Grace time* **1 heure**. Copier son adresse de ping.
3. Dans `.env` : `BACKUP_PING_URL=https://hc-ping.com/<uuid>`. Cette adresse ne doit pas être publiée (n'importe qui pourrait signaler un faux succès).
4. *Integrations* : l'e-mail est actif par défaut ; ajouter si possible une notification sur téléphone (application mobile ou autre intégration gratuite).

Le script n'envoie que `start`, puis son code de sortie (0 : succès) : aucun log, aucune donnée.

### 4. Initialisation et première sauvegarde

```bash
cd /opt/hiver-solidaire
chmod 700 backup.sh restore.sh
./backup.sh --init        # crée le dépôt chiffré dans le bucket (une seule fois)
./backup.sh               # première sauvegarde ; healthchecks.io passe au vert
./restore.sh              # test de restauration (ci-dessous)
```

### 5. Cron

```bash
mkdir -p /var/log/hiver-solidaire
crontab -e     # crontab de root (les scripts utilisent Docker)
# 15 5 * * * /opt/hiver-solidaire/backup.sh >> /var/log/hiver-solidaire/backup.log 2>&1
```

5 h 15, heure du serveur (supposé à l'heure de Paris : `timedatectl`), après la purge de 4 h 30 : les sauvegardes ne contiennent déjà plus les données purgées. Rotation du log : `logrotate` (« Mise à jour automatique »).

### 6. Restaurer

```bash
./restore.sh                     # contrôle : dernière sauvegarde dans une base jetable, vérifiée puis effacée
./restore.sh <identifiant>       # une sauvegarde précise
docker compose -f compose.prod.yaml --profile backup run --rm --no-deps restic snapshots --host hiver-solidaire   # liste
./restore.sh --production <identifiant>   # remplace la base de production (runbook, « Restaurer la base »)
```

Le contrôle affiche, pour la sauvegarde et la production, la dernière migration Flyway, les migrations en échec, et les nombres de saisons, soirées, bénévoles, inscriptions, lignes du journal d'audit et envois d'e-mails. Il échoue si l'historique Flyway manque, si une migration est en échec, s'il n'y a aucune soirée, ou si la dernière sauvegarde a plus de 26 h. Les écarts avec la production correspondent à l'activité depuis la nuit.

**Avant la bêta du 30 novembre**, faire un vrai test de restauration sur le serveur, puis noter sa date dans le [runbook](../docs/exploitation/runbook.md) (« Tests de restauration »).

## Contrôles utiles

```bash
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml logs --tail=100 app
docker inspect --format '{{.State.Health.Status}}' hiver-solidaire-app-1
```

Le healthcheck Docker utilise le port de management 8081, interne au conteneur. La sonde externe interroge `/sante` (ci-dessous).

## Sonde externe (depuis S6)

La page publique `https://hiver-solidaire.marcantoine-meunier.com/sante` répond **200** si l'appli et la base répondent, **503** sinon, sans aucun détail (corps vide, ni version ni erreur). Le résultat est gardé 10 secondes. Le port de management (8081) n'est jamais routé.

### Règle Cloudflare Access « Bypass » sur `/sante`

Tant que le site entier est derrière Access (owner-only, jusqu'à l'ouverture), une sonde sur `/` recevrait la page de connexion d'Access, avec un code 200 ou 302, et croirait le site en ligne. Seul `/sante` y échappe :

1. Zero Trust → Access → Applications → *Add an application* → *Self-hosted*. Nom : « Hiver Solidaire — santé ».
2. Domaine : `hiver-solidaire.marcantoine-meunier.com`, chemin **`sante`** (exactement, sans joker). L'application la plus précise l'emporte sur celle du site entier.
3. Politique : action **Bypass**, *Include* → *Everyone*. Aucune autre application ne doit avoir de règle Bypass, et surtout pas celle de `/admin`.
4. Vérifier dans une fenêtre privée : `/sante` répond sans page d'Access, `/` et `/admin` affichent toujours la page d'Access.

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://hiver-solidaire.marcantoine-meunier.com/sante   # 200
curl -s -o /dev/null -w '%{http_code}\n' https://hiver-solidaire.marcantoine-meunier.com/        # 302 vers Access avant l'ouverture
```

Après l'ouverture (runbook, « Ouvrir le site le jour J »), la règle peut rester : la page ne révèle rien.

### Moniteur UptimeRobot

1. Créer un compte UptimeRobot (offre gratuite) avec une adresse du porteur du projet.
2. *Add New Monitor* : type **HTTP(s)**, nom « Hiver Solidaire », URL `https://hiver-solidaire.marcantoine-meunier.com/sante`, intervalle **5 minutes**. Une réponse autre que 2xx (503, 502 de Cloudflare quand le tunnel est coupé, délai dépassé) est une panne.
3. Contacts d'alerte : l'e-mail du porteur du projet ; installer l'application mobile UptimeRobot et activer ses notifications (vérifier à la création que l'offre gratuite les inclut ; sinon, garder l'e-mail et une notification de la messagerie du téléphone sur l'expéditeur d'UptimeRobot).
4. Ne pas créer de page de statut publique.
5. Tester : `docker compose -f compose.prod.yaml stop app`, attendre l'alerte (5 à 10 minutes), puis `docker compose -f compose.prod.yaml start app` et attendre l'alerte de retour.
