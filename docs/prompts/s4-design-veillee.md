# S4 — Design « veillée » des pages bénévoles

- **Semaine** : S4, après `s4-planning-par-journee.md` (mergée) et avant le back-office S4b
- **Où** : Claude Code en local, sur le PC de développement, en mode plan ; le porteur du projet regarde l'appli sur `http://localhost:8080` pendant la session et donne ses retours
- **Modèle conseillé** : Sonnet (mise en forme ; Opus si le plan est hésitant)
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s4-design-veillee.md, propose un plan, puis attends ma validation. »

## Objectif

Appliquer la piste de design retenue, « veillée chaleureuse » (B v2), à toutes les pages côté bénévole, sans changer le comportement de l'appli. La maquette de référence est `docs/design/planning-par-journee.html` : ouvrir dans un navigateur à 390 px de large. Elle contient des styles inline et une police Google Fonts pour l'aperçu seulement : **ne jamais les recopier tels quels** (voir Contraintes).

## Le langage visuel

- **Fond** `#f6f3ee`, texte `#1f2a44`, texte secondaire `#475569`.
- **En-tête** : bandeau bleu nuit `#1f2a44`, coins bas arrondis (28 px), texte clair `#f8fafc` ; à gauche une petite lune ambre `#fbbf24` et « Hiver Solidaire », à droite un bouton contour « Mon espace » (ou « Me connecter » pour un visiteur), puis le titre de la page (« Planning de la saison »).
- **Police** : Atkinson Hyperlegible, 400 et 700, avec `system-ui` en repli.
- **Cartes** : fond blanc, coins de 20 px, ombre légère ; une carte par journée.
- **Statuts** : pastilles arrondies, texte gras ; « Complète » fond `#dcfce7` texte `#14532d`, « À compléter » fond `#ffedd5` texte `#7c2d12`.
- **Postes** : une ligne par poste, avec une icône au trait de 20 px, le libellé en texte secondaire, puis les pseudos en pastilles ambre clair (fond `#fef3c7`, texte `#78350f`) ; une place libre est une pastille en pointillés « À pourvoir » (bordure et texte `#9a4508`) ; votre propre place est une pastille ambre pleine « Vous » (fond `#b45309`, texte blanc). Libellés alignés en colonne ; les pastilles passent à la ligne proprement quand il n'y a pas la place.
- **Boutons** : action principale en pilule ambre pleine `#b45309`, texte blanc gras, au moins 48 px de haut (« Je participe ») ; action secondaire en pilule à contour `#64748b` (« Me désinscrire »).
- **Filtres** : pilules de 44 px ; le filtre actif est plein bleu nuit, les autres à contour, avec `aria-current`.
- **Carte où l'on participe** : contour ambre de 2 px, séparateur fin avant le bloc « Vous passez la nuit », le bouton secondaire et la date limite en petit texte.

## Livrables

1. **Planning** : la maquette appliquée à la structure livrée par la session précédente. Cartes dépliables avec `<details>`/`<summary>` (pas de JavaScript) : chevron qui pivote selon l'état, journée à compléter ouverte au chargement, journée complète fermée, `summary` accessible au clavier avec un focus visible.
2. **Toutes les autres pages bénévoles** dans le même langage : accueil, connexion (formulaire, lien envoyé, trop de demandes, confirmation), premier profil, choix et confirmation d'inscription, inscription confirmée, « Mes inscriptions », « Mon compte », désinscription, suppression du compte, notice provisoire, page d'erreur. Formulaires : champs de 48 px, libellés au-dessus, messages d'erreur liés au champ (`aria-describedby`), focus visible.
3. **CSS** organisé avec des variables (couleurs, rayons, espacements) dans `static/css`, réutilisable par le back-office en S4b.
4. **Police** servie depuis le dépôt (`static/fonts`, woff2, 400 et 700) avec son fichier de licence OFL ; justifier l'ajout dans la PR. Pas de Google Fonts, pas de CDN.
5. **Icônes** en SVG au trait, dans les gabarits (ou un fichier SVG statique), avec `aria-hidden="true"` : pas de bibliothèque d'icônes, pas d'emoji.
6. **Captures** : pour la PR, des captures à 360 px du planning (visiteur et bénévole connecté), d'un formulaire et de « Mon compte », si l'outillage local le permet ; sinon, le dire.

## Contraintes

- **CSP** : aucun attribut `style="…"`, aucun `<style>` ni `<script>` inline dans les gabarits ; tout passe par des classes et des fichiers statiques.
- **Accessibilité** (§8, WCAG 2.2 AA) : contraste du texte au moins 4,5:1 (3:1 au-dessus de 24 px) ; zones tactiles d'au moins 44 px ; le statut se lit dans le texte, pas seulement dans la couleur ; `prefers-reduced-motion` respecté si une transition est ajoutée.
- **Mobile d'abord**, sans défilement horizontal à 360 px ; une colonne centrée et une largeur maximale sur grand écran.
- **Aucun changement de comportement** : mêmes routes, mêmes formulaires, mêmes règles. Textes de l'interface dans `messages.properties` ; s'il faut en modifier un (par exemple un libellé de bouton), le lister dans la PR.
- **Tests** : si un test vérifie une classe CSS ou un texte qui change, le test suit le changement, sans jamais vérifier moins. Aucun test désactivé.
- Les e-mails et le back-office ne sont pas concernés.

## Façon de travailler

Le porteur du projet a l'appli ouverte sur `localhost` : après chaque étape visible (en-tête et planning d'abord, puis les formulaires, puis le reste), dire quoi recharger et attendre son retour avant de passer à la suite. Ne rien modifier hors du dépôt ; demander avant `docker compose down -v` ou `git push --force`.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte (scan de l'image compris)
- [ ] Toutes les pages bénévoles relues à 360 px et sur ordinateur par le porteur du projet
- [ ] Aucun style ni script inline (vérifiable par une recherche dans `templates/`), aucune erreur de CSP dans la console
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, captures, comment tester, police ajoutée et sa licence, textes modifiés, questions ouvertes
