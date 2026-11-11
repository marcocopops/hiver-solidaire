# S5b — Turnstile, notice RGPD et purge

- **Semaine** : S5, du 16 au 22 novembre 2026 (après S5a, mergée et déployée)
- **Où** : Claude Code en local, en mode plan ; Opus, effort `high` pendant le plan, par défaut ensuite
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s5b-turnstile-et-rgpd.md, propose un plan, puis attends ma validation. »

## Objectif

Rendre le site prêt à être ouvert au public : protéger la demande de lien contre les robots, informer les bénévoles sur leurs données, et supprimer ces données quand elles ne servent plus.

## Décisions du porteur du projet

1. **Turnstile sur la demande de lien de connexion** (la seule page qui déclenche un envoi à une adresse saisie par un inconnu). Pas sur les autres formulaires : ils exigent déjà une session.
2. **Exception à la règle « pas de CDN »** : le script de Turnstile est servi par Cloudflare et ne peut pas l'être depuis le dépôt. La CSP s'ouvre à `https://challenges.cloudflare.com` pour `script-src` et `frame-src`, **et rien d'autre**. Un ADR acte cette exception, avec ses options (Turnstile, captcha maison, limites de débit seules) et ses inconvénients.
3. **Durées de conservation** : celles proposées au §7 du cahier des charges sont retenues jusqu'à avis contraire de l'association :
   - sans accord « Me recontacter » : anonymisation **un mois après la fin de la saison** ;
   - avec accord : conservation jusqu'au retrait, et au plus **3 ans sans participation** ;
   - compte créé mais resté sans profil (jamais terminé) : supprimé au bout de **7 jours** ;
   - données techniques (envois d'e-mails, alertes traitées, jetons, sessions expirées) : **6 mois** au plus.
4. **La notice ne contient aucune donnée réelle** dans le dépôt : le responsable de traitement est l'« Organisateur » saisi dans les réglages de la saison, et le contact est le « contact des coordinateurs » (comme la page d'accueil, lot 4). Si l'un manque, la notice l'indique sobrement et le tableau de bord le signale.
5. **Garde-fou d'envoi** : l'appli n'envoie jamais d'e-mail vers un domaine réservé aux tests (`example.org`, `example.com`, `example.net`, `*.test`, `*.example`, `*.invalid`, `*.localhost`, RFC 2606 et 6761). L'envoi est marqué comme abandonné, sans erreur. En développement, Mailpit les reçoit quand même ? Proposer dans le plan (le plus simple : le filtre ne s'applique qu'au SMTP réel, désactivable par une propriété de développement).

## Livrables

1. **Turnstile**
   - Widget sur le formulaire de demande de lien ; vérification côté serveur (`siteverify`) avant tout envoi, avec le client HTTP du JDK (pas de nouvelle dépendance si possible), délai court, adresse IP transmise par cloudflared.
   - Clés en variables d'environnement (`TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET_KEY`) ; en développement et en test, les **clés de test publiées par Cloudflare** (toujours valides) dans `.env.example` ; sans clé configurée en production, l'appli refuse de démarrer, ou la demande de lien est indisponible : proposer dans le plan.
   - Si Cloudflare ne répond pas : proposer dans le plan (refuser avec un message, plutôt que laisser passer).
   - Le message affiché reste le même, que l'adresse soit connue ou non (énumération, §8).
2. **Notice d'information** (`/donnees-personnelles` ou équivalent), en français clair : responsable, finalités, données, base légale (intérêt légitime pour l'organisation, consentement pour la conservation entre saisons), destinataires (coordinateurs), **pseudo public** (affiché sur le planning, visible par tous, comment le changer), sous-traitants (Cloudflare, Brevo), hébergement en France, durées, sauvegardes (« les sauvegardes antérieures gardent une copie jusqu'à leur expiration » : durée à reprendre de S6, à défaut « au plus 30 jours »), droits (accès et rectification dans « Mon compte », suppression, retrait du consentement, réclamation auprès de la CNIL). Liens vers la notice : premier profil (à côté de l'accord), « Mon compte », pied de page.
3. **Purge** : une tâche planifiée quotidienne (même principe que S5a, heure creuse, `Europe/Paris`) applique les durées ci-dessus. Elle **anonymise** (ADR-0008) plutôt que supprimer quand des inscriptions doivent rester comptées. Le journal d'audit est en ajout seul : il ne contient que des identifiants ; proposer dans le plan s'il est conservé tel quel (plus de donnée personnelle une fois les bénévoles anonymisés) ou purgé au-delà de 6 mois par une exception contrôlée du déclencheur. Chaque exécution laisse une ligne de log avec des comptes, jamais d'identité.
4. **Fiche du registre des traitements** : `docs/rgpd/registre.md`, gabarit rempli sans aucune donnée réelle (responsable : « [l'organisateur de la saison] »), à compléter par l'association.
5. **Documentation** : cahier des charges (§7 durées retenues, §8 Turnstile et CSP, §11 question 4), ADR Turnstile, modèle de menaces (robots, énumération, e-mails vers des domaines réservés), `deploy/README.md` (créer le widget Turnstile chez Cloudflare, variables), `CLAUDE.md`.

## À lire avant de planifier

- `CLAUDE.md` ; `docs/cahier-des-charges.md` §2, §6, §7, §8, §11
- ADR 0003, 0007, 0008, 0012 ; `docs/securite/modele-de-menaces.md`
- `auth` (demande de lien, limites de débit), `config.SecurityConfig` (CSP), `notification` (envois, table d'envois de S5a), `benevole` (anonymisation)

## Tests (avant le code)

- **Turnstile** : demande refusée sans jeton, avec un jeton refusé par `siteverify`, ou si `siteverify` ne répond pas ; acceptée avec un jeton valide (`siteverify` simulé, jamais d'appel réseau réel en test) ; même message affiché pour une adresse connue ou inconnue.
- **CSP** : l'en-tête n'autorise `challenges.cloudflare.com` qu'en `script-src` et `frame-src`, nulle part ailleurs ; aucun `unsafe-inline`.
- **Purge** (horloge simulée) : chaque durée appliquée à la bonne date, ni avant ni après ; un bénévole avec accord n'est pas touché avant 3 ans sans participation ; un compte sans profil disparaît après 7 jours ; les inscriptions passées restent comptées après anonymisation ; deux exécutions le même jour ne font rien de plus.
- **Garde-fou d'envoi** : aucun envoi vers un domaine réservé, envoi normal vers une autre adresse.
- **Notice** : accessible sans connexion, liée depuis le premier profil et « Mon compte », sans donnée réelle en dur (organisateur et contact viennent de la saison).

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Testé à la main en local : demande de lien avec les clés de test Turnstile, notice, purge déclenchée avec une horloge décalée
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte : résumé, points de déploiement (widget Turnstile à créer, variables à ajouter au `.env` du serveur), questions ouvertes pour l'association (durées, base légale)
