# CLAUDE.md — Hiver Solidaire

Planning en ligne des bénévoles d'un accueil hivernal de personnes sans abri : une vingtaine de bénévoles majeurs, 2 à 3 coordinateurs, ouverture des inscriptions le 14 décembre 2026.

La référence est `docs/cahier-des-charges.md` : règles métier R1 à R7 (§5), parcours (§6), RGPD (§7), sécurité (§8), architecture (§9). Lis la section concernée avant toute fonctionnalité. Si le code doit s'écarter du cahier des charges, dis-le et propose la modification du document dans la même PR.

## Stack

- Java 21, Spring Boot 4.1.x, Spring Security 7, Spring Data JPA, Thymeleaf ; htmx servi depuis le dépôt (pas de CDN)
- PostgreSQL, migrations Flyway dans `src/main/resources/db/migration`
- Maven via le wrapper `./mvnw`
- Tests : JUnit 5, AssertJ, Spring Security Test, Testcontainers (PostgreSQL)
- Package racine : `com.marcantoinemeunier.hiversolidaire`

## Commandes

Mets cette section à jour si elles changent.

- `./mvnw verify` : compilation, tests unitaires et d'intégration (Testcontainers : Docker doit tourner). Doit être vert avant chaque push.
- `docker compose up -d` : PostgreSQL de développement sur 127.0.0.1:5433 et Mailpit (SMTP 127.0.0.1:1025, boîte de réception sur http://127.0.0.1:8025), projet `hiver-solidaire-dev` distinct de la production (copier `.env.example` vers `.env`)
- `./mvnw spring-boot:run` : application sur http://localhost:8080 (utiliser `localhost`, pas `127.0.0.1` : cookie `__Host-` Secure) ; santé sur http://localhost:8081/actuator/health. Le lien de connexion arrive dans Mailpit. `APP_CLOCK_OFFSET=P72D` décale l'horloge pour s'inscrire avant l'ouverture (dev seulement). `APP_COORDINATORS_PHONE` : téléphone des coordinateurs affiché après la limite de désinscription (valeur factice dans `.env.example`).
- `docker build -t hiver-solidaire .` : image d'exécution (JRE 21, non-root)
- Déploiement : `deploy/README.md`

## Architecture

- Découpage par fonctionnalité : `planning`, `inscription`, `benevole`, `auth`, `admin`, `notification`, `config`.
- Tests d'intégration web : `@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})` donne une horloge réglable (`MutableClock`) et un double de l'envoi (`RecordingMagicLinkSender`) ; `Browser` simule un navigateur (cookie de session, jeton CSRF, IP propre). `src/test/resources/config/application.properties` fixe les réglages propres aux tests (téléphone fictif des coordinateurs).
- Le planning s'affiche par journée, les données et les règles restent par soirée (ADR-0009) : `Day` regroupe le petit-déjeuner de la soirée D-1 et le reste de la soirée D. Paramètre `jour` dans le parcours d'inscription, `soiree` pour la désinscription.
- Inscriptions annulées logiquement (`cancelled_at`) : toute lecture d'inscriptions filtre `cancelled_at IS NULL`. Comptes supprimés anonymisés (`deleted_at`). Alertes aux coordinateurs écrites dans `coordinator_alert` (ADR-0008).
- Le domaine (règles R1 à R7) est en Java pur, sans dépendance Spring, et testé unitairement.
- Rendu serveur avec Thymeleaf ; htmx seulement pour des échanges partiels. Pas de SPA, pas de framework JavaScript.
- Toute décision structurante fait l'objet d'un ADR dans `docs/adr/` (modèle : `docs/adr/template.md`).

## Conventions

- Code, identifiants, commits et noms de tests en anglais. Interface et documentation en français.
- Textes de l'interface dans `messages.properties`, jamais en dur dans les templates.
- Commits au format Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`, `ci:`).
- Une PR par fonctionnalité. Description : ce qui change, comment c'est testé, points de sécurité, questions ouvertes.
- Dates et heures avec `java.time`, fuseau `Europe/Paris` pour les règles métier (veille à 23 h 59, rappels).

## Façon de travailler

1. Explorer le code concerné et la section du cahier des charges.
2. Pour une fonctionnalité : proposer un plan et attendre la validation avant d'écrire du code. Une correction ciblée (CI rouge, commentaire de revue) n'en a pas besoin.
3. Écrire les tests d'abord, puis le code.
4. Lancer `./mvnw verify` ; ne jamais pousser une branche rouge.
5. Lancer `/security-review` avant de proposer le merge, et traiter ce qui en sort.

## Règles de sécurité (non négociables)

- Aucun secret dans le dépôt. La configuration sensible passe par des variables d'environnement ; seul `.env.example` (valeurs factices) est versionné.
- Aucune donnée réelle dans le dépôt : ni adresse du lieu d'accueil, ni nom, e-mail ou téléphone de bénévole ou de l'association. Données de test factices (`@example.org`, numéros fictifs).
- Aucune donnée sur les personnes accueillies, nulle part.
- Aucune donnée personnelle dans les logs : ni e-mail, ni téléphone, ni nom. Un identifiant technique suffit.
- Autorisation vérifiée côté serveur sur chaque endpoint ; un bénévole n'accède qu'à ses propres données. Chaque endpoint protégé a un test d'accès refusé.
- Toute action qui modifie l'état passe par un POST protégé par CSRF. Jamais de GET qui modifie, liens d'e-mail compris (page de confirmation, puis POST).
- Thymeleaf : `th:text` pour tout contenu venant d'un utilisateur, jamais `th:utext`.
- Pas de script ni de style inline (la CSP les interdit) : JavaScript et CSS dans des fichiers statiques.
- Requêtes SQL paramétrées uniquement (JPA, `@Query` avec paramètres) ; jamais de concaténation.
- Aucun endpoint Actuator sur le port public.
- Une migration Flyway déjà fusionnée ne se modifie jamais ; on en ajoute une nouvelle.
- Pas de nouvelle dépendance sans la justifier dans la description de la PR.
- Ne désactive jamais un test ou un contrôle de sécurité pour faire passer la CI : signale le problème.

## Environnement

- Les sessions cloud de Claude Code ont Java 21, Maven et Docker : les tests Testcontainers y tournent. Elles n'ont pas accès au homelab : le déploiement se fait en local.
- Les fichiers `.env*` (sauf `.env.example`), `secrets/` et les clés sont illisibles par Claude Code (`.claude/settings.json`).
