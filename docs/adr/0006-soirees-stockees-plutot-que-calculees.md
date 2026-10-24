# ADR-0006 — Soirées stockées plutôt que calculées

- **Statut** : accepté
- **Date** : 2026-10-24

## Contexte

L'unité du planning est la soirée, du soir J au matin J+1 ([cahier des charges](../cahier-des-charges.md) §5). Une saison en compte environ 76, et ses dates sont paramétrables (§3). Il faut décider maintenant, avec le modèle de données (S2), si une soirée existe en base ou seulement comme une date comprise entre le début et la fin de la saison.

Trois besoins dépendent de ce choix :

- **R1 Capacité** : la base garantit qu'il n'y a jamais deux personnes sur la dernière place, par une transaction avec verrou sur la soirée ([ADR-0005](0005-postgresql-plutot-que-sqlite.md)).
- **R7 Fenêtre** : un coordinateur peut fermer une soirée.
- **Inscriptions** : chaque inscription se rattache à une soirée.

## Options envisagées

1. **Soirées stockées** : une ligne `evening` par soirée, créée en même temps que la saison.
   - Avantages :
     - la ligne est la cible naturelle du verrou de R1 (`SELECT … FOR UPDATE` sur la soirée) ;
     - les inscriptions la référencent par une clé étrangère ;
     - la fermeture d'une soirée est une simple colonne `closed` ;
     - un réglage propre à une soirée (besoins, horaires) pourra s'ajouter plus tard sans changer le modèle.
   - Inconvénients :
     - changer les dates d'une saison oblige à régénérer les soirées : ajouter les nouvelles dates, supprimer celles qui sortent de la saison, et refuser la suppression d'une soirée qui a des inscriptions ;
     - les dates figurent à deux endroits, sur la saison et dans les soirées.
2. **Soirées calculées** : seule la saison est stockée, et les soirées sont déduites de ses dates.
   - Avantages : aucune duplication, et changer les dates ne demande aucune opération.
   - Inconvénients :
     - les inscriptions référencent un couple (saison, date) sans clé étrangère vers une soirée ;
     - le verrou de R1 doit porter sur la saison entière, ce qui sérialise toutes les inscriptions de la saison, ou passer par un verrou consultatif (`pg_advisory_xact_lock`), moins lisible ;
     - les soirées fermées et les réglages propres à une soirée demandent une table à part, qui finit par ressembler à l'option 1.

## Décision

Option 1 : soirées stockées. La ligne de la soirée porte le verrou de R1, la fermeture de R7 et le rattachement des inscriptions. C'est la traduction la plus directe des règles du cahier des charges.

En S2, une migration crée la saison d'exemple avec `generate_series`. `Season.eveningDates()` (domaine, testé) fixe la règle de génération : toutes les dates du soir entre la première et la dernière soirée, bornes comprises. Le back-office (S4) s'appuiera dessus.

## Conséquences

- R1 se traduira en S3 par `SELECT … FOR UPDATE` sur la ligne `evening`, puis par le contrôle des places et l'insertion de l'inscription, dans la même transaction.
- La modification des dates d'une saison (S4) doit régénérer ses soirées. Elle ne supprime jamais une soirée qui a des inscriptions : elle refuse, et un coordinateur déplace d'abord les inscriptions. Un test couvrira ce cas.
- L'unicité `(season_id, evening_date)` est garantie en base. Que chaque soirée tombe bien entre les dates de sa saison est garanti par le code qui génère les soirées, pas par une contrainte.
- Le volume reste négligeable : environ 80 lignes par saison.
