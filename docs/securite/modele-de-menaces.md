# Modèle de menaces — Hiver Solidaire

*Version 0.7 · 13 novembre 2026 (exploitation : sauvegardes, sonde, mises à jour, revue complète du dépôt, S6) · statut : à relire · méthode : STRIDE allégé*

Ce document répond à l'exigence du [cahier des charges](../cahier-des-charges.md) §8 (modèle de menaces documenté avant l'ouverture). Les probabilités et les impacts sont une première estimation (faible, moyen, élevé), à relire avant la bêta. Les mesures renvoient aux puces de la section « Sécurité » du §8.

## 1. Périmètre et hypothèses

- Appli Spring Boot sur un homelab, publiée par un tunnel Cloudflare sortant ; PostgreSQL sur un réseau Docker dédié ; e-mails par Brevo ; code et images sur GitHub et GHCR ([§9](../cahier-des-charges.md)).
- Une vingtaine de bénévoles majeurs, 2 à 3 coordinateurs. L'accès est libre à toute personne disposant du lien (§2), donc l'attaquant le plus probable est un inconnu sur Internet, pas un initié.
- Aucune donnée sur les personnes accueillies ; l'adresse du lieu n'est pas stockée (§6, lot 2).
- Hors périmètre : la sécurité des appareils des utilisateurs, celle de Cloudflare, de Brevo et de GitHub eux-mêmes, et un attaquant disposant d'un accès physique au homelab.

## 2. Flux et frontières de confiance

```mermaid
flowchart LR
    subgraph Internet["Zone non fiable : Internet"]
        V["Visiteur / bénévole<br/>(navigateur)"]
        C["Coordinateur<br/>(navigateur)"]
        A["Attaquant"]
    end

    subgraph CF["Cloudflare (tiers)"]
        E["DNS, WAF, limitation de débit,<br/>Turnstile"]
        AC["Access sur /admin/*"]
    end

    subgraph Home["Homelab (zone de confiance)"]
        T["cloudflared"]
        subgraph Net["Réseau Docker dédié"]
            APP["Appli Spring Boot"]
            DB[("PostgreSQL")]
        end
        BK["restic (conteneur)<br/>sauvegardes chiffrées"]
    end

    subgraph Tiers["Services tiers"]
        BR["Brevo (e-mails)"]
        OFF["Cloudflare R2 (UE)<br/>sauvegardes chiffrées"]
        MON["UptimeRobot, healthchecks.io<br/>(surveillance)"]
    end

    subgraph CI["Chaîne CI/CD"]
        GH["GitHub (dépôt public, Actions)"]
        REG["GHCR (images)"]
    end

    V -->|HTTPS| E
    A -->|HTTPS| E
    C -->|HTTPS| E
    E --> AC
    E -->|tunnel sortant| T
    AC -->|tunnel sortant| T
    T --> APP
    APP --> DB
    APP -->|SMTP TLS| BR
    BR -.->|lien magique, confirmations, rappels| V
    BR -.->|alertes, récap quotidien| C
    DB --> BK
    BK -->|chiffré| OFF
    MON -->|GET /sante| E
    BK -.->|pings : code de sortie| MON
    GH -->|build, scan| REG
    REG -.->|pull périodique| Home
```

Frontières de confiance :

1. **Internet → Cloudflare** : tout contenu venant du navigateur est hostile (validation, CSRF, XSS).
2. **Cloudflare → homelab** : seul `cloudflared` parle à l'appli ; aucun port entrant ouvert.
3. **Appli → base** : seule l'appli parle à la base ; compte applicatif sans droits d'administration.
4. **Homelab → tiers** : Brevo reçoit les adresses des destinataires et le contenu des e-mails, dont le récap des coordinateurs (noms, pseudos et téléphones des inscrits des 7 jours suivants) ; Cloudflare R2 ne reçoit que des sauvegardes chiffrées par restic (la clé reste sur le serveur et dans le gestionnaire de mots de passe du porteur du projet) ; healthchecks.io ne reçoit que des horaires et des codes de sortie ; UptimeRobot n'interroge que `/sante`, qui ne répond qu'un code de statut.
5. **Chaîne CI → homelab** : le homelab tire des images, GitHub n'a aucune clé ni accès au homelab.
6. **Image → hôte** : l'image s'exécute dans un conteneur durci ; les fichiers de déploiement qu'elle embarque ne sont que comparés à ceux du serveur, jamais exécutés ni copiés automatiquement (ADR-0015).

