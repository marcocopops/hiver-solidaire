# CLAUDE.md — Hiver Solidaire

Planning en ligne des bénévoles d'un accueil hivernal de personnes sans abri : une vingtaine de bénévoles majeurs, 2 à 3 coordinateurs, ouverture des inscriptions le 14 décembre 2026.

La référence est `docs/cahier-des-charges.md` : règles métier R1 à R7 (§5), parcours (§6), RGPD (§7), sécurité (§8), architecture (§9). Lis la section concernée avant toute fonctionnalité. Si le code doit s'écarter du cahier des charges, dis-le et propose la modification du document dans la même PR.

## Stack

- Java 21, Spring Boot 4.1.x, Spring Security 7, Spring Data JPA, Thymeleaf ; htmx servi depuis le dépôt (pas de CDN). Seule exception : le script de Cloudflare Turnstile sur `/connexion` (ADR-0013), CSP ouverte pour cette page seulement
- PostgreSQL, migrations Flyway dans `src/main/resources/db/migration`
- Maven via le wrapper `./mvnw`
- Tests : JUnit 5, AssertJ, Spring Security Test, Testcontainers (PostgreSQL)
- Package racine : `com.marcantoinemeunier.hiversolidaire`

## Commandes

Mets cette section à jour si elles changent.

