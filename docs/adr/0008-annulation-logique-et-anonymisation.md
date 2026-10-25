# ADR-0008 — Annulation logique et anonymisation plutôt que suppression

- **Statut** : proposé
- **Date** : 2026-10-25

## Contexte

S4a ouvre la désinscription (R6) et la suppression du compte par le bénévole ([cahier des charges](../cahier-des-charges.md) §6, §7). Trois besoins tirent dans des sens différents :

- **l'historique** : une inscription passée reste comptée (indicateurs de fin de saison, §1), même si son auteur supprime son compte ;
- **les données personnelles** : après suppression, plus aucun e-mail, nom, prénom, pseudo ni téléphone en base, et les inscriptions passées ne sont plus rattachables à une personne ;
- **les garanties en base** : R2 (une inscription par bénévole et par soirée) et R3 (un plat de chaque par soirée) sont des contraintes d'unicité (V2, V4) ; l'e-mail est unique et non nul, le pseudo unique sans tenir compte de la casse (V4, V7), `registration.volunteer_id` est non nul.

S'y ajoute l'alerte « soirée devenue incomplète » : calculée dès S4a, envoyée par e-mail en S5.

## Options envisagées

### Désinscription

1. **Supprimer la ligne `registration`.**
   - Avantage : aucune contrainte à changer.
   - Inconvénients : on perd la trace du désistement (utile aux coordinateurs, au back-office de S4b) ; rien à quoi rattacher une alerte.
2. **Annulation logique** (`cancelled_at`), unicités R2 et R3 en index uniques partiels `WHERE cancelled_at IS NULL`.
   - Avantages : la base garantit toujours R2 et R3 sur les inscriptions actives ; un bénévole peut se réinscrire ; l'historique reste.
   - Inconvénients : chaque lecture doit filtrer les annulées (oubli = place fantôme) ; une contrainte `UNIQUE` ne pouvant pas être partielle, R2 devient un index.

### Suppression du compte

1. **Supprimer la ligne `volunteer`** et ses inscriptions : perd l'historique, contraire au §6.
2. **Une ligne « bénévole supprimé » partagée** vers laquelle on rattache les inscriptions passées : deux bénévoles supprimés inscrits la même soirée passée violeraient R2.
3. **E-mail factice dérivé de l'identifiant** (`<uuid>@deleted.invalid`) : respecte `NOT NULL`, mais la base ne distingue plus un compte supprimé d'un compte réel, et une adresse inventée reste une donnée qui ressemble à une donnée.
4. **Anonymiser la ligne en place** : `deleted_at` posé, e-mail, nom, prénom, pseudo, téléphone et `profile_completed_at` à NULL, accord de recontact à faux. L'e-mail devient nullable, mais seulement pour un compte supprimé, et un compte supprimé ne peut garder aucune donnée personnelle (`CHECK volunteer_deleted_is_anonymous`).

### Alerte aux coordinateurs

1. **Événement applicatif en mémoire** (`@TransactionalEventListener`) : simple, mais perdu si l'appli redémarre ou si l'envoi échoue.
2. **Table d'événements** (`coordinator_alert`), écrite dans la transaction de l'annulation : l'alerte n'existe que si l'annulation a eu lieu, et survit au redémarrage.

## Décision

- Désinscription : **annulation logique**, R2 et R3 en index uniques partiels.
- Suppression : **anonymisation en place**, garantie par un `CHECK`. Les unicités de l'e-mail et du pseudo ne sont pas affaiblies : NULL n'entre en conflit avec rien, une même adresse peut recréer un compte et un autre bénévole peut reprendre le pseudo.
- Alerte : **table `coordinator_alert`**, sans donnée personnelle (la soirée seule). S5 enverra les lignes sans `sent_at`, puis posera `sent_at`.

Mise en œuvre (`V8__cancellation_and_account_deletion.sql`) :

- l'annulation et la suppression prennent le même verrou que l'inscription (`SELECT … FOR UPDATE` sur la soirée, ADR-0006) ; la suppression verrouille les soirées dans l'ordre des dates ;
- la suppression annule toutes les inscriptions à venir, y compris après la limite de R6, supprime les liens de connexion en cours (ils contiennent l'adresse) et toutes les sessions du bénévole (ADR-0007), puis invalide la session de la requête.

## Conséquences

- Toute requête sur `registration` filtre `cancelled_at IS NULL` ; les tests de capacité, de planning et de « Mes inscriptions » le vérifient.
- Une ligne anonyme par ancien bénévole : ses inscriptions passées restent liées entre elles, mais à aucune personne. La purge de fin de saison (S5) pourra supprimer ces lignes avec leurs inscriptions quand l'historique ne sera plus utile.
- Les horodatages (`created_at`, `updated_at`, `deleted_at`) restent : ils ne désignent personne sans le reste de la ligne.
- `coordinator_alert` resservira aux alertes R4 et « nouveau bénévole » (S5) : ajouter une valeur à `coordinator_alert_kind` par migration.
- Les sauvegardes antérieures à une suppression contiennent encore les données : leur durée de conservation (S6) borne la durée réelle d'effacement, à mentionner dans la notice.
