# Hiver Solidaire — planning des bénévoles

Application web de planning pour l'accueil hivernal de personnes sans abri. Une vingtaine de bénévoles s'y inscrivent en quelques secondes sur les dîners, les nuits et les petits-déjeuners de la saison.

> *EN — Volunteer scheduling app for a small association's winter homeless shelter: Spring Boot with server-side rendering, magic-link login, self-hosted on a homelab behind Cloudflare Tunnel, built end to end with Claude Code.*

**Statut** : en construction. Ouverture des inscriptions prévue le 14 décembre 2026.

## Ce que fait l'application

- Planning public des soirées : postes qui manquent et pseudos des inscrits, sans autre donnée personnelle
- Inscription et désinscription en autonomie, connexion par lien magique (pas de mot de passe)
- Back-office des coordinateurs : soirées à compléter, inscriptions par téléphone, exports
- E-mails de confirmation, rappel la veille, alertes et récap quotidien aux coordinateurs

## Choix techniques

| Couche | Choix |
|---|---|
| Application | Java 21, Spring Boot 4.1, Spring Security 7, Thymeleaf + htmx |
| Données | PostgreSQL, Flyway |
| Hébergement | Docker Compose sur un homelab, exposé par Cloudflare Tunnel (aucun port entrant) |
| CI/CD | GitHub Actions, image publiée sur GHCR, déploiement en pull |

Sécurité et données personnelles : §7 et §8 du [cahier des charges](docs/cahier-des-charges.md).

## Documentation

- [Cahier des charges](docs/cahier-des-charges.md) : la référence fonctionnelle et technique
- [Décisions d'architecture (ADR)](docs/adr/)
- [Prompts des sessions Claude Code](docs/prompts/) : comment le projet est construit avec Claude
- [CLAUDE.md](CLAUDE.md) : les règles que suit Claude Code dans ce dépôt
- [Signaler une vulnérabilité](SECURITY.md)

## Démarrer en local

Prérequis : Java 21 et Docker (pour PostgreSQL, Mailpit et les tests).

```bash
cp .env.example .env             # valeurs factices, à adapter ; .env n'est jamais versionné
docker compose up -d             # PostgreSQL sur 127.0.0.1:5433, Mailpit sur 127.0.0.1:1025 (SMTP) et :8025 (web)
set -a; . ./.env; set +a         # charge DB_*, MAIL_*, APP_BASE_URL dans le shell
./mvnw spring-boot:run           # http://localhost:8080
```

- Ports déjà pris sur la machine : changer `DEV_DB_PORT` (et `DB_URL`), `DEV_SMTP_PORT` (et `MAIL_PORT`), `DEV_MAILPIT_UI_PORT`, ou décommenter `SERVER_PORT` dans `.env`.
- Le compose de développement a son propre nom de projet (`hiver-solidaire-dev`) : il ne touche jamais aux conteneurs ni au volume de production, même lancé sur le serveur.

**Se connecter et s'inscrire en local** :

1. Sur http://localhost:8080/planning, choisir une soirée puis un type, et saisir une adresse factice (`prenom@example.org`).
2. Ouvrir la boîte de réception de développement, http://127.0.0.1:8025 : le lien de connexion y arrive (rien n'est envoyé sur Internet).
3. Ouvrir le lien, appuyer sur « Me connecter », remplir le premier profil, confirmer : l'inscription apparaît dans « Mes inscriptions » et le statut change sur le planning.

- Les inscriptions de la saison d'exemple n'ouvrent que le 14 décembre 2026. Pour essayer avant, décaler l'horloge de l'appli : `APP_CLOCK_OFFSET=P72D ./mvnw spring-boot:run` (développement seulement, un avertissement le rappelle au démarrage).
- Le cookie de session est `Secure` (préfixe `__Host-`) : utiliser `http://localhost`, que les navigateurs traitent comme sûr, et pas `http://127.0.0.1` ni l'adresse IP de la machine.
- Sans `MAIL_HOST`, l'appli démarre mais la page de connexion affiche « momentanément indisponible ».

## Données de test

Pour tester à la main le planning, le back-office et les exports sans s'inscrire vingt fois, un script remplit la base de **développement** avec des données factices :

```bash
scripts/seed-dev.sh              # ajoute les données ; relancé, il ne double rien
scripts/seed-dev.sh --reset      # vide d'abord inscriptions, alertes, journal d'audit et bénévoles factices
```

- **Prérequis** : `docker compose up -d` et une base migrée (démarrer l'appli une fois : Flyway crée le schéma).
- **Contenu** : 21 bénévoles factices (`prenom.nom@example.org`, numéros de la plage réservée à la fiction, pseudos « CamilleM »), dont un inscrit par téléphone sans e-mail et 4 nouveaux non « vus » ; environ 60 % des soirées des 4 premières semaines de la saison remplies : journées complètes, journées incomplètes à divers degrés, un plat préparé chez soi avec sa récupération et un sans (R4), des inscriptions annulées et une alerte « soirée devenue incomplète ». Les dates suivent la première soirée de la saison.
- **Règles** : les inscriptions respectent R1 à R4 et R7 par construction, et une requête de contrôle le vérifie à la fin ; une violation annule tout (une seule transaction). Une soirée qui porte déjà une inscription faite à la main est laissée telle quelle.
- **Garde-fous** : le script refuse de tourner si le conteneur `db` n'appartient pas au projet Compose `hiver-solidaire-dev`, ou si la base n'a pas le schéma attendu. Il ne lit jamais `.env` : `psql` tourne dans le conteneur avec ses propres variables. Rien de tout cela n'existe dans l'appli.
- **Voir les soirées comme à venir** : le script affiche le décalage d'horloge à utiliser, par exemple `APP_CLOCK_OFFSET=P53D ./mvnw spring-boot:run` (horloge 3 jours avant la première soirée, inscriptions ouvertes).
- **Se connecter** : comme coordinateur avec une adresse de `APP_COORDINATOR_EMAILS` (back-office sur http://localhost:8080/admin), ou comme un bénévole factice (`camille.martin@example.org`…) pour voir « Vous » sur le planning ; le lien arrive dans Mailpit.
- Le journal d'audit est en ajout seul, mais son déclencheur ne bloque pas `TRUNCATE`, que `--reset` utilise. Si un jour il le bloque, repartir d'une base vide : `docker compose down -v`, `docker compose up -d`, démarrer l'appli une fois, puis `scripts/seed-dev.sh`.

## Tests et exploitation

- Tests (JUnit + Testcontainers, sur un vrai PostgreSQL) : `./mvnw verify`.
- Santé : `http://localhost:8081/actuator/health` (port de management, jamais exposé en production).
- Image Docker : `docker build -t hiver-solidaire .`
- Déploiement sur le homelab : [deploy/README.md](deploy/README.md).
