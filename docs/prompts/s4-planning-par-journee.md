# S4 — Planning par journée, filtres et désinscription depuis le planning

- **Semaine** : S4 (après S4a ; avant la session de design et le back-office S4b)
- **Où** : Claude Code en local, sur le PC de développement, en mode plan
- **Modèle conseillé** : Opus (regroupement sur deux soirées et règles R1 à R7 : les erreurs y sont coûteuses)
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s4-planning-par-journee.md, propose un plan, puis attends ma validation. »

## Objectif

Changer ce que montre le planning, avant de le mettre en forme : une carte par **journée** plutôt que par soirée, deux statuts au lieu de quatre, des filtres, et la désinscription accessible depuis la carte. Cette session ne fait pas le design : la mise en forme de la piste retenue viendra juste après (`s4-design-veillee.md`). Les gabarits restent sobres, mais leur structure doit déjà permettre ce design.

Référence visuelle (pour la structure seulement, pas pour le style) : `docs/design/planning-par-journee.html`.

## Décisions déjà prises (par le porteur du projet)

1. **Une carte par journée calendaire** : « Lun. 4 janv. », sans « jusqu'au mar. 5 au matin ». En base, rien ne change : l'unité reste la soirée (dîner et nuit du soir J, petit-déjeuner du matin J+1 ; ADR-0006). C'est l'affichage qui regroupe autrement : la carte du jour D montre le petit-déjeuner du matin D (soirée D-1), puis le plat principal, les accompagnements, les présences au dîner et la nuit du soir D. Ordre des lignes : petit-déjeuner, plat principal, accompagnements, dîner, nuit. La ligne « Nuit » précise « jusqu'au matin ».
2. **Premier et dernier jour** : n'afficher que les postes qui existent ce jour-là (le premier jour n'a pas de petit-déjeuner, le dernier n'a que lui).
3. **Deux statuts** (R5) : « Complète » si tous les minimums des postes de la journée sont couverts, sinon « À compléter ». « Personne pour l'instant » disparaît.
4. **Plus de soirée fermée** : la notion est supprimée (R7, statut « Fermée », refus `CLOSED`, alerte, tests). Nouvelle migration qui supprime la colonne `evening.closed` (ne jamais modifier une migration fusionnée).
5. **Plus de bandeau « Dates d'exemple »** sur le planning. Le marqueur `season.sample` peut rester en base.
6. **Filtres** en haut du planning : « Toutes », « À compléter » et « Les miennes » (journées où le bénévole connecté a une inscription active ; filtre absent pour un visiteur). En GET avec un paramètre, sans JavaScript ; une valeur inconnue revient à « Toutes ».
7. **Désinscription depuis la carte** : sur une journée où le bénévole est inscrit, son pseudo est remplacé par « Vous » sur son poste, une ligne dit ce qu'il fait (« Vous passez la nuit »), et un bouton secondaire « Me désinscrire » mène à la page de confirmation existante (S4a), puis au POST. Une inscription par bouton : une carte peut en porter deux (petit-déjeuner du matin et dîner du soir), avec alors « Me désinscrire du petit-déjeuner », « … du dîner ». Sous le bouton, la date limite exacte (R6). Après la limite, le bouton est remplacé par le téléphone des coordinateurs, comme dans « Mes inscriptions ».

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md` : §5 (R1 à R7, en particulier R5, R6 et R7 tels que précisés en S4a), §6 (planning, inscription, désinscription)
- ADR [0006](../adr/0006-soirees-stockees-plutot-que-calculees.md) et [0008](../adr/0008-annulation-logique-et-anonymisation.md)
- Le module `planning` (service et vue publique), `inscription` (choix, confirmation, « Mes inscriptions », désinscription), et les tests associés
- `docs/design/planning-par-journee.html`

## Points à traiter dans le plan

- **Inscription depuis une journée** : « Je participe » propose les choix encore disponibles de la journée, qui viennent de deux soirées (petit-déjeuner : soirée D-1 ; le reste : soirée D). L'inscription reste rattachée à sa soirée ; R1 à R4 et R7 s'appliquent soirée par soirée, sans changement.
- **R6 reste inchangée** (l'activité est le soir J, petit-déjeuner du lendemain compris) : pour un petit-déjeuner affiché sur la carte du jour D, la limite tombe donc le jour D-2 à 23 h 59. Afficher la date limite calculée, jamais une formule générale, et signaler dans la PR si ce résultat paraît contre-intuitif.
- **Journée passée** : la carte du jour D disparaît à minuit le lendemain, heure de Paris. Le petit-déjeuner du jour D appartient à une soirée déjà passée ce jour-là : il s'affiche mais n'est plus proposé à l'inscription.
- **Libellés de date ailleurs** : « Mes inscriptions », les confirmations et les pages de désinscription affichent la date du jour de l'activité (« Petit-déjeuner du mar. 5 janv. », « Dîner du lun. 4 janv. »), pour rester cohérentes avec le planning.
- **Alerte « soirée devenue incomplète »** (ADR-0008) : elle reste calculée par soirée ; vérifier qu'elle tient sans la notion de soirée fermée.
- **Structure pour le design** : une carte par journée dans un `<details>` dont le `<summary>` porte la date et le statut, `open` si la journée est à compléter, fermé si elle est complète ; les postes en liste. Le style fin attend la session de design.

## Hors périmètre

Le style (couleurs, police, icônes, mise en page de la piste B : session de design suivante) ; le back-office (S4b) ; les e-mails (S5).

## Tests (écrits avant le code)

- **Domaine** : regroupement par journée (petit-déjeuner de la soirée D-1 sur la carte D, premier et dernier jour) ; statut d'une journée sur deux soirées ; plus de statut vide ni fermé.
- **Planning** : ordre des postes ; « Vous » à la place de son pseudo ; un bouton de désinscription par inscription active, et le téléphone après la limite ; `open` sur les journées à compléter seulement ; filtres, y compris une valeur inconnue et « Les miennes » sans connexion ; toujours aucun e-mail, téléphone de bénévole, nom ou UUID dans la page.
- **Inscription** : depuis une journée, le petit-déjeuner s'inscrit sur la soirée D-1 et le dîner sur la soirée D ; R2 reste par soirée.
- **Base** : la migration supprime `closed` sur une base de développement qui contient des inscriptions.
- **Accès** : le bouton de désinscription ne mène qu'à ses propres inscriptions ; un GET n'annule rien ; POST sans CSRF refusé.

## Documentation dans la même PR

- Cahier des charges : §5 (affichage par journée, données par soirée ; R5 à deux statuts ; R7 sans fermeture ; R6 et son effet sur le petit-déjeuner), §6 (planning, filtres, désinscription depuis la carte). Retirer « fermer une soirée » du back-office prévu en S4b.
- Un ADR (`0009`) pour « affichage par journée, données par soirée », avec au moins deux options (dont garder l'affichage par soirée), inconvénients compris.
- `CLAUDE.md` si une règle d'architecture change.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Parcours testé à la main en local à 360 px : filtres, inscription depuis une journée (petit-déjeuner et dîner), désinscription depuis la carte avant et après la limite (`APP_CLOCK_OFFSET`)
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, comment tester, points de sécurité, questions ouvertes

## Règles

- Données de test factices uniquement. Ne rien modifier hors du dépôt ; demander avant `docker compose down -v` ou `git push --force`.
- Si une décision ci-dessus se heurte à une règle du cahier des charges non prévue ici, le dire dans le plan plutôt que de trancher seul.
