# ADR-0005 — PostgreSQL plutôt que SQLite

- **Statut** : proposé
- **Date** : 2026-10-24

## Contexte

La règle R1 exige que jamais deux personnes n'obtiennent la dernière place d'une soirée : la base doit garantir la capacité par une transaction avec verrou sur la soirée ([cahier des charges](../cahier-des-charges.md) §5). Le volume est très faible (une vingtaine de bénévoles, une soixantaine de soirées par saison), mais les inscriptions peuvent arriver en rafale le jour de l'ouverture.

Le cahier des charges demande des tests d'intégration sur une vraie base (Testcontainers), des migrations Flyway versionnées et des sauvegardes chiffrées hors site.

## Options envisagées

1. **PostgreSQL** (conteneur dédié) : verrous de ligne (`SELECT … FOR UPDATE`) et isolation transactionnelle bien établis, outillage Spring, Flyway et Testcontainers courant, base identique en test, en développement et en production. En contrepartie, un service de plus à mettre à jour, superviser et sauvegarder, et une capacité largement surdimensionnée pour ce volume.
2. **SQLite** (fichier) : aucun service à exploiter, sauvegarde par copie de fichier, parfaitement adapté à ce volume. En contrepartie, un seul écrivain à la fois (les écritures concurrentes sont sérialisées au niveau de la base, pas de verrou de ligne), un dialecte et un support Hibernate moins courants, et un fichier à protéger sur le disque de l'appli ; le conteneur d'application est en lecture seule, ce qui impose un volume dédié.
3. **H2** (embarqué) : pratique en test, mais une autre base que celle de la production, donc des différences de comportement non détectées par les tests.

## Décision

Option 1 : PostgreSQL, avec Flyway. Le verrou de ligne sur la soirée traduit directement R1, et les tests d'intégration tournent sur le même moteur que la production.

SQLite aurait suffi pour le volume et simplifierait l'exploitation ; le choix de PostgreSQL privilégie la fidélité de test et un modèle de verrouillage explicite, au prix d'un conteneur supplémentaire. Je n'ai pas pu relire la documentation officielle de PostgreSQL et de SQLite depuis la session (accès réseau bloqué) : les affirmations sur les verrous et la concurrence sont à recouper avec leurs sources avant l'acceptation.

## Conséquences

- La base vit sur le réseau Docker dédié, jamais exposée, avec un compte applicatif aux droits limités (pas de superutilisateur).
- Sauvegarde logique quotidienne (`pg_dump`), chiffrée et envoyée hors site ; la restauration est testée avant l'ouverture.
- Les montées de version majeures de PostgreSQL demandent une procédure de migration à documenter dans le runbook.
- Les tests de concurrence sur R1 (deux inscriptions simultanées sur la dernière place) sont obligatoires.
- Une migration Flyway fusionnée ne se modifie jamais.
