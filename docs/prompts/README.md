# Prompts des sessions Claude Code

Chaque fichier décrit une session de travail confiée à Claude Code : objectif, livrables et critères de fin. Ensemble, ils montrent comment le projet est construit avec Claude.

| Fichier | Semaine | Session |
|---|---|---|
| [s1-squelette.md](s1-squelette.md) | S1 | Squelette déployable : Spring Boot, CI, image Docker, compose de production |
| [s1-adr.md](s1-adr.md) | S1 | ADR 0002 à 0005 et modèle de menaces, en parallèle |
| [s2-modele-et-planning-public.md](s2-modele-et-planning-public.md) | S2 | Modèle de données (règles R1-R7) et planning public en lecture seule |
| [s3-connexion-et-inscription.md](s3-connexion-et-inscription.md) | S3 | Connexion par lien magique, inscription avec règles de capacité, Mailpit en développement |
| [s4a-desinscription-et-mon-compte.md](s4a-desinscription-et-mon-compte.md) | S4 | Désinscription (R6, annulation logique) et « Mon compte » (modification, suppression et anonymisation) |
| [s4-planning-par-journee.md](s4-planning-par-journee.md) | S4 | Planning par journée, deux statuts, filtres, désinscription depuis la carte, suppression des soirées fermées |
| [s4-design-veillee.md](s4-design-veillee.md) | S4 | Design « veillée » de toutes les pages bénévoles, d'après `docs/design/planning-par-journee.html` |
| [s4b-back-office.md](s4b-back-office.md) | S4 | Back-office des coordinateurs, en session cloud sans surveillance (décisions par défaut, ordre de priorité) |
| [lot1-donnees-de-test.md](lot1-donnees-de-test.md) | S5 | Corrections, lot 1 : script de données de test pour la base de développement |
| [lot2-saison-et-contact.md](lot2-saison-et-contact.md) | S5 | Corrections, lot 2 : saison sans nom, adresse ni consignes ; contact des coordinateurs (téléphone ou e-mail) |
| [lot3-plusieurs-postes-et-recuperation.md](lot3-plusieurs-postes-et-recuperation.md) | S5 | Corrections, lot 3 : plusieurs postes par personne (R2), récupération liée aux préparations chez soi (R4) |
| [lot4-navigation-et-accueil.md](lot4-navigation-et-accueil.md) | S5 | Corrections, lot 4 : menu burger, en-tête prune des pages coordinateur, page d'accueil |
| [lot5-exports.md](lot5-exports.md) | S5 | Corrections, lot 5 : page Exports avec tableau mis à jour (htmx) et e-mails |

Pour lancer une session : ouvrir claude.ai/code sur ce dépôt, ou lancer `claude` en local dans le clone (selon le champ « Où » du fichier), choisir le mode plan, puis envoyer le message de lancement indiqué en tête du fichier.
