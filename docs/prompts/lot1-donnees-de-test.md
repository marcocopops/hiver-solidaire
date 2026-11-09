# Lot 1 — Jeu de données de test pour la base de développement

- **Quand** : après la PR #20 (back-office), en premier des lots de corrections ; peut tourner en parallèle du lot 2 (fichiers différents)
- **Où** : Claude Code en local, en mode plan ; effort par défaut
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot1-donnees-de-test.md, propose un plan, puis attends ma validation. »

## Objectif

Pouvoir remplir en une commande une bonne partie de la saison avec des données réalistes, pour tester à la main le planning, le back-office et les exports sans s'inscrire vingt fois.

## Livrables

1. **`scripts/seed-dev.sh`** (et le SQL qu'il exécute, dans `scripts/`), qui passe par le conteneur PostgreSQL de développement : `docker compose exec -T db psql …`.
   - **Garde-fous** : refuse de tourner si le projet Compose n'est pas `hiver-solidaire-dev`, ou si la base n'a pas le schéma attendu. Ne lit jamais `.env` lui-même (illisible pour Claude Code) : utiliser les variables du conteneur (`$POSTGRES_USER`, `$POSTGRES_DB`) dans `docker compose exec`.
   - **Option `--reset`** : vide d'abord les inscriptions, les alertes, le journal d'audit (si le déclencheur d'ajout seul l'empêche, le dire et proposer une solution, par exemple `docker compose down -v` documenté) et les bénévoles factices créés par le script.
   - **Idempotent** : relancé sans `--reset`, il ne double pas les données.
2. **Contenu généré**, uniquement factice :
   - une vingtaine de bénévoles `prenom.nom@example.org`, numéros fictifs (plage ARCEP réservée à la fiction, comme `.env.example`), pseudos au format « PrénomN » ;
   - un bénévole sans e-mail (inscrit par téléphone) ;
   - environ 60 % des soirées des 4 premières semaines de la saison remplies, avec : des journées complètes, des journées incomplètes à divers degrés, une préparation chez soi sans récupération (R4), une préparation chez soi avec sa récupération, des inscriptions annulées, quelques nouveaux bénévoles non « vus ».
   - Toutes les inscriptions respectent R1 à R4 et R7 **par construction** (le script n'appelle pas l'appli : il faut donc écrire les inserts en respectant les règles du domaine, à vérifier par une requête de contrôle à la fin du script).
3. **Documentation** : section « Données de test » dans `README.md` et commande dans `CLAUDE.md`. Préciser qu'il faut lancer l'appli avec `APP_CLOCK_OFFSET` pour voir les soirées comme à venir.

## Contraintes

- Rien dans l'appli Java : pas de profil, pas d'endpoint, rien qui puisse partir en production.
- Aucune donnée réelle.
- Le script doit marcher sur une base fraîchement migrée (`docker compose down -v`, `docker compose up -d`, démarrage de l'appli une fois pour Flyway).

## Tests

- Un test d'intégration (Testcontainers) exécute le SQL du script sur une base migrée et vérifie : nombre de bénévoles et d'inscriptions, aucune violation de R1 à R4 (requêtes de contrôle), idempotence (deux exécutions = mêmes comptes).

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] `scripts/seed-dev.sh --reset` testé à la main, planning et back-office remplis
- [ ] PR ouverte : résumé, comment l'utiliser, garde-fous
