# S1 — Squelette déployable

- **Semaine** : S1, du 19 au 25 octobre 2026
- **Où** : session cloud Claude Code (claude.ai/code), en mode plan
- **Modèle conseillé** : Sonnet
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s1-squelette.md, propose un plan, puis attends ma validation. »

## Objectif

Poser un squelette de bout en bout : une page d'accueil servie par Spring Boot, testée sur un vrai PostgreSQL, construite en image Docker et publiée sur GHCR par la CI, prête à être déployée sur le homelab. Aucune fonctionnalité métier cette semaine.

Cette session n'a pas accès au homelab : elle prépare les fichiers et la procédure, et le déploiement se fera ensuite en local.

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md`, §8 (exigences non fonctionnelles) et §9 (architecture)
- `docs/adr/0001-sous-domaine-plutot-que-sous-chemin.md`

## Livrables

1. **Projet Maven**
   - Java 21, Spring Boot 4.1 (dernière 4.1.x publiée sur Maven Central), wrapper `./mvnw`.
   - Starters : web MVC, Thymeleaf, Security, Data JPA, Validation, Actuator, Flyway avec le module PostgreSQL, driver PostgreSQL. En test : Spring Boot Test, Spring Security Test, Testcontainers PostgreSQL.
   - Package racine `com.marcantoinemeunier.hiversolidaire`.
2. **Page d'accueil** `/` en français : nom du projet et « Inscriptions à partir du 14 décembre 2026 ». Mobile d'abord, CSS dans un fichier statique, textes dans `messages.properties`, `lang="fr"`.
3. **Sécurité de base** (`SecurityFilterChain`)
   - Seules `/`, les ressources statiques et la page d'erreur sont publiques ; tout le reste est refusé par défaut.
   - En-têtes : CSP stricte (`default-src 'self'`, pas d'`unsafe-inline`, `frame-ancestors 'none'`, `form-action 'self'`, `base-uri 'self'`, `object-src 'none'`), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` restrictive.
   - CSRF actif. Ni formulaire de connexion par défaut, ni authentification HTTP Basic.
   - HSTS est posé par Cloudflare. L'appli prend en compte les en-têtes `X-Forwarded-*` : seul cloudflared peut la joindre.
4. **Base de données** : Flyway configuré, sans table métier (S2). `compose.yaml` à la racine pour un PostgreSQL de développement, avec des identifiants factices lus depuis `.env` et un modèle `.env.example`.
5. **Supervision**
   - Actuator sur un port de management séparé (8081), avec `health` seulement. Ce port n'est jamais routé par le tunnel : il sert au healthcheck Docker.
   - La sonde externe interrogera `/`.
6. **Tests** (`./mvnw verify` vert)
   - Le contexte démarre sur un PostgreSQL Testcontainers.
   - `/` répond 200 et contient le texte attendu.
   - Les en-têtes de sécurité sont présents sur `/`.
   - Une URL non publique (`/admin`) est refusée.
7. **Image Docker** : `Dockerfile` multi-étapes, image d'exécution JRE 21, utilisateur non-root, compatible avec un système de fichiers en lecture seule (`/tmp` en tmpfs), `HEALTHCHECK` sur le port de management.
8. **Compose de production** `deploy/compose.prod.yaml`
   - `app` sur le réseau externe `edge` (partagé avec le conteneur cloudflared existant) et sur un réseau interne ; `db` uniquement sur le réseau interne (`internal: true`). Aucun `ports:` publié sur l'hôte.
   - Durcissement : `read_only`, `tmpfs` pour `/tmp`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, limites CPU et mémoire, `restart: unless-stopped`, volume nommé pour PostgreSQL.
   - Secrets lus depuis un `.env` présent seulement sur le serveur.
9. **CI GitHub Actions**
   - Sur chaque PR et chaque push sur `main` : `./mvnw -B verify`.
   - Sur les PR : `actions/dependency-review-action`, et construction de l'image sans publication.
   - Sur push sur `main` : construction de l'image, scan de vulnérabilités (échec sur CRITICAL), publication sur `ghcr.io/marcocopops/hiver-solidaire` avec les tags `sha-<commit>` et `main`.
   - `permissions` minimales par job : `contents: read` par défaut, `packages: write` seulement pour la publication. Actions épinglées par SHA de commit complet, avec la version en commentaire.
   - `.github/dependabot.yml` : Maven, GitHub Actions et Docker, chaque semaine.
10. **Procédure de déploiement** `deploy/README.md`
    - Prérequis du homelab, création du `.env`, réseau `edge`, premier démarrage.
    - Route du tunnel pour `hiver-solidaire.marcantoine-meunier.com` vers `app:8080`, règle de redirection Cloudflare depuis `/hiver-solidaire`, HSTS côté Cloudflare.
    - Mise à jour en pull : script `deploy/update.sh` lancé par cron (`docker compose pull`, puis `up -d`).
    - Accès à l'image GHCR (package public ou jeton `read:packages`) et retour arrière vers le tag précédent.
11. **Documentation** : section « Démarrer en local » du `README.md`, section « Commandes » de `CLAUDE.md` à jour.

## Hors périmètre

Modèle de données, authentification, e-mails, htmx, back-office : semaines suivantes.

## Critères de fin

- [ ] `./mvnw verify` vert dans la session et en CI
- [ ] Image construite et démarrée dans la session en lecture seule : `/` répond, healthcheck sain
- [ ] Aucun secret ni donnée réelle dans le diff
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, comment tester, points de sécurité, questions ouvertes

## Si quelque chose bloque

Si Testcontainers ou Docker ne fonctionnent pas dans la session, dis-le et montre l'erreur. Ne remplace pas PostgreSQL par H2 et ne désactive aucun test.
