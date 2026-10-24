# ADR-0005 — PostgreSQL plutôt que SQLite

- **Statut** : proposé
- **Date** : 2026-10-24

## Contexte

La règle R1 exige que jamais deux personnes n'obtiennent la dernière place d'une soirée : la base doit garantir la capacité par une transaction avec verrou sur la soirée ([cahier des charges](../cahier-des-charges.md) §5). Le volume est très faible (une vingtaine de bénévoles, une soixantaine de soirées par saison), mais les inscriptions peuvent arriver en rafale le jour de l'ouverture.

Le cahier des charges demande des tests d'intégration sur une vraie base (Testcontainers), des migrations Flyway versionnées et des sauvegardes chiffrées hors site.

## Options envisagées

1. **PostgreSQL** (conteneur dédié) : verrous de ligne (`SELECT … FOR UPDATE` : les autres transactions qui tentent de modifier ou verrouiller ces lignes attendent la fin de la transaction en cours, d'après la [documentation](https://www.postgresql.org/docs/current/explicit-locking.html)) et isolation transactionnelle bien établis, outillage Spring, Flyway et Testcontainers courant, base identique en test, en développement et en production. En contrepartie, un service de plus à mettre à jour, superviser et sauvegarder, et une capacité largement surdimensionnée pour ce volume.
2. **SQLite** (fichier) : aucun service à exploiter, sauvegarde par copie de fichier, parfaitement adapté à ce volume. En contrepartie, un seul écrivain à tout instant (lecteurs illimités) : les écritures font la queue, ce qui ne pose pas de problème dans beaucoup de situations selon la [documentation](https://www.sqlite.org/whentouse.html) et suffirait pour ~20 bénévoles, mais le verrou porte sur la base entière, pas sur une ligne. S'y ajoutent un dialecte et un support Hibernate moins courants, et un fichier à protéger ; le conteneur d'application étant en lecture seule, il faudrait un volume dédié.
3. **H2** (embarqué) : pratique en test, mais une autre base que celle de la production, donc des différences de comportement non détectées par les tests.

## Décision

Option 1 : PostgreSQL, avec Flyway. Le verrou de ligne sur la soirée traduit directement R1, et les tests d'intégration tournent sur le même moteur que la production.

SQLite aurait suffi pour le volume et simplifierait l'exploitation ; le choix de PostgreSQL privilégie la fidélité de test et un modèle de verrouillage explicite par ligne, au prix d'un conteneur supplémentaire.

## Conséquences

- La base vit sur le réseau Docker dédié, jamais exposée, avec un compte applicatif aux droits limités (pas de superutilisateur).
- Sauvegarde logique quotidienne (`pg_dump`), chiffrée et envoyée hors site ; la restauration est testée avant l'ouverture.
- Les montées de version majeures de PostgreSQL demandent une procédure de migration à documenter dans le runbook.
- Les tests de concurrence sur R1 (deux inscriptions simultanées sur la dernière place) sont obligatoires.
- Une migration Flyway fusionnée ne se modifie jamais.