## 3. Actifs

| Id | Actif | Pourquoi il compte |
|---|---|---|
| A1 | Données des bénévoles (nom, prénom, e-mail, téléphone, inscriptions) | Données personnelles ; RGPD ; confiance des bénévoles |
| A2 | Sessions des coordinateurs | Donnent accès à tout, et à des actions d'administration |
| A3 | Disponibilité pendant la saison | Le planning organise une activité du soir au matin pour des personnes vulnérables |
| A4 | Réputation de l'association | Un incident ou un spam à son nom abîme la confiance |
| A5 | Secrets (Brevo, base, tunnel, sauvegardes) | Donnent accès aux actifs ci-dessus |
| A6 | Sauvegardes | Contiennent A1 ; condition de la reprise |
| A7 | Adresse et consignes du lieu (plus stockées par l'appli depuis le lot 2) | Sécurité des personnes accueillies |

## 4. Menaces

Catégories : **S** usurpation, **T** altération, **R** répudiation, **I** divulgation d'information, **D** déni de service, **E** élévation de privilèges. Statut : *prévu* (décidé dans le cahier des charges, à construire), *à décider*, *accepté*.

| Id | Menace | STRIDE | Actifs | Prob. | Impact | Mesure prévue | Statut |
|---|---|---|---|---|---|---|---|
| M1 | Lien magique intercepté (boîte mail compromise, e-mail transféré, historique) et rejoué | S | A1 | Faible | Moyen | Jeton à usage unique, 15 minutes, stocké haché et consommé atomiquement ; session avec nouvel identifiant à la connexion ; cookie `__Host-` HttpOnly, Secure (§8 « Lien magique », « Cookie ») | Fait (S3) |
| M2 | Lien consommé par le filtre d'une messagerie avant le bénévole (connexion impossible, ou connexion de la mauvaise session) | S, D | A1 | Élevée | Faible | Le lien ouvre une page de confirmation ; connexion en POST seulement, jamais de GET qui modifie (§6, §8) | Fait (S3) |
| M3 | Énumération d'adresses e-mail : réponse ou délai différent selon que l'adresse existe | I | A1 | Moyenne | Moyen | Même message et même durée de traitement dans les deux cas (envoi asynchrone, aucune recherche du compte à la demande) ; limitation par e-mail et par IP ; Turnstile vérifié avant tout envoi, même réponse quelle que soit l'issue pour une adresse connue ou inconnue (test) (§8, ADR-0013) | Fait (S3, S5b) |
| M4 | Inscriptions malveillantes avec accès libre : robots qui saturent les places, créent des comptes, remplissent la base | D, T | A3, A4 | Moyenne | Moyen | Turnstile sur la demande de lien, seule porte d'entrée (ADR-0013) ; si Cloudflare ne répond pas, refus ; limitation de débit Cloudflare et appli ; signalement de chaque nouveau bénévole aux coordinateurs (tableau de bord, récap) ; annulation par les coordinateurs ; comptes restés sans profil supprimés après 7 jours (ADR-0014) | Fait (S5b) |
| M5 | Abus de l'envoi d'e-mails : le formulaire sert à inonder une boîte tierce, ou épuise le quota de Brevo | D, R | A3, A4 | Moyenne | Moyen | Limitation par e-mail et par IP ; Turnstile vérifié avant tout envoi (S5b) ; e-mails automatiques bornés (S5a) : moins de 100 par jour estimés pour 300 permis, confirmations regroupées (un bénévole qui s'inscrit et se désinscrit en boucle reçoit un e-mail toutes les 10 minutes au plus), essais bornés à 6 par e-mail, un rappel et un récap par jour garantis par la base ; alerte si quota proche (**à décider** : seuil et supervision du quota) | En partie (S5a, S5b), supervision à décider |
| M6 | Compte coordinateur compromis (boîte mail hameçonnée, session volée, poste laissé ouvert) : lecture de toutes les coordonnées, exports, annulations et déplacements en masse, suppression de bénévoles, modification de la saison | S, E, I, T | A1, A2, A3, A7 | Faible | Élevé | Deux contrôles indépendants : Cloudflare Access sur `/admin/*` (code par e-mail ou fournisseur d'identité), puis le lien magique de l'appli ; rôle vérifié par l'appli sur chaque route (test sur toutes les routes du back-office) ; session de 8 h après la connexion ; aucun écran ne donne le rôle (liste dans la configuration du serveur, ADR-0010), donc un compte compromis ne crée pas d'autre coordinateur ; journal d'audit en ajout seul de chaque action et de chaque export ; aucune action ne touche une soirée passée ; une suppression de bénévole passe par une page de confirmation et l'anonymisation d'ADR-0008. Reste : retrait d'un coordinateur effectif à sa reconnexion (8 h au plus), sauf suppression manuelle de ses sessions (`deploy/README.md`) | Fait (S4b), sauf Access (configuration Cloudflare) |
| M7 | Contournement de Cloudflare Access en joignant l'appli sans passer par Cloudflare (tunnel mal configuré, autre route, autre conteneur du réseau `edge`) | E | A1, A2 | Faible | Élevé | Aucun port entrant ; le rôle coordinateur reste vérifié par l'appli, indépendamment d'Access (défense en profondeur) : l'appli ne lit aucun en-tête d'Access ; port de management lié à la boucle locale du conteneur (S6). Reste : le réseau `edge` est partagé avec d'autres applis du homelab ; un conteneur voisin compromis joint `hiver-solidaire-app:8080` sans Cloudflare et peut fixer `X-Forwarded-For` (adresses privées acceptées comme mandataires), donc contourner la limitation par IP. **À décider** : réseau dédié entre `cloudflared` et l'appli | Fait (S4b) côté appli ; réseau dédié à décider (S6) |
| M8 | Un coordinateur nie une action (annulation, suppression de bénévole) | R | A1 | Faible | Faible | Journal d'audit (`audit_log`) horodaté par identifiant technique, écrit dans la transaction de l'action ; table en ajout seul (déclencheur qui refuse `UPDATE` et `DELETE`) ; pas de compte partagé (un coordinateur = une adresse). Le compte applicatif de la base peut toujours supprimer le déclencheur : le journal protège contre une erreur de l'appli, pas contre un accès direct à la base | Fait (S4b) |
| M9 | Accès croisé : un bénévole lit ou modifie les données d'un autre (IDOR), annule son inscription ou supprime son compte | I, E, T | A1 | Moyenne | Élevé | Autorisation côté serveur sur chaque endpoint ; le bénévole est celui de la session, jamais un paramètre ; l'inscription à annuler est cherchée parmi les siennes par la date de la soirée (aucun identifiant d'inscription dans l'URL, même réponse 404 qu'une soirée inconnue) ; tests d'accès croisé ; identifiants non devinables (§8) | Fait (S3, S4a) pour les bénévoles |
| M10 | CSRF : action déclenchée à l'insu d'un bénévole ou d'un coordinateur connecté (désinscription, suppression du compte, actions du back-office) | T | A1, A2 | Faible | Moyen | Protection CSRF active, POST pour toute action qui modifie l'état, page de confirmation avant chaque action ; les exports sont aussi des POST ; le sous-domaine est *same-site* du portfolio, donc le jeton reste obligatoire ([ADR-0001](../adr/0001-sous-domaine-plutot-que-sous-chemin.md)) | Fait (tests POST sans jeton, toutes les routes du back-office comprises) |
| M11 | XSS (contenu saisi affiché : pseudo sur la page publique, nom de bénévole, horaires et contact des coordinateurs dans le back-office et l'espace bénévole) | T, I | A2, A1 | Faible | Élevé | `th:text` uniquement, CSP stricte sans script inline, `frame-ancestors 'none'` (§8) ; cookie de session sur sa propre origine (ADR-0001) ; pseudo limité à lettres latines, chiffres, `-` et `_`, et `CHECK` en base refusant `< > & " '` ; nom et prénom limités aux lettres et à la ponctuation des noms ; textes de la saison bornés en longueur ; contact des coordinateurs limité à un numéro français ou une adresse sans caractère réservé de `mailto:` (lot 2) | Fait (S4b) |
| M12 | Injection SQL | T, I | A1 | Faible | Élevé | JPA et `JdbcTemplate` avec paramètres seulement ; aucune requête construite par concaténation (revue complète du dépôt, S6) ; scripts d'exploitation : nom de la base pris dans le conteneur et cité par psql (`:"db"`) | Fait (revu en S6) |
| M13 | Données personnelles dans les logs, le journal d'audit, les sauvegardes non chiffrées ou les exports laissés traîner | I | A1 | Moyenne | Moyen | Aucun e-mail, téléphone ni nom dans les logs (testé sur les parcours du back-office) ni dans le journal d'audit (aucune colonne pour cela, testé) ; sauvegardes chiffrées ; exports CSV et page imprimable réservés aux coordinateurs, en POST audités, servis avec `Cache-Control: no-store` ; l'écran des exports rappelle de ne pas garder ni transférer les fichiers et de détruire les feuilles. **À décider** : consigne écrite aux coordinateurs sur la conservation des exports | Fait (S4b), consigne à décider |
| M14 | Vol ou perte de la clé de chiffrement des sauvegardes, ou sauvegardes inutilisables | D, I | A6, A3 | Faible | Élevé | Mot de passe restic dans `backup.env` (chmod 600, vérifié par `backup.sh`, lu par le seul conteneur restic) et dans le gestionnaire de mots de passe du porteur du projet, hors du homelab ; relecture du dump avant l'envoi, `restic check` chaque semaine ; restauration éprouvée par un test qui lance les vrais scripts (`BackupRestoreScriptTest`) et par un test réel noté dans le runbook ([ADR-0016](../adr/0016-sauvegardes-restic-vers-r2-dans-un-conteneur.md)). **À décider** : une seconde personne de confiance qui aurait aussi le mot de passe | Fait (S6) ; test réel avant la bêta, seconde personne à décider |
| M15 | Homelab hors service en pleine saison (électricité, box, disque, panne de Cloudflare) | D | A3 | Moyenne | Élevé | Sonde UptimeRobot toutes les 5 minutes sur `/sante` (appli et base), alerte par e-mail et téléphone ; RTO 4 h, RPO 24 h (sauvegarde chaque nuit) ; récap quotidien à 20 h (S5a) et PDF chez les coordinateurs ; ancien Google Form prêt ; [runbook](../exploitation/runbook.md) « site indisponible » avec délais et message aux coordinateurs (S6) | Fait (S6) ; moniteur à créer chez UptimeRobot |
| M16 | Le plan B tombe avec le reste : le récap quotidien dépend du homelab et de Brevo | D | A3 | Moyenne | Moyen | Le récap de la veille au soir reste dans les boîtes des coordinateurs ; un récap manqué (appli arrêtée à 20 h) est rattrapé jusqu'au lendemain 8 h (ADR-0012) ; planning imprimable des 7 prochains jours (S4b), enregistré en PDF par un coordinateur et remis aux autres à l'avance, régulièrement (**à décider** : fréquence) | En partie (S5a), fréquence à décider |
| M17 | Compromission de la chaîne CI : action GitHub tierce piégée, dépendance Maven ou image de base malveillante, image GHCR remplacée | T, E | A1, A5 | Faible | Élevé | Dependabot (Maven, actions, `Dockerfile`, fichiers Compose depuis S6) avec délai de 3 à 7 jours, revue des dépendances, scan de l'image ; actions épinglées par empreinte ; images épinglées par tag versionné et empreinte ; pas de runner auto-hébergé, déploiement en pull sans clé du homelab dans GitHub ; pendant la saison, production épinglée par empreinte (`APP_TAG=sha-…@sha256:…`) : une image remplacée sur GHCR est refusée (S6) ; fichiers de déploiement de l'image jamais copiés automatiquement (ADR-0015). Reporté : signature ou attestation de l'image | Fait en partie (S6) ; signature reportée |
| M18 | Fuite de secrets (fichier d'environnement, dépôt public, image publique) | I, E | A5 | Faible | Élevé | Secrets hors du dépôt (`.env`, `backup.env`, chmod 600), illisibles par Claude Code ; `.env.example` et `backup.env.example` factices ; liste blanche de `.dockerignore` : aucun autre fichier de `deploy/` n'entre dans l'image (test) ; adresses de ping jamais écrites dans les logs | Fait (S6) |
| M19 | Divulgation de l'adresse du lieu à des personnes non autorisées | I | A7 | Moyenne | Élevé | L'appli ne stocke plus l'adresse du lieu (lot 2) : elle n'apparaît ni sur la page publique, ni dans l'espace bénévole, ni dans les e-mails | Sans objet dans l'appli (lot 2) |
| M20 | Le prestataire d'e-mail ou Cloudflare lit ou perd des données | I, D | A1 | Faible | Moyen | Sous-traitants cités dans la notice ; e-mails sans donnée inutile (§7) : aucune adresse du lieu ni consigne, aucune adresse e-mail de bénévole dans le récap, aucun nom dans les alertes immédiates ; suivi des ouvertures et des clics désactivé chez Brevo (`deploy/README.md`) | Accepté (R3) |
| M21 | Repérage d'une personne par son pseudo public : savoir qu'elle sera à l'accueil tel soir, à telle heure (planning public, horaires connus), voire en déduire le lieu en la suivant | I | A1, A7 | Faible | Moyen | Pseudo choisi par le bénévole, qui peut éviter son prénom (aide du champ) ; aucune autre donnée publique ; pas de pseudo sur les journées passées (plus de soirée fermée depuis S4) ; `noindex` sur toutes les pages ; décision produit assumée (§2) | Accepté (R6) |
| M22 | Usurpation de pseudo : un inconnu prend « MarieD » avant Marie, ou un pseudo trompeur (homoglyphe, « Coordination ») pour s'inscrire au nom d'un autre | S | A3, A4 | Faible | Faible | Alphabet latin seul (pas de cyrillique ni de lettres pleine chasse), unicité sans tenir compte de la casse ; le bénévole change son pseudo dans « Mon compte » (S4a) ; alerte de première inscription aux coordinateurs (S5) ; modification par les coordinateurs (S4b) | Accepté en partie (R6) |
| M24 | Données d'un compte supprimé qui subsistent (ligne, liens de connexion, sessions encore valides sur un autre appareil) | I | A1 | Faible | Moyen | Anonymisation garantie par un `CHECK` en base ; suppression des liens en cours et de toutes les sessions du bénévole dans la même transaction ; test de vérification sur toutes les colonnes ([ADR-0008](../adr/0008-annulation-logique-et-anonymisation.md)). Les sauvegardes antérieures en gardent une copie jusqu'à leur expiration | Fait (S4a) ; purge automatique des comptes selon les durées du §7 (S5b, ADR-0014) ; sauvegardes : 30 jours au plus (7 quotidiennes, 4 hebdomadaires, S6) |
| M25 | Désistements en masse : un compte compromis ou malveillant annule ses inscriptions, ou supprime son compte la veille, et vide des soirées | D | A3 | Faible | Moyen | Désinscription en autonomie seulement jusqu'à la veille 23 h 59 ; alerte « soirée devenue incomplète » enregistrée dans la même transaction, envoyée tout de suite par e-mail pour une soirée des 48 prochaines heures, dans le récap de 20 h sinon (S5a) ; la page de suppression demande de prévenir les coordinateurs pour une soirée imminente | Fait (S5a) |
| M26 | Injection de formule par l'export CSV : un nom ou un pseudo qui commence par `=`, `+`, `-` ou `@` s'exécute dans le tableur d'un coordinateur (lien piégé, exfiltration) | T, I | A1, A2 | Faible | Moyen | Toute cellule qui commence par `=`, `+`, `-`, `@`, une tabulation ou un retour chariot est préfixée d'une apostrophe ; cellules entre guillemets, guillemets doublés (test) ; en amont, nom, prénom et pseudo n'acceptent pas ces caractères en tête | Fait (S4b) |
| M27 | Bénévole sans e-mail créé par un coordinateur : compte utilisé pour se connecter à la place de quelqu'un, ou données saisies sans l'accord de la personne | S, I | A1 | Faible | Faible | Sans adresse, aucune connexion possible (la connexion cherche le compte par l'adresse du lien) ; mêmes règles de saisie qu'au premier profil ; création auditée ; la personne est informée par le coordinateur qui l'inscrit, et la notice le mentionne ; sans accord de recontact, anonymisée un mois après la fin de la saison | Fait (S4b, S5b) |
| M28 | Usurpation des e-mails de l'appli : un hameçonnage imite une confirmation ou un rappel (« désinscrivez-vous ici ») pour voler une session ou des données, ou l'en-tête « Répondre à » détourne les réponses | S, I | A1, A4 | Faible | Moyen | Domaine d'envoi authentifié chez Brevo (SPF, DKIM, DMARC, §9) ; expéditeur fixe « Hiver Solidaire » (`MAIL_FROM`) ; nos e-mails ne demandent jamais d'information ni de mot de passe, et leurs liens pointent vers `APP_BASE_URL`, en clair, sans réécriture ni suivi (test sur les quatre e-mails) ; le lien de désinscription ouvre une page de confirmation (GET sans effet) ; le lien de connexion n'est envoyé qu'à la demande ; « Répondre à » ne vient que du contact de la saison, validé (adresse sans caractère réservé) et saisi par un coordinateur ; sujets sans donnée saisie par un utilisateur (pas d'injection d'en-tête) | Fait (S5a), DMARC à vérifier au déploiement |
| M29 | Fuite des coordonnées par le récap : boîte d'un coordinateur compromise, récap transféré ou imprimé et laissé traîner, adresse de coordinateur mal saisie dans `APP_COORDINATOR_EMAILS` | I | A1 | Faible | Moyen | Contenu minimal : 7 jours, nom, pseudo et téléphone des inscrits, jamais leur adresse e-mail ; destinataires limités à `APP_COORDINATOR_EMAILS` (ADR-0010), sans copie ailleurs ; mention « ne le transférez pas, supprimez-le quand il ne sert plus » ; les alertes immédiates ne contiennent aucun nom ; journal technique sans adresse ni contenu (test). Reste : un récap par jour dans 2 à 3 boîtes, conservé selon leurs habitudes (**à décider** : consigne écrite aux coordinateurs, avec celle des exports, M13) | Fait (S5a), consigne à décider |
| M30 | E-mails envoyés à des adresses factices du dépôt ou des tests (`example.org`…) : rebonds, réputation du domaine d'envoi, quota de Brevo consommé, ou données d'un environnement de test qui partent vers un domaine réel | I, D | A4 | Faible | Faible | Garde-fou : aucun envoi vers un domaine réservé (RFC 2606, 6761 : `example.org`, `.com`, `.net`, `.test`, `.example`, `.invalid`, `.localhost`) ; e-mail abandonné (`RESERVED_DOMAIN`) sans erreur, lien de connexion non envoyé avec la même réponse (test) ; désactivable en développement seulement (`APP_MAIL_ALLOW_RESERVED_DOMAINS`, jamais transmis par `compose.prod.yaml`) | Fait (S5b) |
| M31 | Script tiers sur la page de connexion : le script de Turnstile, servi par Cloudflare, est modifié ou compromis et lit ou détourne ce que saisit le bénévole | T, I | A1 | Faible | Moyen | Exception limitée à `/connexion` : `script-src` et `frame-src` ouverts à `https://challenges.cloudflare.com` seulement, CSP stricte partout ailleurs (test page par page, aucun `unsafe-inline`) ; la page ne contient que le champ d'adresse, aucune donnée personnelle affichée ; Cloudflare est déjà le réseau du site, et pourrait de toute façon modifier les pages en transit (R3) ; jeton vérifié par le serveur, jamais une décision du navigateur (ADR-0013) | Accepté (R3) |
| M23 | Indexation par les moteurs de recherche : les pseudos et les soirées d'un bénévole deviennent trouvables hors de la communauté | I | A1 | Moyenne | Faible | `X-Robots-Tag: noindex, nofollow` sur toutes les réponses et `<meta name="robots">` ; aucun lien public attendu en dehors de la communauté | Fait |
| M32 | Sauvegardes effacées ou rendues inutilisables depuis un serveur compromis (rançongiciel, suppression du bucket par la clé R2) | D | A6, A3 | Faible | Élevé | Clé R2 limitée au seul bucket ; sauvegardes chiffrées (rien de lisible) ; une sauvegarde manquante alerte (healthchecks.io). La clé est en écriture : restic doit pouvoir oublier les anciennes sauvegardes | Accepté (R7) |
| M33 | Page de santé publique `/sante` : divulgation (version, erreur, temps de réponse) ou déni de service (base sollicitée à chaque appel), seule exception à Cloudflare Access avant l'ouverture | I, D | A3 | Moyenne | Faible | Code de statut seulement, corps vide, aucun cookie, `no-store` ; résultat gardé 10 s, un seul contrôle à la fois, délai de 2 s ; route hors des pages (aucune lecture de la saison) ; règle *Bypass* d'Access limitée au chemin `sante` ; tests (S6) | Fait (S6) |
| M34 | Fichiers de déploiement du serveur divergents (variable manquante : service dégradé, cas de Turnstile en S5b) ou remplacés par ceux d'une image piégée (exécution de code sur l'hôte) | T, E, D | A3, A5 | Moyenne | Élevé | `update.sh` compare aux fichiers de l'image en service et signale l'écart (log, alerte), sans jamais rien remplacer ; un lien symbolique venu de l'image n'est jamais suivi ; `--diff` pour relire avant de copier (ADR-0015, tests) | Fait (S6) |
| M35 | Fuite du dump en clair ou des secrets de sauvegarde (disque, logs, service de surveillance) | I | A1, A5 | Faible | Élevé | Dump écrit seulement en mémoire (`/dev/shm`, refus sinon), supprimé dans tous les cas ; restic sans accès à la base ni à l'appli ; pings sans contenu ; logs des scripts sans donnée (tests) | Fait (S6) |
| M36 | Restauration en production erronée : mauvaise sauvegarde, sauvegarde du jour oubliée par la rétention avant d'être restaurée, appli relancée par le cron pendant la restauration, appli redémarrée sur une base vide | T, D | A1, A3 | Faible | Élevé | Contrôle dans une base jetable d'abord ; confirmation en retapant le nom de la base ; sauvegarde résolue en identifiant avant la sauvegarde préalable, faite sans rétention ; verrou d'`update.sh` ; en cas d'échec après l'effacement, l'appli reste arrêtée ; tests | Fait (S6) |

## 5. Risques résiduels acceptés

| Id | Risque | Raison de l'acceptation | À réexaminer si |
|---|---|---|---|
| R1 | Boîte mail d'un bénévole compromise : l'attaquant se connecte comme lui (M1) | Un seul compte, peu de données (les siennes) ; pas de mot de passe plus sûr pour ce public | L'appli expose davantage de données aux bénévoles |
| R2 | ~~Tout inconnu qui s'inscrit voit l'adresse du lieu (M19)~~ Levé (lot 2) : l'appli ne stocke plus l'adresse | — | — |
| R3 | Dépendance à Cloudflare, Brevo et GitHub (M15, M20) | Pas d'alternative gratuite et simple ; plan B manuel | Panne longue pendant la saison |
| R4 | Disponibilité d'un homelab inférieure à celle d'un hébergeur ([ADR-0004](../adr/0004-homelab-et-cloudflare-tunnel-plutot-que-vps.md)) | Coût et apprentissage ; RTO de 4 h supportable avec le plan B | Bêta ou première moitié de saison en dessous de 99,5 % |
| R5 | Bénévole ou coordinateur malveillant dans un cercle de personnes qui se connaissent : un coordinateur voit et exporte toutes les coordonnées | Hors portée d'un MVP ; 2 à 3 coordinateurs désignés par le porteur du projet ; le journal d'audit dissuade et documente | Le nombre de coordinateurs ou de bénévoles augmente |
| R6 | Un pseudo public révèle qui vient tel soir à quiconque a le lien (M21, M22) | Décision produit (§2) : permettre de s'inscrire avec les gens qu'on connaît ; l'accès est libre, réserver le pseudo aux bénévoles connectés ne protégerait rien ; aucune autre donnée n'est publique et le bénévole choisit son pseudo | Incident de harcèlement, demande d'un bénévole, ou accès au site restreint à l'avenir |
| R7 | Un serveur compromis peut effacer les sauvegardes (M32) | Restic doit pouvoir oublier les anciennes sauvegardes ; la clé est limitée au bucket ; le serveur est sous la seule responsabilité du porteur du projet | Incident sur le homelab ; ou passage à un second dépôt hors ligne (`restic copy`) ou à un verrouillage d'objets chez R2 |

## 6. Actions à suivre

| Quand | Action | Menaces |
|---|---|---|
| S3 | Tests d'accès croisé et de non-énumération ; comportement du lien magique avec un filtre de messagerie (fait : le GET du lien ne consomme pas le jeton) ; pseudo public : règles de forme, unicité, `noindex` (fait) | M2, M3, M9, M11, M21-M23 |
| S4 | Désinscription et « Mon compte » : tests d'accès croisé, CSRF, suppression sans reste (fait en S4a). Vérification du rôle côté appli sur toutes les routes du back-office, sessions de 8 h, journal d'audit, modification d'un pseudo par un coordinateur, export CSV neutralisé (fait en S4b). Reste : application Cloudflare Access à créer (`deploy/README.md`) | M6, M7, M8, M9, M10, M22, M24, M25, M26, M27 |
| S5 | E-mails automatiques par une table d'envois (fait en S5a : M25, M28, M29, une partie de M5 et M16) ; Turnstile, garde-fou des domaines réservés, notice mentionnant le pseudo public, Cloudflare et Brevo, purge selon les durées de conservation (fait en S5b : M3, M4, M24, M27, M30, M31). Reste : suivi du quota Brevo | M3, M4, M5, M16, M21, M24, M25, M27, M28, M29, M30, M31 |
| S6 | Fait : sauvegarde chiffrée et restauration éprouvée (test automatique), sonde `/sante`, runbook, mises à jour épinglées et écarts signalés, revue complète du dépôt, port de management sur la boucle locale. Reste : test de restauration réel, moniteurs UptimeRobot et healthchecks.io, réseau dédié entre `cloudflared` et l'appli (à décider), signature de l'image (reportée) | M7, M12, M14, M15, M17, M18, M32-M36 |
| S8 | Plan B prêt (planning imprimable remis en PDF, Google Form), relecture du présent document | M15, M16 |
