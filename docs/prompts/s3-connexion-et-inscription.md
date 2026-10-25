# S3 — Connexion par lien magique et inscription

- **Semaine** : S3, du 2 au 8 novembre 2026
- **Où** : session cloud Claude Code (claude.ai/code), en mode plan
- **Modèle conseillé** : Opus (authentification et concurrence : les erreurs y sont coûteuses)
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s3-connexion-et-inscription.md, propose un plan, puis attends ma validation. »

## Objectif

Permettre à un bénévole de se connecter par lien magique et de s'inscrire sur une soirée depuis le planning, avec les règles de capacité garanties en base. À la fin de la session, le parcours « S'inscrire » du §6 marche de bout en bout en local, avec une boîte de réception de développement à la place des vrais e-mails.

## À lire avant de planifier

- `CLAUDE.md`
- `docs/cahier-des-charges.md` : §5 (R1 à R4, R7), §6 (« S'inscrire »), §7 (données collectées), §8 (sécurité)
- [ADR-0003](../adr/0003-lien-magique-plutot-que-mot-de-passe.md) : lien magique, et les points qu'il renvoie à S3
- [ADR-0005](../adr/0005-postgresql-plutot-que-sqlite.md) (verrou `FOR UPDATE`) et [ADR-0006](../adr/0006-soirees-stockees-plutot-que-calculees.md) (soirées stockées)
- `docs/securite/modele-de-menaces.md` : menaces sur le lien magique et l'énumération d'adresses
- Le code du module `planning` livré en S2

## Hors périmètre (sessions suivantes)

Désinscription et R6, « Mon compte », back-office et rôle coordinateur (S4) ; e-mails autres que le lien de connexion, Brevo, Turnstile, notice RGPD et purge (S5). Les alertes aux coordinateurs (R4, nouveau bénévole) ne sont pas envoyées cette semaine.

## Livrables

1. **Connexion par lien magique** (module `auth`), avec le One-Time Token Login de Spring Security :
   - formulaire de saisie de l'e-mail ; même réponse, que l'adresse soit connue ou non ;
   - lien valable 15 minutes, à usage unique ; ouvrir le lien affiche une page « Me connecter » et ne consomme pas le jeton ; seul le POST de cette page, protégé par CSRF, connecte ;
   - jetons stockés en base (`JdbcOneTimeTokenService` ou équivalent, nouvelle migration), avec purge des jetons expirés : ils survivent ainsi à un redémarrage ;
   - demandes de lien limitées par e-mail et par IP (une implémentation en mémoire suffit pour une instance unique, à documenter) ;
   - cookie de session `__Host-`, HttpOnly, Secure, SameSite=Lax, nouvel identifiant de session à la connexion ; vérifier que le cookie fonctionne en local sur `http://localhost`.
2. **Session de 30 jours sur l'appareil** (§6, étape 6), renvoyée à S3 par l'ADR-0003 : choisir le mécanisme (session longue côté serveur, « se souvenir de moi » persistant, Spring Session JDBC…) et l'acter dans un ADR (`0007`), au moins deux options, inconvénients compris. La session doit survivre à un redémarrage de l'appli.
3. **Envoi du lien** (module `notification`) :
   - une interface d'envoi, avec une implémentation SMTP configurée par variables d'environnement (valeurs factices dans `.env.example`) ; Brevo s'y branchera en S5 ;
   - en développement, **Mailpit** dans `compose.yaml` (image épinglée par empreinte, SMTP et interface web sur `127.0.0.1` seulement), pour lire le lien dans le navigateur ; rien dans le compose de production ;
   - le lien, le jeton et l'adresse e-mail ne sont **jamais** écrits dans les logs ;
   - sans SMTP configuré (cas de la production avant S5), l'appli démarre quand même et la demande de lien affiche un message d'indisponibilité neutre.
4. **Bénévole** (module `benevole`) : table et entité avec e-mail (unique, normalisé), nom, prénom, téléphone, accord « Me recontacter la saison prochaine » (décoché par défaut) et horodatages. Formulaire de première connexion (§6, étape 4), validé côté serveur. Le lien vers la notice d'information pointe vers une page provisoire, la notice arrive en S5. Aucun champ libre (§7).
5. **Inscription** (module `inscription`) :
   - depuis `/planning`, choix d'une soirée et d'un type encore disponible (et du plat pour les types qui préparent) ;
   - le choix ne doit pas être perdu si le bénévole ouvre le lien sur un autre appareil ou un autre navigateur que celui de la demande (cas fréquent : demande sur l'ordinateur, lien ouvert sur le téléphone) ; proposer la solution dans le plan ;
   - confirmation par POST protégé par CSRF, puis page de confirmation à l'écran (l'e-mail récapitulatif arrive en S5) ;
   - **R1** : transaction avec verrou sur la soirée (`SELECT … FOR UPDATE`), contrôle des places de chaque poste couvert ;
   - **R2** : une inscription par bénévole et par soirée, garantie aussi en base ;
   - **R3** et **R7** appliqués à l'écriture (soirée fermée, passée, hors fenêtre d'ouverture) ;
   - **R4** : une préparation chez soi est acceptée même sans récupération ; l'état reste calculable pour l'alerte de S5 ;
   - nouvelle migration : `registration.volunteer_id` non nul, avec clé étrangère.