- `./mvnw verify` : compilation, tests unitaires et d'intégration (Testcontainers : Docker doit tourner). Doit être vert avant chaque push.
- `docker compose up -d` : PostgreSQL de développement sur 127.0.0.1:5433 et Mailpit (SMTP 127.0.0.1:1025, boîte de réception sur http://127.0.0.1:8025), projet `hiver-solidaire-dev` distinct de la production (copier `.env.example` vers `.env`)
- `./mvnw spring-boot:run` : application sur http://localhost:8080 (utiliser `localhost`, pas `127.0.0.1` : cookie `__Host-` Secure) ; santé sur http://localhost:8081/actuator/health. Le lien de connexion et les e-mails automatiques arrivent dans Mailpit. `APP_MAIL_CONFIRMATION_DELAY=PT30S` raccourcit le regroupement des confirmations (10 min par défaut) ; `APP_MAIL_DEV_RUN_NOW=true` crée et envoie au démarrage le rappel de demain et le récap du jour (dev seulement, une fois par jour : `scripts/seed-dev.sh --reset` pour recommencer). `APP_CLOCK_OFFSET=P72D` décale l'horloge pour s'inscrire avant l'ouverture (dev seulement) ; la purge tourne au démarrage avec cette horloge (`P170D` : après la fin de la saison d'exemple plus un mois, les bénévoles factices sans accord sont anonymisés). `TURNSTILE_SITE_KEY` et `TURNSTILE_SECRET_KEY` : clés de test de Cloudflare dans `.env.example` (toujours valides ; secrète `2x…` pour voir un refus), acceptées seulement avec une `APP_BASE_URL` locale. `APP_MAIL_ALLOW_RESERVED_DOMAINS=true` (dev seulement) : Mailpit reçoit les adresses `@example.org`, que le garde-fou bloque sinon. `APP_COORDINATOR_EMAILS` : adresses des coordinateurs, séparées par des virgules (ADR-0010) ; en local, se connecter avec une adresse factice de `.env.example` pour ouvrir le back-office sur http://localhost:8080/admin.
- `scripts/seed-dev.sh [--reset]` : données factices dans la base de développement (21 bénévoles, ~60 % des soirées des 4 premières semaines), idempotent ; `--reset` vide aussi la table d'envois ; refuse un autre projet Compose que `hiver-solidaire-dev` ou une base non migrée. Afficher le planning avec le `APP_CLOCK_OFFSET` qu'il indique. Testé par `SeedDevScriptTest` (même fichier SQL).
- `docker build -t hiver-solidaire .` : image d'exécution (JRE 21, non-root)
- `docker run --rm -v "$PWD:/mnt:ro" -w /mnt koalaman/shellcheck:v0.11.0@sha256:61862eba1fcf09a484ebcc6feea46f1782532571a34ed51fedf90dd25f925a8d -x deploy/*.sh scripts/*.sh` : shellcheck des scripts, sans avertissement (même image que la CI).
- Scripts d'exploitation (`deploy/backup.sh`, `restore.sh`, `update.sh`, ADR-0015 et 0016) : testés par `BackupRestoreScriptTest` et `UpdateScriptTest`, qui lancent les vrais scripts (Docker Compose v2.24 ou plus requis) avec un dépôt restic local, un faux conteneur `app` et un faux service de ping. Procédures : `docs/exploitation/runbook.md`.
- Déploiement : `deploy/README.md`

## Architecture

- Découpage par fonctionnalité : `planning`, `inscription`, `benevole`, `auth`, `admin`, `notification`, `privacy`, `monitoring`, `config`.
- Tests d'intégration web : `@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})` donne une horloge réglable (`MutableClock`), des doubles de l'envoi du lien (`RecordingMagicLinkSender`) et des e-mails automatiques (`RecordingMailTransport`, à vider au début du test), une liste de coordinateurs modifiable (`TestCoordinatorEmails`), un double de Turnstile (`StubTurnstile`, accepte le jeton factice `Browser.TURNSTILE_TOKEN`) et le garde-fou des domaines réservés, désactivé par défaut (`TestRecipientGuard`) : un test qui modifie un double le remet à zéro après ; `Browser` simule un navigateur (cookie de session, jeton CSRF, IP propre) et demande un lien par `requestLink` (adresse et jeton Turnstile) ; `TestVolunteers` connecte un bénévole ou un coordinateur par le vrai parcours. Une session de coordinateur dure 8 h : se connecter après avoir avancé l'horloge. Un test qui change la saison prend sa propre base (`@TestPropertySource` distinct et `@DirtiesContext`, voir `SeasonAdminTest`). `AdminAccessTest` vérifie le refus sur toutes les routes `/admin/**` déclarées. Le contact des coordinateurs ne vient que des réglages de la saison : un test qui l'affiche le renseigne sur la saison et le remet à vide après (voir `CancellationJourneyTest`).
- Le planning s'affiche par journée, les données et les règles restent par soirée (ADR-0009) : `Day` regroupe le petit-déjeuner de la soirée D-1 et le reste de la soirée D. Paramètre `jour` dans le parcours d'inscription, `soiree` et `choix` pour la désinscription (un bénévole peut tenir plusieurs postes d'une soirée, un par poste : ADR-0011).
- Inscriptions annulées logiquement (`cancelled_at`) : toute lecture d'inscriptions filtre `cancelled_at IS NULL`. Comptes supprimés anonymisés (`deleted_at`). Alertes aux coordinateurs écrites dans `coordinator_alert` (ADR-0008). Un bénévole inscrit par téléphone n'a pas d'e-mail (`email IS NULL`, profil complet) et ne peut pas se connecter.
- E-mails automatiques (`notification`, ADR-0012) : chaque e-mail est une ligne de `mail_delivery` (identifiants et dates seulement), écrite dans la transaction de sa cause (`MailOutbox` pour les confirmations, `CoordinatorAlerts` pour les alertes immédiates) ou par `MailPlanner` à son heure (`MailSchedule`, heure de Paris) ; la base refuse les doublons. `MailDispatcher` calcule le contenu à l'envoi (un `MailComposer` par type, gabarits `templates/mail/*.txt` et `*.html`) et envoie hors transaction. La tâche planifiée est coupée dans les tests (`src/test/resources/application.properties`) : appeler `MailJobs.tick()` après avoir réglé l'horloge. Le contexte de test est partagé : filtrer les e-mails par destinataire.
- Données personnelles (`privacy`, ADR-0014) : notice publique `/donnees-personnelles` ; purge quotidienne `DataRetention` (4 h 30 et au démarrage, coupée dans les tests : appeler `DataRetention.run()` après avoir réglé l'horloge) selon `RetentionPolicy` (Java pur), qui donne aussi les durées affichées par la notice. Ses tests prennent leur propre base (`DataRetentionTest`). Le journal d'audit n'accepte de suppression que pour ses lignes de plus de 6 mois (V19). Aucun e-mail vers un domaine réservé aux tests (`notification.RecipientGuard`).
- Back-office (`admin`) : rôle `COORDINATOR` exigé sur `/admin/**` par `SecurityConfig`. Chaque action passe par `admin.CoordinatorActions`, qui écrit sa ligne `audit_log` (`AuditLog`, identifiants seulement, table en ajout seul) dans la même transaction ; les écritures sur les inscriptions restent dans `RegistrationService`, sous le verrou de la soirée. Réglages et création de saison : `planning.season.SeasonSettingsService`.
- Page de santé publique `/sante` (`monitoring`) pour la sonde externe : route fonctionnelle (`RouterFunction`), hors des `@ControllerAdvice` qui lisent la base ; code de statut seulement, résultat gardé 10 s (`app.health.cache-ttl`).
- Fichiers de `deploy/` : tout nouveau fichier entre dans la liste blanche de `.dockerignore` (copiés dans l'image, comparés par `update.sh`, vérifié par `UpdateScriptTest`).
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
