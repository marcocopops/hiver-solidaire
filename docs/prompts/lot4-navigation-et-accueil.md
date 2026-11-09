# Lot 4 — Menu, en-tête, identité des pages coordinateur et page d'accueil

- **Quand** : après les lots 2 et 3 (mergés), pour ne pas croiser leurs gabarits
- **Où** : Claude Code en local, en mode plan ; effort par défaut ; le porteur du projet regarde l'appli en direct sur `localhost` et valide étape par étape
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot4-navigation-et-accueil.md, propose un plan, puis attends ma validation. »

## Objectif

Une navigation unique et simple pour tous, un compte coordinateur aussi proche que possible d'un compte bénévole, des pages coordinateur reconnaissables au premier coup d'œil, et une vraie page d'accueil.

## Décisions du porteur du projet

1. **Supprimer les liens orange** en haut des pages (navigation actuelle), sur toutes les pages. Le planning garde ses filtres.
2. **Menu « burger »** à la place du bouton « Mon espace », dans l'en-tête de toutes les pages, sans JavaScript (élément `<details>`, accessible au clavier, `aria-current="page"` et une couleur particulière sur la page en cours). Ordre :
   - visiteur : Planning, Me connecter ;
   - bénévole : Planning, Mes inscriptions, Mon compte, Se déconnecter (POST) ;
   - coordinateur : **le même ordre que le bénévole**, avec ses entrées propres en couleur prune juste avant la déconnexion : Planning, Mes inscriptions, Mon compte, Tableau de bord, Bénévoles, Saison, Exports, Se déconnecter.
3. **Identité des pages coordinateur** (proposition A retenue) : sur toutes les pages `/admin/**`, l'en-tête passe du bleu nuit à un **prune foncé**, avec la mention « Accès coordinateur » sous le nom de l'appli. Le même prune marque les entrées coordinateur du menu, sur toutes les pages. Contrastes WCAG AA vérifiés (texte clair sur prune, prune sur fond clair).
4. **Alignement de l'en-tête** : sur les pages admin, le contenu de l'en-tête n'a pas la même marge que sur les pages publiques. Corriger pour qu'il soit aligné au pixel près sur les deux (largeur de page plus grande côté admin comprise).
5. **Page d'accueil** (lien « Hiver Solidaire » en haut à gauche) : remplacer « Inscriptions à partir du 14 décembre » par une présentation reprise du formulaire de l'an dernier. **Le porteur du projet collera le texte exact dans la session.** À adapter :
   - aucune donnée réelle dans le dépôt : ni nom ni e-mail de personne, ni nom réel d'association s'il y figure (`CLAUDE.md`) ; le contact affiché est le « contact des coordinateurs » de la saison (lot 2) ;
   - les éléments datés de 2022 (dates, pass sanitaire) sont retirés ; les dates viennent de la saison en base ;
   - aucune information sur les personnes accueillies au-delà d'une formulation générale ;
   - les trois façons de participer (petit-déjeuner, dîner, nuit) présentées simplement, avec un bouton vers le planning ;
   - les textes dans `messages.properties`.

## Contraintes

- CSP : aucun style ni script inline ; pas de CDN. `TemplatesHaveNoInlineCodeTest` reste vert et exigeant.
- Accessibilité WCAG 2.2 AA : menu utilisable au clavier, cible de 44 px au moins, focus visible, état ouvert/fermé annoncé.
- Aucun changement de comportement hors navigation et page d'accueil. Les tests qui vérifiaient les anciens liens suivent le changement sans vérifier moins (y compris `AdminAccessTest`).

## Tests

- Le menu contient les bonnes entrées, dans le bon ordre, pour un visiteur, un bénévole et un coordinateur ; aucune entrée coordinateur pour un bénévole.
- `aria-current="page"` sur l'entrée de la page en cours.
- Les pages `/admin/**` portent la classe d'en-tête coordinateur et la mention « Accès coordinateur » ; les pages publiques ne la portent pas, même pour un coordinateur connecté.
- La page d'accueil n'affiche ni e-mail ni téléphone en dur.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Toutes les pages relues par le porteur du projet à 360 px et sur ordinateur, en visiteur, bénévole et coordinateur
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte avec captures, textes modifiés listés
