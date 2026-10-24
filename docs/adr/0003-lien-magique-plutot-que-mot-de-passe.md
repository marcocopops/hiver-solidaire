# ADR-0003 — Connexion par lien magique plutôt que par mot de passe

- **Statut** : proposé
- **Date** : 2026-10-24

## Contexte

Les bénévoles sont une vingtaine, majeurs, et reviennent de façon occasionnelle. L'objectif est de s'inscrire en moins de 30 secondes sans compte à créer ([cahier des charges](../cahier-des-charges.md) §1, §6). Toute personne disposant du lien peut demander une connexion : l'appli ne stocke donc des données qu'après une preuve de contrôle de l'adresse e-mail.

Les coordinateurs voient toutes les coordonnées. Ils ont en plus Cloudflare Access sur `/admin/*` (§8).

## Options envisagées

1. **Lien magique** (jeton à usage unique de Spring Security, valable 15 minutes, validé en POST) : aucun mot de passe à stocker, à oublier ou à réinitialiser. En contrepartie, la connexion dépend de la délivrabilité des e-mails, et le lien peut être intercepté ou consommé par un filtre de messagerie qui ouvre les liens.
2. **Mot de passe** : connu des utilisateurs et sans dépendance à l'e-mail pour chaque connexion. En contrepartie, il faut le hacher et le stocker, gérer l'oubli (qui passe de toute façon par un e-mail), la force minimale et le bourrage d'identifiants ; pour un usage occasionnel, la plupart l'oublieraient.
3. **Code à saisir reçu par e-mail** : pas de lien à consommer, donc insensible aux filtres de messagerie. En contrepartie, sur mobile il faut passer d'une appli à l'autre et recopier le code, et le code peut être deviné par force brute s'il est court.
4. **Passkeys (WebAuthn)** : très robuste contre l'hameçonnage. En contrepartie, l'inscription initiale et la récupération restent à concevoir, et la prise en main est plus lourde pour un public occasionnel.

## Décision

Option 1. Le lien reçu ouvre une page de confirmation ; la connexion se fait par un POST protégé par CSRF, car un GET qui consomme le jeton serait déclenché par les filtres des messageries. Le jeton est à usage unique et valable 15 minutes. La réponse à la demande est identique que l'adresse soit connue ou non, et les demandes sont limitées par adresse et par IP, en plus de Turnstile.

Le mécanisme est fourni par Spring Security (One-Time Token Login), ce qui évite d'écrire du code d'authentification maison. D'après la [documentation](https://docs.spring.io/spring-security/reference/servlet/authentication/onetimetoken.html) (version 7.1.2, consultée le 24 octobre 2026) :

- la demande de jeton est un `POST /ott/generate` ; la livraison du lien est à écrire (un `OneTimeTokenGenerationSuccessHandler`), Spring ne choisit pas le canal ;
- le lien de l'exemple pointe vers `/login/ott?token=…`, qui affiche une page de saisie préremplie ; le jeton n'est utilisé qu'à l'envoi de cette page, ce qui correspond à notre exigence de confirmation avant consommation (le fait que cet envoi soit bien un POST protégé par CSRF reste à vérifier par un test) ;
- la durée par défaut est de **5 minutes**, modifiable (`GenerateOneTimeTokenRequestResolver`) : il faudra la porter à 15 minutes pour suivre le cahier des charges ;
- le stockage par défaut est en mémoire (`InMemoryOneTimeTokenService`) ; la documentation recommande `JdbcOneTimeTokenService` en production.

## Conséquences

- Pas de mot de passe dans la base ni dans les logs.
- Si Brevo tombe, personne ne peut se connecter : à couvrir par le plan B ([modèle de menaces](../securite/modele-de-menaces.md)).
- Une boîte mail compromise donne l'accès au compte : risque accepté pour des bénévoles, atténué par l'expiration du jeton et la durée limitée des sessions.
- La session de 30 jours demande un mécanisme distinct du jeton (type « se souvenir de moi »), à préciser en S3 ; elle ne s'applique pas aux coordinateurs, dont les sessions durent 8 h.
- Le stockage en mémoire par défaut perd les jetons à chaque redémarrage de l'appli (un lien envoyé juste avant un déploiement cesse de fonctionner) : choisir le stockage JDBC, avec purge des jetons expirés, à trancher en S3.
