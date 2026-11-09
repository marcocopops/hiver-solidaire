# Lot 5 — Page « Exports » du back-office

- **Quand** : après le lot 4 (mergé)
- **Où** : Claude Code en local, en mode plan ; effort par défaut
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot5-exports.md, propose un plan, puis attends ma validation. »

## Objectif

Une page d'exports directe : choisir une période, voir le tableau, l'imprimer ou l'exporter.

## Décisions du porteur du projet

1. **Plus de texte explicatif** sur la page.
2. **En haut** : deux champs de date, « Du » et « Au », par défaut les 7 prochains jours ; deux boutons, « Ouvrir la page imprimable » et « Exporter au format CSV ».
3. **En dessous** : le tableau qu'affiche la page imprimable, pour la période choisie, **mis à jour dès qu'une date change**.
4. **Le tableau contient aussi les adresses e-mail** (en plus de ce qu'il montre aujourd'hui) ; la page imprimable et le CSV aussi.

## Mise en œuvre attendue

- Mise à jour partielle avec **htmx** (prévu par `CLAUDE.md`), servi depuis le dépôt (`static/js`, version épinglée, fichier de licence), sans CDN : une requête GET renvoie le fragment du tableau. Justifier l'ajout dans la PR.
- Sans JavaScript, la page reste utilisable : un bouton « Afficher » soumet les dates en GET.
- Le tableau de la page et celui de la page imprimable viennent du **même fragment Thymeleaf**.
- Le CSV reste un POST protégé par CSRF, avec l'échappement des formules existant.
- La période est bornée (pas d'export de toute la saison d'un coup si la page devient lente : proposer une limite dans le plan).

## Contraintes

- CSP : htmx fonctionne sans `eval` ni style inline (le configurer si besoin, par exemple `htmx.config.includeIndicatorStyles = false` depuis un fichier statique) ; ajuster la CSP seulement si c'est indispensable, et le justifier.
- Les e-mails n'apparaissent que pour un coordinateur. Aucune donnée personnelle dans les logs.

## Tests

- Accès : la page, le fragment, l'impression et le CSV refusés à un bénévole et à un visiteur (`AdminAccessTest`).
- Période par défaut : les 7 prochains jours ; dates invalides ou inversées : message clair, pas d'erreur 500.
- Le fragment, la page imprimable et le CSV contiennent les e-mails ; le CSV garde l'échappement des formules.
- Aucun style ni script inline (`TemplatesHaveNoInlineCodeTest`).

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Testé à la main avec les données du lot 1 : changement de dates, impression, CSV ouvert dans un tableur
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte : résumé, htmx ajouté et sa justification, comment tester
