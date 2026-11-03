# S4b — Back-office des coordinateurs

- **Semaine** : S4 (après le planning par journée, #18)
- **Où** : session cloud Claude Code, **sans surveillance** (lancée le soir, relue le lendemain), en mode **Auto**
- **Modèle conseillé** : Opus (autorisations, données personnelles et actions sur les inscriptions : les erreurs y sont coûteuses)
- **Message de lancement** : voir en bas de ce fichier

## Objectif

Donner aux 2 ou 3 coordinateurs l'outil pour tenir le planning pendant la saison : voir les journées à compléter, agir sur les inscriptions (y compris pour un bénévole sans e-mail, par téléphone), gérer les bénévoles et la saison, tracer leurs actions. Cette session remplace la saison d'exemple par une saison saisie dans l'appli.

## Travail sans surveillance

Personne ne pourra répondre pendant la session. Cela remplace l'étape 2 de « Façon de travailler » de `CLAUDE.md` (attendre la validation du plan), et rien d'autre :

1. Explorer, puis écrire le plan dans `docs/plans/s4b-back-office.md` et le committer en premier sur la branche. Il sert de validation a posteriori.
2. Suivre les **décisions par défaut** ci-dessous. Pour toute autre question, prendre l'option la plus prudente pour les données et la plus simple à défaire, et la noter dans la section « Décisions prises sans moi » de la PR.
3. Avancer dans l'**ordre de priorité**. Si le temps ou le contexte manque, s'arrêter à la fin d'une étape complète (tests verts), pousser, et lister ce qui reste dans la PR.
4. Ne jamais faire ce qui ne se défait pas : pas de `git push --force`, pas de réécriture d'historique, pas de modification d'une migration fusionnée, rien en dehors du dépôt.
5. Ouvrir la PR en **brouillon** si un critère de fin n'est pas rempli, prête à relire sinon.

## Décisions par défaut (à respecter, le porteur du projet peut les revoir dans la PR)

- **Désigner les coordinateurs** : une variable d'environnement `APP_COORDINATOR_EMAILS` (liste d'adresses séparées par des virgules, valeur factice `@example.org` dans `.env.example`). À la connexion par lien magique, une adresse de la liste reçoit le rôle coordinateur en plus du rôle bénévole. Aucune adresse réelle dans le dépôt.
- **Sessions coordinateur de 8 h** (§8), celles des bénévoles restent à 30 jours (ADR-0007). Au-delà de 8 h, reconnexion. Un coordinateur reste aussi un bénévole : il peut s'inscrire comme les autres.
- **Cloudflare Access sur `/admin/*`** se configure chez Cloudflare, pas dans l'appli : le documenter dans `deploy/README.md`. L'appli ne fait pas confiance à Access, elle vérifie elle-même le rôle sur chaque endpoint (défense en profondeur).
- **Exports** : CSV, et une page « imprimable » (CSS d'impression) que le navigateur enregistre en PDF. Pas de bibliothèque PDF, donc pas de nouvelle dépendance. Le noter dans le cahier des charges.
- **Bénévole sans e-mail** (inscrit par téléphone) : nouvelle migration qui rend `volunteer.email` facultatif, unicité conservée sur les adresses présentes. Un tel bénévole a nom, prénom, pseudo et téléphone, ne peut pas se connecter, et apparaît sur le planning par son pseudo comme les autres.
- **Une saison active à la fois.** Les réglages de la saison remplacent la saison d'exemple : la saison d'exemple ne peut être supprimée que si elle n'a aucune inscription active ; sinon, l'écran l'indique.
- **Téléphone des coordinateurs** : il passe dans les réglages de la saison ; `APP_COORDINATORS_PHONE` reste en repli tant qu'aucun numéro n'est saisi.

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md` : §2, §4, §5 (R1 à R7, et R2 « sauf décision d'un coordinateur »), §6 (back-office), §7, §8, §11 (questions 1, 2 et 8)
- Tous les ADR, en particulier 0006 à 0009
- `docs/securite/modele-de-menaces.md` (compte coordinateur compromis)
- Les modules `auth`, `planning`, `inscription`, `benevole`, et la table `coordinator_alert`

## Livrables, dans l'ordre de priorité

1. **Rôle et accès** : rôle coordinateur, sessions de 8 h, tout `/admin/**` réservé au rôle, page d'accueil du back-office. **Journal d'audit** posé dès cette étape (table, service), pour que chaque action des étapes suivantes l'alimente : qui (identifiant technique du coordinateur), quand, quelle action, sur quoi (identifiants), sans nom, e-mail ni téléphone dans le journal.
2. **Tableau de bord** : les journées incomplètes des 14 prochains jours en tête (avec ce qui manque), les alertes enregistrées non traitées (`coordinator_alert`), les préparations chez soi sans récupération (R4), les nouveaux bénévoles à signaler (§2).
3. **Fiche journée** (et sa soirée) : les inscrits avec nom, prénom, pseudo, téléphone et e-mail. Actions, chacune avec page de confirmation puis POST, et inscrite au journal : annuler une inscription (même après la limite de R6), déplacer une inscription vers une autre soirée (mêmes contrôles R1 à R4 et R7, sous verrou), inscrire un bénévole existant ou un nouveau bénévole sans e-mail.
4. **Bénévoles** : liste avec recherche, nouveaux bénévoles signalés, fiche avec ses inscriptions, correction du nom, du prénom et du pseudo (pseudo usurpé, §11 question 8), suppression sur demande (réutiliser l'anonymisation d'ADR-0008).
5. **Saison** : création et modification (première et dernière soirée, ouverture des inscriptions, besoins min/max par poste, horaires affichés, adresse du lieu, consignes, téléphone des coordinateurs), génération des soirées (ADR-0006), remplacement de la saison d'exemple. Modifier les dates ne doit jamais supprimer une soirée qui a des inscriptions actives : refuser, avec un message clair.
6. **Exports** : CSV des inscriptions d'une période, page imprimable du planning des 7 prochains jours (sert au plan B, §8).
7. **Exception à R2** décidée par un coordinateur (§11 question 1) : permettre deux inscriptions d'un même bénévole sur une soirée quand un coordinateur le décide, par une nouvelle migration sur l'index d'unicité. À faire seulement si les étapes précédentes sont finies ; sinon, la lister comme restante.

Le back-office reprend le CSS et les gabarits existants, sobrement : pas de nouveau design. Ses pages sont pensées d'abord pour l'ordinateur, mais restent utilisables sur téléphone.

## Tests (écrits avant le code)

- **Accès** : chaque endpoint `/admin/**`, GET et POST, refusé à un visiteur et à un bénévole non coordinateur ; un bénévole dont l'adresse sort de `APP_COORDINATOR_EMAILS` perd le rôle à sa prochaine connexion ; session coordinateur refusée après 8 h (horloge simulée).
- **CSRF** : tout POST du back-office sans jeton est refusé ; aucun GET ne modifie rien.
- **Règles** : déplacement soumis à R1 à R4 et R7 ; annulation après R6 possible pour un coordinateur et refusée au bénévole ; concurrence entre une action coordinateur et une inscription bénévole sur la dernière place.
- **Saison** : génération des soirées ; refus d'un changement de dates qui supprimerait des soirées avec inscriptions ; suppression de la saison d'exemple seulement sans inscription active.
- **Bénévole sans e-mail** : créé, inscrit, visible par son pseudo, sans possibilité de connexion.
- **Audit** : chaque action écrit une ligne, sans donnée personnelle.
- **Logs et exports** : aucun nom, e-mail ni téléphone dans les logs ; le CSV ne contient que ce que voit un coordinateur, avec échappement des cellules qui commencent par `=`, `+`, `-` ou `@` (injection de formule dans un tableur).
- **Page publique** : `/planning` ne montre toujours que les pseudos.

## Documentation dans la même PR

- Cahier des charges : §6 (back-office tel que livré), §8 (sessions de 8 h, Access), §11 (questions 1, 2 et 8 mises à jour), et les « décisions par défaut » ci-dessus.
- Un ADR pour la désignation des coordinateurs par variable d'environnement, avec au moins deux options.
- Modèle de menaces : compte coordinateur compromis, export CSV.
- `deploy/README.md` : `APP_COORDINATOR_EMAILS`, application Cloudflare Access sur `/admin/*`.
- `CLAUDE.md` et `.env.example` pour les nouvelles variables.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte (tests, revue des dépendances, scan de l'image)
- [ ] Plan dans `docs/plans/s4b-back-office.md`, et section « Décisions prises sans moi » dans la PR
- [ ] Aucune donnée réelle dans le diff ; aucune donnée personnelle dans les logs ni le journal d'audit
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte (brouillon si un critère manque) : résumé, comment tester en local (avec une adresse de coordinateur factice), points de sécurité, décisions prises sans moi, ce qui reste

## Message de lancement

À coller dans une nouvelle session cloud, mode **Auto** :

> Lis CLAUDE.md et docs/prompts/s4b-back-office.md. Tu travailles seul cette nuit et personne ne pourra répondre : suis la section « Travail sans surveillance » du prompt, écris ton plan dans docs/plans/s4b-back-office.md et commite-le, puis implémente sans attendre de validation, dans l'ordre de priorité. Termine par une PR, en brouillon si un critère de fin manque.
