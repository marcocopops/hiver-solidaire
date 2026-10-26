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

Pour lancer une session : ouvrir claude.ai/code sur ce dépôt, ou lancer `claude` en local dans le clone (selon le champ « Où » du fichier), choisir le mode plan, puis envoyer le message de lancement indiqué en tête du fichier.
