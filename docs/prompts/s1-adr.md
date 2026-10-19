# S1 bis — ADR 0002 à 0005 et modèle de menaces

- **Semaine** : S1, en parallèle de `s1-squelette.md`
- **Où** : seconde session cloud Claude Code, en mode plan
- **Modèle conseillé** : Sonnet
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s1-adr.md, propose un plan, puis attends ma validation. »

## Objectif

Rédiger les quatre ADR restants et une première version du modèle de menaces. Cette session ne touche qu'à `docs/` : aucun code.

## Livrables

1. **ADR**, au format de `docs/adr/template.md`, en français, une page au plus chacun, en s'appuyant sur le cahier des charges :
   - `0002` : rendu serveur (Thymeleaf + htmx) plutôt qu'une SPA ;
   - `0003` : connexion par lien magique (jeton à usage unique de Spring Security, validé en POST) plutôt que par mot de passe ;
   - `0004` : homelab + Cloudflare Tunnel plutôt qu'un VPS ou un PaaS ;
   - `0005` : PostgreSQL plutôt que SQLite.

   Mettre à jour l'index `docs/adr/README.md`.
2. **Modèle de menaces** `docs/securite/modele-de-menaces.md` (STRIDE allégé) :
   - schéma des flux et des frontières de confiance (Mermaid) ;
   - actifs : données des bénévoles, sessions des coordinateurs, disponibilité pendant la saison, réputation de l'association ;
   - menaces par catégorie STRIDE, avec probabilité, impact, mesure prévue (renvoi au §8 du cahier des charges) et statut ;
   - risques résiduels acceptés.

   À couvrir notamment : lien magique intercepté ou consommé par le filtre d'une messagerie, inscriptions malveillantes avec un accès libre, énumération d'adresses e-mail, compte coordinateur compromis, homelab hors service en pleine saison, compromission de la chaîne CI.

## Règles

- Chaque ADR expose au moins deux options réelles, inconvénients compris : pas de plaidoyer.
- Toute affirmation sur un produit ou une offre (gratuité, limites) cite sa source.
- Si un ADR révèle une incohérence avec le cahier des charges, la signaler dans la PR sans modifier le cahier des charges.

## Critères de fin

- [ ] 4 ADR, index à jour, modèle de menaces, liens relatifs valides
- [ ] PR ouverte, séparée de celle du squelette
