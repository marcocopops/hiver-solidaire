# ADR-0002 — Rendu serveur (Thymeleaf + htmx) plutôt qu'une SPA

- **Statut** : proposé
- **Date** : 2026-10-24

## Contexte

L'appli affiche un planning, un formulaire d'inscription et un back-office pour 2 à 3 coordinateurs. Elle sert une vingtaine de bénévoles, surtout depuis un téléphone, et est développée et exploitée par une seule personne ([cahier des charges](../cahier-des-charges.md) §1, §8).

Le cahier des charges impose une CSP stricte sans script inline, des pages légères sans framework JavaScript lourd et un objectif WCAG 2.2 AA.

## Options envisagées

1. **Thymeleaf + htmx** : pages rendues par Spring, htmx servi depuis le dépôt pour les échanges partiels. Un seul projet, un seul déploiement, peu de JavaScript à auditer. En contrepartie, les interactions riches (mise à jour en direct, glisser-déposer) demandent plus de travail, et htmx se met à jour à la main.
2. **SPA (par exemple React) + API REST** : interface très réactive et API réutilisable par une future appli mobile. En contrepartie, deux projets, une chaîne de build JavaScript, des dépendances npm à surveiller dans la CI, une API à protéger en plus des pages, et un chargement initial plus lourd sur mobile.
3. **Thymeleaf seul, sans htmx** : encore moins de JavaScript, mais chaque action recharge la page entière, ce qui dégrade le parcours d'inscription visé en moins de 30 secondes.

## Décision

Option 1 : rendu serveur avec Thymeleaf, et htmx seulement pour des échanges partiels (par exemple le statut des places). Pas de SPA, pas de framework JavaScript.

Le parcours est court et fait de formulaires, que le rendu serveur traite bien. La surface d'attaque reste réduite : peu de JavaScript, pas de chaîne npm, et la CSP sans script inline est simple à tenir. Une API REST en moins, c'est un jeu d'endpoints en moins à autoriser et à tester.

## Conséquences

- Textes dans `messages.properties`, `th:text` pour tout contenu venant d'un utilisateur, JavaScript et CSS dans des fichiers statiques.
- htmx est copié dans le dépôt, sans CDN : sa version est à suivre à la main, hors Dependabot.
- Les fragments htmx sont des endpoints à protéger comme les autres (autorisation côté serveur, CSRF sur les POST).
- Si une appli mobile ou une API publique devient nécessaire, il faudra l'ajouter : l'option 2 n'est pas fermée, mais elle n'est pas préparée.
