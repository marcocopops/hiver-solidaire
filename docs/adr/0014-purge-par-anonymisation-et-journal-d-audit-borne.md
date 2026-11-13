# ADR-0014 — Purge quotidienne par anonymisation, journal d'audit borné à six mois

- **Statut** : proposé
- **Date** : 2026-11-16

## Contexte

Le cahier des charges (§7) fixe des durées de conservation, retenues jusqu'à avis contraire de l'association :

- sans accord « Me recontacter la saison prochaine » : effacement un mois après la fin de la saison ;
- avec accord : jusqu'au retrait, et au plus trois ans sans participation ;
- compte créé à la connexion mais resté sans profil : supprimé au bout de sept jours ;
- données techniques (envois d'e-mails, alertes, jetons, sessions expirées) : six mois au plus.

Contraintes :

- les inscriptions passées restent comptées (indicateurs de fin de saison, §1) : on ne peut pas supprimer un bénévole qui en a ([ADR-0008](0008-annulation-logique-et-anonymisation.md)) ;
- le journal d'audit (`audit_log`, S4b) est en ajout seul : un déclencheur refuse `UPDATE` et `DELETE`. Il ne contient que des identifiants, mais `coordinator_id` désigne un coordinateur dont le compte existe, et `volunteer_id` un bénévole qui peut être encore actif : c'est une donnée personnelle tant que ces comptes existent ;
- une seule instance de l'appli, une horloge injectée (`MutableClock` en test), l'heure de Paris ;
- aucune donnée personnelle dans les logs.

## Options envisagées

### Effacement des bénévoles

1. **Supprimer les lignes** `volunteer` et leurs inscriptions : contraire à l'historique (§1, ADR-0008).
2. **Anonymiser** par le même chemin que la suppression du compte (`AccountDeletionService`) : la ligne reste, vidée de toute donnée personnelle (contrainte `volunteer_deleted_is_anonymous`), ses inscriptions passées restent comptées, ses liens et ses sessions sont supprimés.

Pour un compte sans profil, il n'y a aucune inscription (le profil est exigé avant) : la ligne est supprimée.

### Journal d'audit

1. **Le garder tel quel** : il ne contient que des identifiants ; une fois les bénévoles anonymisés, `volunteer_id` ne désigne plus personne. Mais `coordinator_id` désigne toujours un coordinateur actif, sans limite de durée.
2. **Le purger au-delà de six mois par une exception contrôlée du déclencheur** : `UPDATE` toujours refusé ; `DELETE` accepté seulement pour une ligne dont `occurred_at` a plus de six mois, **à l'horloge de la base**. Une erreur de l'appli ne peut effacer que ce qui a déjà dépassé la durée.
3. **Un rôle de base dédié à la purge** : plus fin, mais un second compte et ses secrets à gérer pour une seule requête.

### Déclenchement

1. **Tâche planifiée avec une table de suivi** (comme la table d'envois, ADR-0012).
2. **Tâche planifiée idempotente** : chaque étape ne retient que ce qui a dépassé sa durée et n'est pas déjà traité ; deux exécutions le même jour ne font rien de plus, une exécution manquée est rattrapée à la suivante.

## Décision

- **Anonymisation** (option 2) pour les bénévoles, **suppression** pour les comptes sans profil.
- **Journal d'audit purgé au-delà de six mois** (option 2, migration V19) : six mois couvrent une saison et les semaines qui la suivent, le temps d'examiner une contestation.
- **Tâche idempotente** (option 2), sans table de suivi : chaque jour à 4 h 30, heure de Paris (heure creuse, hors du changement d'heure), et une fois au démarrage pour rattraper un arrêt.

Règles appliquées (`privacy.RetentionPolicy`, Java pur, testée unitairement ; `privacy.DataRetention`) :

1. **Comptes sans profil**, créés il y a sept jours ou plus, sans inscription : suppression de la ligne, de ses liens de connexion, de ses sessions et de ses lignes d'envoi.
2. **Sans accord** : saison de référence = la plus récente dont la fin (lendemain matin de sa dernière soirée) plus un mois est passée, saison d'exemple comprise si elle n'a pas été remplacée. Ses bénévoles sans accord, créés avant sa fin et sans inscription sur une soirée postérieure, sont anonymisés. Un bénévole inscrit pour la saison suivante, ou arrivé après la fin, n'est pas touché. Un accord retiré après la saison est appliqué à l'exécution suivante. Les inscrits par téléphone, qui n'ont pas d'accord, sont compris.
3. **Avec accord** : dernière participation (soirée de la dernière inscription non annulée ; à défaut, création du compte) il y a trois ans ou plus → anonymisation.
4. **Données techniques de plus de six mois** : envois d'e-mails terminés (envoyés ou abandonnés), alertes aux coordinateurs (une alerte porte sur une soirée de la saison, passée six mois plus tard), journal d'audit. Les jetons expirés (toutes les heures) et les sessions expirées (chaque minute, Spring Session) étaient déjà purgés : rien à ajouter.

Un bénévole qui a une inscription à venir n'est jamais anonymisé : sa ligne est verrouillée et la condition vérifiée de nouveau dans la transaction de l'anonymisation. Chaque exécution laisse une ligne de log avec des comptes, jamais une identité.

## Conséquences

- La base garde une ligne anonyme par ancien bénévole, avec ses inscriptions passées : elle ne désigne personne.
- Le journal d'audit ne protège plus une action de plus de six mois : à réexaminer si l'association veut une durée plus longue (question ouverte, §11).
- Un coordinateur sans accord est anonymisé comme les autres ; il recrée son compte à sa prochaine connexion.
- Les sauvegardes antérieures gardent une copie jusqu'à leur expiration (S6) : la notice annonce « au plus 30 jours » d'ici là.
- Les tests de la purge prennent leur propre base (`DataRetentionTest`) : ils avancent l'horloge de plusieurs années et touchent toute la base. La tâche planifiée est coupée dans les tests (`app.retention.scheduler.enabled=false`).
- Changer une durée : une constante de `RetentionPolicy` (la notice suit), et le déclencheur V19 pour le journal d'audit, par une nouvelle migration.
