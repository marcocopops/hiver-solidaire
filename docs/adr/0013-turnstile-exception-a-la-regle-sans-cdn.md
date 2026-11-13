# ADR-0013 — Cloudflare Turnstile sur la demande de lien, exception à la règle « pas de CDN »

- **Statut** : proposé
- **Date** : 2026-11-16

## Contexte

L'accès au site est libre (cahier des charges §2) : n'importe qui peut saisir une adresse dans le formulaire de demande de lien. C'est la seule page qui déclenche un envoi vers une adresse saisie par un inconnu. Un robot peut s'en servir pour inonder une boîte tierce, épuiser le quota de Brevo (300 e-mails par jour), ou créer des comptes en masse (modèle de menaces M3, M4, M5). Les limites de débit de l'appli (3 demandes par adresse et par 15 minutes, 10 par IP et par heure, en mémoire) et celles de Cloudflare freinent un attaquant isolé, pas un robot qui change d'adresse IP.

Les autres formulaires exigent déjà une session, obtenue par ce lien.

Contrainte du projet : tout le JavaScript est servi depuis le dépôt (htmx compris, aucun CDN), et la CSP n'autorise que `'self'`. Le script de Turnstile ne peut pas être servi depuis le dépôt : Cloudflare le fait évoluer, et il charge son propre cadre.

## Options envisagées

1. **Limites de débit seules** (appli et Cloudflare).
   - Avantages : rien à ajouter, aucun tiers de plus, aucun JavaScript externe.
   - Inconvénients : un robot qui fait tourner ses adresses IP passe sous la limite par IP ; la limite par adresse ne protège pas le quota de Brevo. Aucune preuve qu'un humain est derrière la demande.
2. **Captcha maison** (question, calcul, image générée par l'appli).
   - Avantages : aucun tiers, aucun script externe, CSP inchangée.
   - Inconvénients : facile à casser automatiquement ; une épreuve visuelle ou textuelle gêne l'accessibilité (objectif WCAG 2.2 AA) et le public (bénévoles de tous âges, sur téléphone) ; du code de sécurité à écrire et à maintenir.
3. **Cloudflare Turnstile** : un widget, le plus souvent invisible, dont le jeton est vérifié par le serveur (`siteverify`).
   - Avantages : gratuit, sans épreuve visuelle dans la plupart des cas, conçu pour l'accessibilité ; Cloudflare est déjà le réseau du site (§9), donc aucun nouveau sous-traitant ; vérification côté serveur avec le client HTTP du JDK, sans dépendance.
   - Inconvénients : un script et un cadre servis par `challenges.cloudflare.com`, donc une exception à la CSP ; la connexion demande JavaScript ; dépendance à la disponibilité de Cloudflare pour se connecter ; l'adresse IP du client est transmise à Cloudflare (qui la voit déjà comme réseau du site).

## Décision

Option 3, **sur la seule page de demande de lien** (`/connexion`, GET et POST).

- La CSP de cette page ajoute `script-src 'self' https://challenges.cloudflare.com` et `frame-src https://challenges.cloudflare.com`, **rien d'autre** ; toutes les autres pages gardent la CSP stricte (`default-src 'self'`). Aucun `unsafe-inline` : le widget est rendu implicitement, sans script à nous. Un test vérifie l'en-tête page par page.
- Le serveur vérifie le jeton avant toute création de lien ou tout envoi : après la limite par IP (qui borne les appels à Cloudflare), avant la limite par adresse. Délais de 3 s (connexion) et 5 s (réponse).
- **Cloudflare ne répond pas** : la demande est refusée (503, « vérification momentanément impossible »), jamais acceptée sans vérification. Un robot qui saurait provoquer une panne ne doit pas y gagner un passage libre.
- La réponse ne dépend jamais de l'existence du compte (M3) : aucune recherche du bénévole avant l'envoi.
- **Clés** : `TURNSTILE_SITE_KEY` et `TURNSTILE_SECRET_KEY`. Sans clé, l'appli démarre et la demande de lien est indisponible, comme sans SMTP : le planning public et le plan B restent en ligne. Les clés de test publiées par Cloudflare (dans `.env.example`) ne sont acceptées qu'avec une `APP_BASE_URL` locale : un `.env` copié sur le serveur ne laisse pas passer les robots.

## Conséquences

- Se connecter demande JavaScript et la disponibilité de Cloudflare ; la page le dit (`<noscript>`). Les bénévoles déjà connectés (session de 30 jours) ne sont pas concernés.
- Le script de Cloudflare s'exécute avec les droits de la page de connexion : elle ne contient que le formulaire d'adresse, aucune donnée personnelle, et elle est seule à l'autoriser (modèle de menaces M31).
- Turnstile est cité dans la notice d'information, avec Cloudflare.
- En test, la vérification est remplacée par un double (`StubTurnstile`) ; le client `siteverify` est testé contre un serveur HTTP local. Jamais d'appel réseau réel.
- Si Cloudflare changeait les directives exigées par le widget, la CSP de `/connexion` serait à revoir, sans l'élargir aux autres pages.
