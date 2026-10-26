# ADR-0009 — Affichage par journée, données par soirée

- **Statut** : proposé
- **Date** : 2026-10-26

## Contexte

L'unité du planning est la soirée : dîner et nuit du soir J, petit-déjeuner du matin J+1 ([ADR-0006](0006-soirees-stockees-plutot-que-calculees.md)). Jusqu'en S4a, le planning affichait une carte par soirée, « Lun. 4 → mar. 5 janv. ». Le porteur du projet a décidé d'afficher une carte par journée calendaire, « Lun. 4 janv. », avant la session de design (`docs/prompts/s4-planning-par-journee.md`, maquette `docs/design/planning-par-journee.html`) : un libellé à une seule date est plus court sur un téléphone, et chaque repas apparaît le jour où il a lieu.

Ce qui ne bouge pas : R1 (verrou sur la ligne de la soirée), R2 (une inscription par bénévole et par soirée), R3, R4, R6 (limite la veille de la soirée), l'alerte « soirée devenue incomplète » et le rattachement des inscriptions à une soirée. Il faut décider si la journée devient une donnée ou reste un regroupement à l'affichage.

La même décision supprime la soirée fermée (R7) : avec des cartes par journée, une soirée fermée laisserait une demi-carte fermée, et les coordinateurs n'en ont pas l'usage. La colonne `evening.closed` disparaît (migration V9) ; la partie « fermeture » d'ADR-0006 est donc caduque.

## Options envisagées

1. **Garder l'affichage par soirée** : une carte « Lun. 4 → mar. 5 janv. » par soirée.
   - Avantages : aucun changement ; la carte correspond exactement à l'unité des règles (statut, inscription, limite de désinscription).
   - Inconvénients : le libellé à deux dates est long et ambigu sur mobile ; le petit-déjeuner du mardi apparaît sous le lundi ; la maquette retenue ne peut pas être réalisée.
2. **Afficher par journée, garder les données par soirée** : la carte du jour D montre le petit-déjeuner du matin D (soirée D-1), puis le dîner et la nuit du soir D (soirée D). Le domaine ajoute un objet `Day`, calculé à partir de deux soirées, sans table.
   - Avantages : la base, le verrou de R1 et les règles R1 à R6 restent inchangés et testés soirée par soirée ; le regroupement est en Java pur, testé unitairement ; aucune migration de données.
   - Inconvénients :
     - une carte porte deux soirées : son statut (R5) et « Je participe » consultent les deux, et un bénévole peut avoir deux inscriptions sur une même carte (petit-déjeuner et dîner), donc deux boutons de désinscription ;
     - R6 s'applique à la soirée : la limite pour le petit-déjeuner du jour D tombe le jour D-2 à 23 h 59, ce qui peut surprendre. L'appli affiche toujours la date limite calculée, jamais une formule ;
     - deux paramètres coexistent dans les URL : `jour` pour l'inscription (la carte), `soiree` pour la désinscription (l'inscription, unique par soirée grâce à R2) ;
     - le premier jour de la saison n'a pas de petit-déjeuner, et il existe une journée de plus que de soirées (le lendemain de la dernière, petit-déjeuner seul).
3. **Stocker des journées** : une ligne par jour, les inscriptions rattachées à une journée et à un repas.
   - Avantages : la carte correspond à une ligne ; plus de décalage d'un jour entre l'affichage et les données.
   - Inconvénients : refonte du modèle et des contraintes R2 et R3 ; le verrou de R1 devrait couvrir deux journées pour une nuit (dîner et petit-déjeuner du lendemain) ; R6 et l'alerte à réécrire ; migration de données en pleine préparation de l'ouverture du 14 décembre.

## Décision

Option 2 : la journée est un regroupement d'affichage, les données restent par soirée. Elle donne l'affichage voulu sans toucher aux règles déjà testées ni au modèle, à sept semaines de l'ouverture.

## Conséquences

- `Day` (domaine) fixe la règle de regroupement, l'ordre des postes (petit-déjeuner, plat principal, accompagnements, dîner, nuit) et ce que « Je participe » propose, en déléguant chaque décision à `Evening.decide` de la soirée concernée.
- R5 n'a plus que deux statuts, « Complète » et « À compléter », évalués sur les postes de la journée.
- Les libellés de date de toute l'appli suivent le jour de l'activité : « Petit-déjeuner du mar. 5 janv. », « Dîner du lun. 4 janv. ».
- À surveiller : l'effet de R6 sur le petit-déjeuner (limite à D-2) auprès des bénévoles et des coordinateurs. Si elle gêne, c'est R6 qu'il faudra changer (limite propre au petit-déjeuner), pas le modèle.
- Le back-office (S4b) raisonne toujours par soirée (fiche soirée, inscriptions par téléphone) ; il pourra afficher les journées avec le même `Day`.
