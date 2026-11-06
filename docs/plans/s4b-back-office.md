# Plan S4b — Back-office des coordinateurs

*Session sans surveillance du 3 novembre 2026. Ce plan sert de validation a posteriori (prompt `docs/prompts/s4b-back-office.md`, section « Travail sans surveillance »). Les décisions par défaut du prompt sont appliquées telles quelles ; les autres choix sont marqués **(décision)** et repris dans la section « Décisions prises sans moi » de la PR.*

## Ce que j'ai lu

`CLAUDE.md`, le cahier des charges (§2, §4 à §8, §11), les ADR 0001 à 0009, le modèle de menaces, les modules `auth`, `planning`, `inscription`, `benevole`, `notification` et les migrations V1 à V9.

Points d'appui dans le code existant :

- `VolunteerPrincipal(UUID id)` : le principal ne contient que l'identifiant technique ; il est sérialisé dans la session JDBC. Ajouter un composant `boolean coordinator` à ce record reste compatible avec les sessions déjà en base (un champ absent du flux prend sa valeur par défaut, `false`).
- `SessionLifetimeFilter` borne déjà la durée absolue à 30 jours ; il choisira 8 h pour un coordinateur (ADR-0007 l'avait prévu).
- `RegistrationService` porte le verrou de R1 (`SELECT … FOR UPDATE` sur la soirée), R2 à R7 via `Evening.decide`, l'annulation logique et l'alerte `coordinator_alert`. Les actions des coordinateurs passent par lui, sous le même verrou.
- `AccountDeletionService` fait déjà l'anonymisation d'ADR-0008 : la suppression demandée à un coordinateur le réutilise.
- `Season.eveningDates()` fixe la règle de génération des soirées (ADR-0006).

## Étapes, dans l'ordre de priorité

Chaque étape se termine tests verts, avec un commit (ou plusieurs) poussé.

### 1. Rôle, accès et journal d'audit

- `APP_COORDINATOR_EMAILS` → `app.coordinators.emails` ; `auth.CoordinatorEmails` normalise chaque adresse avec `EmailAddress.normalize` ; une entrée invalide empêche le démarrage (comme `APP_COORDINATORS_PHONE`). Les adresses ne sont jamais journalisées.
- À la connexion par lien magique, le principal reçoit `coordinator = true` si l'adresse est dans la liste : rôles `VOLUNTEER` et `COORDINATOR`. Une adresse retirée de la liste perd le rôle à la prochaine connexion (et au plus tard 8 h après la précédente, voir ci-dessous).
- `SessionLifetimeFilter` : 8 h pour une session coordinateur, 30 jours sinon. Au-delà, la session est invalidée : reconnexion complète.
- `SecurityConfig` : `/admin` et `/admin/**`, toutes méthodes, `hasRole("COORDINATOR")`, avant le `denyAll` final. Un visiteur reçoit 403 (pas de redirection, rien n'est révélé) ; un bénévole non coordinateur reçoit 403. CSRF inchangé (actif).
- Après connexion, un coordinateur sans choix en cours arrive sur `/admin` (**décision**) ; la navigation affiche « Back-office » aux coordinateurs.
- **Journal d'audit** : migration `V10__coordinator_role_and_audit.sql`, table `audit_log` (id, `occurred_at`, `coordinator_id`, `action` bornée par un `CHECK`, identifiants des objets : `volunteer_id`, `registration_id`, `evening_id`, `target_evening_id`, `season_id`, et `period_from`/`period_to` pour les exports). Aucun texte libre, aucune colonne pour un nom, un e-mail ou un téléphone. Un déclencheur refuse `UPDATE` et `DELETE` (**décision** : journal en ajout seul ; une purge future passera par une migration). Pas de clé étrangère vers les objets visés : le journal survit à la suppression d'une saison d'exemple ou d'une inscription annulée (**décision**).
- `admin.AuditLog.record(...)` en `Propagation.MANDATORY` : la ligne est écrite dans la transaction de l'action, ou pas du tout.
- Page d'accueil `/admin` (le tableau de bord de l'étape 2).

Tests : accès refusé à un visiteur et à un bénévole sur **toutes** les routes `/admin/**` (liste tirée des `RequestMappingHandlerMapping`, pour qu'une route oubliée fasse échouer le test), GET et POST ; POST sans CSRF refusé pour un coordinateur ; rôle perdu à la reconnexion ; session coordinateur refusée après 8 h, bénévole gardée ; parsing de `APP_COORDINATOR_EMAILS`.

### 2. Tableau de bord (`GET /admin`)

- Journées incomplètes des 14 prochains jours (aujourd'hui compris), en tête, avec ce qui manque par poste (R5, via `Day`).
- Alertes enregistrées non traitées (`coordinator_alert`) : migration qui ajoute `handled_at` et `handled_by` ; bouton « Marquer comme traitée » (POST, audité).
- Préparations chez soi sans récupération (R4), soirées à venir.
- Nouveaux bénévoles à signaler (§2) : profil rempli, compte non supprimé, pas encore « vu » par un coordinateur ; migration qui ajoute `volunteer.reviewed_at` ; bouton « Vu » (POST, audité). Un bénévole créé par un coordinateur est vu d'office.

### 3. Fiche journée (`GET /admin/journee?jour=…`)

- Les deux soirées de la journée (petit-déjeuner de la soirée D-1, dîner et nuit de la soirée D), statut par poste, et les inscrits avec nom, prénom, pseudo, téléphone et e-mail (`th:text` partout).
- Actions, chacune page de confirmation (GET) puis POST, auditée :
  - **annuler** une inscription, même après la limite de R6 (pas sur une soirée passée), avec l'alerte « soirée devenue incomplète » comme pour un bénévole ;
  - **déplacer** une inscription vers une autre soirée : mêmes type et plat ; les deux soirées verrouillées dans l'ordre des dates ; la soirée cible est contrôlée par `Evening.decide` (R1 à R4 et R7, R2 pour ce bénévole) ; l'ancienne inscription est annulée et une nouvelle est créée (historique conservé) ;
  - **inscrire un bénévole existant** (mêmes règles que l'inscription en ligne, sous le même verrou) ;
  - **créer un bénévole sans e-mail** (nom, prénom, pseudo, téléphone), puis l'inscrire.
- Migration : `volunteer.email` facultatif aussi pour un compte actif, à condition d'avoir un profil complet (nom, prénom, pseudo, téléphone) ; unicité des adresses présentes inchangée. Sans e-mail, aucune connexion possible (la recherche par adresse ne trouve jamais une ligne sans adresse).

Tests : règles du déplacement, annulation après R6 par un coordinateur (et toujours refusée au bénévole), concurrence coordinateur/bénévole sur la dernière place, bénévole sans e-mail créé, inscrit, visible par son pseudo sur `/planning`, sans connexion possible ; une ligne d'audit par action, sans donnée personnelle.

### 4. Bénévoles (`/admin/benevoles`)

- Liste avec recherche (nom, prénom, pseudo, e-mail, téléphone ; `LIKE` paramétré, caractères `%` et `_` échappés), nouveaux bénévoles signalés.
- Fiche : coordonnées, inscriptions à venir et passées, correction du nom, du prénom et du pseudo (mêmes règles de forme et d'unicité que le bénévole, §11 question 8), « Vu », suppression sur demande (page de confirmation, puis `AccountDeletionService`, ADR-0008). Un coordinateur ne supprime pas son propre compte depuis le back-office : il passe par « Mon compte » (**décision**).

### 5. Saison (`/admin/saison`)

- Migration : réglages de la saison (`venue_address`, `instructions`, `coordinators_phone`) et horaires affichés par poste (`season_need.schedule`), remplis pour la saison d'exemple avec les horaires du §5.
- Modification : libellé, première et dernière soirée, ouverture des inscriptions (heure de Paris), besoins min/max par poste, horaires, adresse, consignes, téléphone des coordinateurs. Les soirées sont régénérées (`Season.eveningDates()`) : ajout des nouvelles dates, suppression de celles qui sortent de la saison **seulement si elles n'ont aucune inscription active** ; sinon refus, avec les dates en cause. Les inscriptions annulées et les alertes d'une soirée supprimée sont supprimées avec elle (**décision**).
- Création d'une saison (une seule active à la fois) : si la saison courante est la saison d'exemple, la création la remplace dans la même transaction, et seulement si elle n'a aucune inscription active ; sinon l'écran l'indique. Si une vraie saison est en cours, la création est refusée.
- Téléphone des coordinateurs : celui de la saison, sinon `APP_COORDINATORS_PHONE`.
- L'adresse et les consignes apparaissent dans « Mes inscriptions » (espace bénévole), jamais sur la page publique (§6).

### 6. Exports

- `GET /admin/export/inscriptions.csv?du=…&au=…` : inscriptions actives de la période (jour d'activité, activité, nom, prénom, pseudo, téléphone, e-mail). Cellules entre guillemets, guillemets doublés, et toute cellule qui commence par `=`, `+`, `-`, `@`, tabulation ou retour chariot préfixée d'une apostrophe (injection de formule). `Cache-Control: no-store`. Export audité (période).
- `GET /admin/impression` : planning des 7 prochains jours, page imprimable (CSS `@media print`), à enregistrer en PDF par le navigateur. Aucune nouvelle dépendance. Consultation auditée.

### 7. Exception à R2 (si le temps le permet)

Migration sur l'index d'unicité (`coordinator_exception`), et case « exception décidée par un coordinateur » à l'inscription par un coordinateur. Beaucoup de code suppose « au plus une inscription par bénévole et par soirée » (désinscription par date, `Evening.without`, « Mes inscriptions ») : à ne faire que si tout le reste est fini, sinon à lister comme restant.

## Documentation dans la même PR

- Cahier des charges : §6 (back-office livré), §8 (sessions de 8 h, Access), §11 (questions 1, 2, 8), décisions par défaut, exports CSV + page imprimable.
- ADR-0010 : désignation des coordinateurs par variable d'environnement (options : variable d'environnement, colonne en base gérée depuis le back-office, groupe Cloudflare Access transmis à l'appli).
- Modèle de menaces : M6 (compte coordinateur compromis), M8, M13 et export CSV (injection de formule).
- `deploy/README.md` : `APP_COORDINATOR_EMAILS`, application Cloudflare Access sur `/admin/*`.
- `CLAUDE.md` et `.env.example`.

## Tests transverses

- Aucun nom, e-mail ni téléphone dans les logs (sortie capturée pendant un parcours coordinateur complet).
- `/planning` ne montre toujours que les pseudos, y compris pour un bénévole créé par un coordinateur.
- Les tests qui modifient la saison tournent dans leur propre contexte Spring (donc leur propre base Testcontainers), pour ne pas changer la saison vue par les autres tests.
