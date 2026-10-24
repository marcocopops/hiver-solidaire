# S2 — Modèle de données et planning public

- **Semaine** : S2, du 26 octobre au 1er novembre 2026
- **Où** : session cloud Claude Code (claude.ai/code), en mode plan
- **Modèle conseillé** : Opus pour la conception du modèle de données et des règles R1-R7 (logique métier sensible aux erreurs), Sonnet acceptable si le plan est bien cadré
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s2-modele-et-planning-public.md, propose un plan, puis attends ma validation. »

## Objectif

Poser le modèle de données du planning et afficher une page publique en lecture seule : les soirées de la saison, leur état (complète, partielle, vide) et ce qu'il manque par poste. Aucune authentification, aucune inscription possible cette semaine : seule la lecture publique existe.

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md`, §4 (utilisateurs et rôles), §5 (modèle métier, règles R1 à R7), §6 (seulement le paragraphe sur l'adresse absente de la page publique), §9 (architecture)
- Les quatre ADR existants, en particulier [0005](../adr/0005-postgresql-plutot-que-sqlite.md) (PostgreSQL, verrous)
- `docs/securite/modele-de-menaces.md`, pour les actifs déjà identifiés sur le planning

## Hors périmètre (sessions suivantes)

Authentification par lien magique, création ou suppression d'une inscription, back-office des coordinateurs, e-mails, Turnstile, RGPD (notice, consentement, purge). Le modèle de données peut inclure la table des inscriptions si elle est nécessaire pour calculer l'état d'une soirée, mais rien ne doit permettre d'en créer une par l'interface ou une API publique cette semaine.

## Livrables

1. **Domaine métier en Java pur** (`planning`, sans dépendance Spring, testé unitairement en premier) :
   - Les postes d'une soirée et leurs besoins (§5, tableau « Besoins par soirée »), paramétrables plutôt que codés en dur.
   - **R1 Capacité** : calcul des places restantes par poste à partir des besoins et des inscriptions existantes. L'application de la règle en écriture (transaction avec verrou) attend l'inscription elle-même (S3) ; cette semaine, seul le calcul de lecture est requis.
   - **R3 Préparation** : représentation du choix plat principal ou accompagnements.
   - **R4 Récupération** : lien entre une préparation « chez soi » et une récupération le même soir.
   - **R5 Statut** : une soirée est complète, partielle ou vide ; couleur et texte associés.
   - **R7 Fenêtre** : une soirée passée n'accepte plus d'inscription (le test suffit ici ; l'endpoint de blocage viendra avec l'inscription en S3).
   - R2 (unicité) et R6 (désinscription) ne sont pas à coder cette semaine : ils supposent une inscription existante.
2. **Persistance** (`Saison`, `Soiree`, `Poste`/besoin, `Inscription` si nécessaire pour R1/R5) :
   - Entités Spring Data JPA, migrations Flyway dans `src/main/resources/db/migration` (nouvelle migration, jamais de modification de `V1__baseline.sql`).
   - Une saison a des dates de début et de fin paramétrables (§1 : « Elles seront paramétrables ») et une liste de soirées qui en découle. Les dates réelles 2026-2027 ne sont pas encore connues (question ouverte §11.2) : utiliser une saison d'exemple clairement marquée comme telle dans les migrations et la documentation, à remplacer par le back-office en S4.
   - Si la génération des soirées (lignes stockées à la création de la saison, ou calculées à la volée depuis les dates) est une décision structurante, l'exposer comme telle et écrire l'ADR correspondant (`docs/adr/0006-...md`), avec au moins deux options.
3. **Page publique** `/planning` (`HomeController` ou nouveau contrôleur du module `planning`) :
   - Liste les soirées de la saison, format d'affichage « Lun. 5 → mar. 6 janv. » (§5).
   - Pour chaque soirée : statut (couleur + texte, R5) et ce qui manque par poste, sans aucune donnée personnelle (ni noms, ni nombre exact d'inscrits si cela permettrait de déduire une identité pour une si petite communauté — se limiter à « manque : nuit, petit-déjeuner » par exemple).
   - Pas d'adresse du lieu sur cette page (§6, dernier paragraphe ; question ouverte §11.6, considérée tranchée par défaut : l'adresse n'apparaît jamais sur la page publique).
   - Mobile d'abord, textes dans `messages.properties`, `th:text` uniquement, cohérent avec la page d'accueil existante.
4. **Tests** :
   - Tests unitaires du domaine (R1, R3, R4, R5, R7), sans Spring ni base de données.
   - Test d'intégration Testcontainers : la page `/planning` répond 200, affiche les soirées et leur statut, sans fuite de donnée personnelle.
   - Test d'accès : `/planning` reste publique, mais toute route qui donnerait les coordonnées ou la liste nominative des inscrits (même si elle n'existe pas encore) n'est pas exposée par erreur.
5. **Documentation** : mettre à jour `docs/cahier-des-charges.md` si le modèle de données s'écarte du document (par exemple sur la question ouverte des soirées), dans la même PR plutôt que la modifier à part. Mettre à jour `docs/adr/README.md` si un ADR est ajouté.

## Règles

- Le domaine (R1 à R7) reste en Java pur ; la dépendance à Spring Data et PostgreSQL commence à la couche persistance.
- Aucune donnée réelle (adresse, nom, e-mail, téléphone) dans les migrations ou les données de test : uniquement des exemples factices.
- Toute divergence avec le cahier des charges (notamment sur la génération des soirées ou le format de la saison d'exemple) est signalée dans la PR, avec la proposition de mise à jour du document.

## Critères de fin

- [ ] `./mvnw verify` vert, avec des tests unitaires du domaine qui ne dépendent ni de Spring ni de PostgreSQL
- [ ] `/planning` répond 200, affiche la saison d'exemple avec un statut correct par soirée, sans donnée personnelle
- [ ] Migration Flyway ajoutée (jamais de modification d'une migration déjà fusionnée)
- [ ] ADR ajouté si une décision structurante a été prise sur la représentation des soirées
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, comment tester, points de sécurité, questions ouvertes

## Si quelque chose bloque

Si la question de la génération des soirées (stockées ou calculées) n'est pas claire, proposer les deux options dans le plan avant validation plutôt que de trancher seul. Si le format exact d'affichage du statut (couleurs, textes) n'est pas précisé par le cahier des charges, proposer une convention simple et la documenter dans `messages.properties` et le CSS, à ajuster ensuite.
