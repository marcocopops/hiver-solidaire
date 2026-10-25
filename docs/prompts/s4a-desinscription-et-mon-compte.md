# S4a — Désinscription et « Mon compte »

- **Semaine** : S4, du 9 au 15 novembre 2026 (première des deux sessions de S4 ; le back-office suit en S4b)
- **Où** : Claude Code en local, sur le PC de développement, en mode plan
- **Modèle conseillé** : Opus (annulation, anonymisation et contraintes en base : les erreurs y sont coûteuses)
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s4a-desinscription-et-mon-compte.md, propose un plan, puis attends ma validation. »

## Objectif

Permettre au bénévole de gérer seul ce qui le concerne : se désinscrire d'une soirée jusqu'à la veille, et consulter, modifier ou supprimer ses données dans « Mon compte ». À la fin de la session, une place libérée par une désinscription est immédiatement reprenable, et un compte supprimé ne laisse plus aucune donnée personnelle.

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md` : §2 (désinscription, conservation), §5 (R1, R2, R3, R5, R6), §6 (« Se désinscrire », « Mon compte »), §7 (durées, consentement)
- Les ADR, en particulier [0006](../adr/0006-soirees-stockees-plutot-que-calculees.md) et [0007](../adr/0007-session-jdbc-plutot-que-se-souvenir-de-moi.md)
- `docs/securite/modele-de-menaces.md`
- Le code des modules `inscription` et `benevole`, et les migrations V2, V4 et V7 (index et contraintes uniques)

## Hors périmètre (sessions suivantes)

Back-office et rôle coordinateur, annulation ou déplacement par un coordinateur, Cloudflare Access (S4b) ; tous les e-mails, dont la confirmation de désinscription, le lien de désinscription dans l'e-mail et les alertes aux coordinateurs ; purge automatique en fin de saison (S5).

## Livrables

1. **Annulation logique des inscriptions** :
   - une désinscription marque l'inscription comme annulée (par exemple `cancelled_at`) au lieu de la supprimer ;
   - nouvelle migration : les garanties d'unicité ne portent plus que sur les inscriptions actives. Cela concerne l'index R3 `registration_one_dish_per_evening` (V2) et la contrainte R2 `registration_one_per_volunteer_and_evening` (V4), à remplacer par un index unique partiel (une contrainte `UNIQUE` ne peut pas être partielle) ;
   - le calcul des places (R1), le statut (R5), les pseudos affichés sur `/planning` et « Mes inscriptions » ignorent les inscriptions annulées.
2. **Désinscription** (§6, R6), depuis « Mes inscriptions » :
   - possible jusqu'à la veille de l'activité à 23 h 59, heure de Paris : préciser dans le plan quelle date compte comme « l'activité » (le soir J, ou le matin J+1 pour le petit-déjeuner) et l'écrire dans le cahier des charges ;
   - page de confirmation, puis POST protégé par CSRF ; un bénévole ne peut annuler que ses propres inscriptions ;
   - après la limite, le bouton disparaît et l'appli affiche le téléphone des coordinateurs. Ce numéro vient de la configuration (variable d'environnement, valeur factice dans `.env.example`) : jamais de numéro réel dans le dépôt. S4b pourra le déplacer dans les réglages de la saison ;
   - le verrou sur la soirée (R1) s'applique aussi à l'annulation, pour qu'une annulation et une inscription simultanées restent cohérentes ;
   - **alerte « soirée devenue incomplète »** (soirée des 7 prochains jours qui perd un minimum) : la condition est calculée et testée cette semaine, l'e-mail arrive en S5. Proposer dans le plan comment S5 la récupérera (événement applicatif, table d'événements…).
3. **« Mon compte »** (§6) :
   - voir ses données : e-mail, nom, prénom, pseudo, téléphone, accord de recontact ;
   - modifier son téléphone et son pseudo (mêmes règles de validation et d'unicité qu'au premier profil, rappel que le pseudo est public) ;
   - retirer ou donner son accord « Me recontacter la saison prochaine » ;
   - **supprimer son compte** : page de confirmation explicite, puis POST. La suppression annule ses inscriptions futures et anonymise son historique : plus aucun e-mail, nom, prénom, pseudo ni téléphone en base, les inscriptions passées restent comptées sans être rattachables à une personne. Toutes ses sessions sont invalidées (sessions en base, ADR-0007). Proposer dans le plan le schéma d'anonymisation compatible avec les contraintes existantes (e-mail et pseudo uniques et non nuls, `volunteer_id` non nul) ;
   - l'e-mail n'est pas modifiable dans cette version : le dire à l'écran.
4. **Documentation** : cahier des charges (R6 précisé, annulation logique, anonymisation), modèle de menaces si une menace change, `CLAUDE.md` et `.env.example` pour la nouvelle variable.

## Tests (écrits avant le code)

- **Domaine** : limite de R6 à 23 h 59 heure de Paris, y compris au changement d'heure ; une inscription annulée ne compte plus dans R1, R2, R3 et R5 ; condition de l'alerte « soirée devenue incomplète ».
- **Base** : après annulation, un autre bénévole peut prendre le plat libéré, et le bénévole annulé peut se réinscrire sur la même soirée ; deux inscriptions actives identiques restent refusées.
- **Concurrence** : une annulation et une inscription simultanées sur la même soirée donnent un état cohérent.
- **Accès** : chaque nouvel endpoint a un test d'accès refusé sans connexion ; un bénévole ne peut ni voir ni annuler l'inscription d'un autre, ni modifier ou supprimer un autre compte ; POST sans CSRF refusé ; un GET n'annule ni ne supprime rien.
- **Suppression** : après suppression, aucune donnée personnelle du bénévole en base (requête de vérification sur toutes les colonnes concernées), ses inscriptions futures sont annulées, ses sessions ne sont plus valides, son pseudo n'apparaît plus sur `/planning` et peut être repris par quelqu'un d'autre.
- **Logs** : aucun e-mail, nom, pseudo ni téléphone dans les logs pendant ces parcours.

## Règles

- Données de test factices uniquement (`@example.org`, numéros fictifs).
- La nouvelle migration passe sur la base de production (aucun bénévole réel) et sur une base de développement qui contient des inscriptions de test.
- La session tourne sur le PC de développement, pas dans une VM jetable : ne rien modifier hors du dépôt, ne pas lancer `docker compose down -v` ni `git push --force` sans le demander.
- Si un point du cahier des charges est ambigu, le poser dans le plan plutôt que de trancher seul.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte (tests, revue des dépendances, scan de l'image)
- [ ] Parcours testé à la main en local à 360 px : inscription, désinscription avant et après la limite (avec `APP_CLOCK_OFFSET`), modification du profil, suppression du compte
- [ ] Aucune donnée personnelle d'un compte supprimé en base, ni dans les logs
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, comment tester, points de sécurité, questions ouvertes

## Si quelque chose bloque

Si l'anonymisation ne tient pas avec les contraintes actuelles, le dire et proposer une migration dans le plan, plutôt que d'affaiblir une contrainte d'unicité ou de supprimer l'historique sans le dire. Ne désactive jamais un test ni CSRF pour avancer.