6. **« Mes inscriptions »**, en lecture seule : le bénévole connecté voit ses inscriptions à venir, et seulement les siennes. Elle sert à vérifier le parcours ; la désinscription viendra en S4.
7. **Documentation** : `CLAUDE.md` (commandes, Mailpit), `README.md` (démarrer en local), `.env.example`, index des ADR. Mettre à jour le cahier des charges dans la même PR si l'implémentation s'en écarte.

## Tests (écrits avant le code)

- **Domaine** : R1, R2, R3, R7 à l'écriture, sans Spring.
- **Concurrence** (Testcontainers) : deux inscriptions simultanées sur la dernière place d'un poste, une seule réussit.
- **Lien magique** : le GET du lien ne connecte pas et ne consomme pas le jeton ; POST sans CSRF refusé ; jeton réutilisé refusé ; jeton expiré refusé (horloge simulée) ; même réponse pour une adresse connue ou inconnue ; limitation de débit.
- **Session** : attributs du cookie, nouvel identifiant après connexion, session conservée au-delà de 8 h et refusée au-delà de 30 jours.
- **Accès** : chaque nouvel endpoint protégé a un test d'accès refusé sans connexion ; un bénévole ne voit pas les inscriptions d'un autre.
- **Logs** : un test vérifie qu'aucune adresse e-mail ni aucun jeton n'apparaît dans les logs pendant le parcours.
- **Parcours** : planning, demande de lien, connexion, premier profil, inscription, « Mes inscriptions » ; le statut de la soirée change sur `/planning`. L'envoi est remplacé par un double de test : Mailpit sert aux essais manuels, pas aux tests automatisés.

## Règles

- Pas de nouvelle dépendance sans justification dans la PR (par exemple le starter mail de Spring Boot, ou Spring Session si l'ADR la retient).
- Données de test factices uniquement (`@example.org`, numéros fictifs).
- La nouvelle migration doit passer sur une base où `registration` est vide (cas de la production). Si elle échoue sur une base de développement qui contient des inscriptions de test, l'indiquer dans la PR avec la commande pour les vider.
- Si un point du cahier des charges est ambigu (par exemple ce que voit un bénévole déjà connecté sur `/planning`), le poser dans le plan plutôt que de trancher seul.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte (tests, revue des dépendances, scan de l'image)
- [ ] Parcours complet testé à la main en local avec Mailpit, sur un écran de 360 px
- [ ] ADR-0007 sur la session de 30 jours, index à jour
- [ ] Aucun e-mail, jeton ou lien dans les logs
- [ ] `/security-review` lancé, remarques traitées ou listées dans la PR
- [ ] PR ouverte : résumé, comment tester (y compris avec Mailpit), points de sécurité, nouvelles dépendances justifiées, questions ouvertes

## Si quelque chose bloque

Si le One-Time Token Login de Spring Security ne permet pas l'une des exigences (page de confirmation avant consommation, durée, stockage), le dire et proposer une solution dans le plan avant d'écrire du code d'authentification maison. Ne désactive jamais CSRF ni aucun test pour avancer.
