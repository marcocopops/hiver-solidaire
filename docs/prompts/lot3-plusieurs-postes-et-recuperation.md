# Lot 3 — Plusieurs postes pour une même personne, récupération liée aux préparations chez soi

- **Quand** : après le lot 2 (mergé)
- **Où** : Claude Code en local, en mode plan ; **Opus, effort `high` ou `xhigh`**
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot3-plusieurs-postes-et-recuperation.md, propose un plan, puis attends ma validation. »

## Objectif

Assouplir R2 pour qu'une même personne puisse prendre plusieurs postes d'une soirée, et n'ouvrir « Récupérer le dîner et le partager » que lorsqu'il y a un repas préparé chez soi à récupérer.

## Décisions du porteur du projet

1. **R2 assouplie** : une même personne peut, sur la même soirée, cumuler les postes **distincts** : passer la nuit, préparer le plat principal, préparer les accompagnements. Elle ne peut pas prendre deux fois le même poste (pas deux plats principaux, pas deux nuits). Une présence « récupérer et partager » cumulée avec un autre poste du même dîner est à examiner dans le plan (proposer une règle, ne pas trancher seul).
2. **Comptage des présences inchangé** : chaque inscription « préparer et partager » compte une présence au dîner. Une personne qui prépare et partage le plat **et** les accompagnements compte donc pour deux présences : en pratique, c'est un couple qui vient. Les coordinateurs vérifient auprès d'eux. Le cahier des charges doit le dire.
3. **R4 reste telle quelle, avec une contrainte en plus** : « Récupérer le dîner et le partager » n'est proposé (et accepté en écriture) que s'il existe une préparation chez soi qui n'a pas encore sa récupération. Le nombre de récupérations d'une soirée ne dépasse jamais le nombre de préparations chez soi : une préparation chez soi ouvre une récupération ; une deuxième préparation chez soi en ouvre une deuxième. Les présences au dîner gardent leur maximum (R1).
4. **Désinscription d'une préparation chez soi déjà récupérée** : proposer dans le plan ce qui se passe (la récupération reste, mais devient « sans repas » : alerte aux coordinateurs ? refus ?). Ne pas trancher seul.
5. L'exception à R2 « sur décision d'un coordinateur » (§11 question 1) devient sans objet pour le cumul de postes ; le dire dans le cahier des charges.

## À lire avant de planifier

- `CLAUDE.md` ; `docs/cahier-des-charges.md` §5 (R1 à R5), §6, §11 question 1
- ADR 0006, 0008, 0009 ; le domaine (`planning`), `inscription.RegistrationService` (verrou de la soirée), le parcours de choix (« Je participe ») et le déplacement par un coordinateur
- Migrations V2, V4 et V8 : `registration_one_dish_per_evening` et `registration_one_active_per_volunteer_and_evening`

## Livrables

- **Domaine** (Java pur) : nouvelle règle d'unicité par poste et par bénévole ; disponibilité de la récupération selon les préparations chez soi ; R1, R3, R5 inchangées.
- **Nouvelle migration** : remplacer l'index unique « une inscription active par bénévole et par soirée » par une garantie par poste (index partiel ou contrainte adaptée au modèle) ; ne jamais modifier V4 ni V8.
- **Parcours d'inscription** : les choix proposés tiennent compte des postes déjà pris par le bénévole et de la disponibilité de la récupération ; même contrôle en écriture, sous le verrou de la soirée (R1).
- **Back-office** : inscription par téléphone et déplacement appliquent les mêmes règles ; le tableau de bord continue de signaler une préparation chez soi sans récupération.
- **Documentation** : cahier des charges (§5 R2 et R4, §6, §11), un ADR pour le passage de « une inscription par soirée » à « un poste par bénévole et par soirée », avec au moins deux options.

## Tests (avant le code)

- **Domaine** : cumul plat + accompagnements + nuit accepté ; même poste deux fois refusé ; présences comptées par inscription « préparer et partager » ; récupération indisponible sans préparation chez soi ; une seule récupération pour une préparation chez soi, une deuxième après une deuxième préparation ; R1 respectée.
- **Concurrence** : deux récupérations simultanées pour une seule préparation chez soi : une seule passe.
- **Parcours** : l'option « Récupérer » n'apparaît pas tant qu'aucune préparation chez soi n'est inscrite ; elle apparaît après ; elle disparaît une fois la récupération prise.
- **Back-office** : inscription par téléphone et déplacement soumis aux mêmes règles.
- La migration passe sur une base de développement remplie par `scripts/seed-dev.sh` (lot 1).

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Testé à la main avec les données du lot 1 : cumul de postes, récupération, désinscriptions
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte : résumé, règles avant/après, comment tester, questions ouvertes (dont les points 1 et 4 des décisions)
