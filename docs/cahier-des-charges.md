# Hiver Solidaire — Cahier des charges du MVP

*Version 0.5 · 25 octobre 2026 · Marc-Antoine Meunier · statut : à valider*

Ce document est la référence du projet. Toute évolution passe par une PR qui le met à jour.

## 1. Contexte et objectifs

Le planning en ligne remplace le Google Form de 2025-2026 et ouvre aux bénévoles le lundi 14 décembre 2026. Chaque hiver, l'association accueille des personnes vivant à la rue, du soir au lendemain matin. Une vingtaine de bénévoles, tous majeurs et qui se connaissent, se relaient sur trois services : dîner, nuit, petit-déjeuner.

La saison 2025-2026 allait du lundi 5 janvier au soir au dimanche 22 mars au matin. Les inscriptions passaient par un Google Form qui alimentait un Google Sheet publié en lecture seule.

1. **S'inscrire en moins de 30 secondes** depuis un téléphone, sans compte à créer (moins de 2 minutes la première fois).
2. **Disposer d'un planning fiable et disponible toute la saison** : chacun voit ce qui manque, et les coordinateurs voient les trous en premier.
3. **Protéger les données des bénévoles** et ne rien enregistrer sur les personnes accueillies.
4. **Faire un projet vitrine et un terrain d'apprentissage** : Spring Boot, sécurité, exploitation, Claude de bout en bout.

Indicateurs en fin de saison : part des soirées complètes la veille ; aucun incident touchant les données ; disponibilité d'au moins 99,5 % ; moins de sollicitations des coordinateurs.

## 2. Décisions actées (18 octobre 2026)

- **Adresse** : `hiver-solidaire.marcantoine-meunier.com`, avec une redirection depuis `marcantoine-meunier.com/hiver-solidaire`. L'appli a sa propre origine, isolée du portfolio et du cv-assistant ([ADR-0001](adr/0001-sous-domaine-plutot-que-sous-chemin.md)).
- **Ouverture des inscriptions** : mi-décembre 2026.
- **Communauté** : une vingtaine de bénévoles qui se connaissent, tous majeurs.
- **Accès** : toute personne disposant du lien, sans validation préalable. Garde-fous : Turnstile, et une alerte aux coordinateurs à la première inscription d'un nouveau bénévole.
- **Activités** : identiques à 2025-2026.
- **Coordinateurs** : 2 à 3 personnes.
- **Désinscription** : en autonomie jusqu'à la veille ; ensuite, par téléphone auprès d'un coordinateur.
- **Coordonnées** : aucune n'est partagée entre bénévoles ; seuls les coordinateurs les voient.
- **Pseudo public** (décision du 25 octobre 2026) : chaque bénévole choisit un pseudo, affiché sur le planning pour que chacun voie qui vient et s'inscrive avec les gens qu'il connaît. C'est la seule donnée partagée ; nom, e-mail et téléphone restent réservés aux coordinateurs. Le pseudo est public, et non réservé aux bénévoles connectés : l'accès au site est libre, une telle restriction ne protégerait rien.
- **Remise des repas préparés chez soi** : organisée par un coordinateur ; une messagerie relayée viendra en V2.
- **Bénévole de nuit** : ne compte pas dans les 2 présences au dîner.
- **Conservation** : d'une saison à l'autre, uniquement avec l'accord du bénévole (case à cocher).

## 3. Calendrier

| Semaine | Dates | Livrable |
|---|---|---|
| S1 | 19 → 25 oct. | Cahier des charges validé, premiers ADR, dépôt et CI, squelette en ligne sur le sous-domaine via le tunnel |
| S2 | 26 oct. → 1er nov. | Modèle de données, planning public |
| S3 | 2 → 8 nov. | Connexion par lien magique, inscription, règles de capacité |
| S4 | 9 → 15 nov. | Désinscription, back-office des coordinateurs, Cloudflare Access |
| S5 | 16 → 22 nov. | E-mails (confirmation, rappel, alertes, récap), Turnstile, RGPD (notice, consentement, purge) |
| S6 | 23 → 29 nov. | Durcissement, sauvegardes et test de restauration, supervision, revue de sécurité |
| S7 | 30 nov. → 6 déc. | Bêta avec les coordinateurs et quelques bénévoles |
| S8 | 7 → 13 déc. | Gel des fonctionnalités, corrections, runbook, plan B prêt |
| — | **Lundi 14 déc.** | **Ouverture des inscriptions** |

La bêta de la semaine 7 laisse une semaine de marge avant le gel. Les dates de la saison 2026-2027 restent à confirmer (hypothèse : début janvier → fin mars). Elles seront paramétrables.

## 4. Utilisateurs et rôles

- **Visiteur** : voit le planning des soirées à venir, leur statut, les postes qui manquent (« Manque : nuit, petit-déjeuner ») et, par poste, les pseudos des inscrits (sauf sur une soirée fermée). Aucune autre donnée personnelle : ni nom, ni e-mail, ni téléphone. Les pages demandent aux moteurs de recherche de ne pas les indexer (`noindex`).
- **Bénévole** : se connecte par lien magique, s'inscrit, se désinscrit, consulte et supprime ses données. Connecté, il voit en plus sur le planning les soirées où il participe lui-même, rien sur les autres.
- **Coordinateur (2 à 3)** : a un accès complet au planning, aux bénévoles et au paramétrage.
- **Personnes accueillies** : aucune donnée les concernant n'est saisie.

## 5. Modèle métier

L'unité du planning est la soirée : du soir J (dîner) au matin J+1 (petit-déjeuner). Elle s'affiche ainsi : « Lun. 5 → mar. 6 janv. » (« Sam. 31 janv. → dim. 1er févr. » quand le mois change). La saison 2025-2026 en comptait 76.

Une saison est définie par sa première et sa dernière soirée (dates du soir J) et par la date d'ouverture des inscriptions. Ses soirées sont stockées en base, une ligne par soirée, générées à partir de ces dates ([ADR-0006](adr/0006-soirees-stockees-plutot-que-calculees.md)).

**Besoins par soirée** (paramétrables) :

| Poste | Besoin | Horaires |
|---|---|---|
| Plat principal (repas pour 8) | 1 | Livré pour 19 h |
| Accompagnements : soupe, pain, fromage, dessert | 1 | Livrés pour 19 h |
| Présence au dîner | 2 | 19 h → 21 h-21 h 30 |
| Nuit | 1 minimum, 2 maximum | Du dîner au petit-déjeuner, auxquels le bénévole participe |
| Petit-déjeuner | 1 | Le lendemain : 7 h en semaine, 8 h le week-end |

**Types d'inscription** (repris de 2025-2026) :

| Type | Postes couverts |
|---|---|
| Préparer et partager le dîner | 1 préparation (plat ou accompagnements, au choix) + 1 présence |
| Préparer le dîner chez soi et le tenir à disposition | 1 préparation |
| Récupérer le dîner et le partager | 1 présence + récupération d'un repas préparé chez soi |
| Passer la nuit et partager le dîner | 1 nuit (ne compte pas dans les 2 présences au dîner) |
| Fournir et partager le petit-déjeuner | 1 petit-déjeuner |

**Règles** :

- **R1 Capacité** : une inscription n'est acceptée que s'il reste de la place sur chaque poste qu'elle couvre. La base le garantit par une transaction avec verrou sur la soirée : jamais deux personnes sur la dernière place.
- **R2 Unicité** : une seule inscription par bénévole et par soirée (hypothèse), sauf décision d'un coordinateur.
- **R3 Préparation** : qui prépare choisit plat principal ou accompagnements, un de chaque par soirée.
- **R4 Récupération** : chaque « préparer chez soi » doit avoir un « récupérer et partager » distinct le même soir (deux repas préparés chez soi demandent deux récupérations) ; sinon, les coordinateurs sont alertés et organisent la remise.
- **R5 Statut** : une soirée est complète (tous les minimums couverts), partielle ou vide (aucune inscription), affichée par une couleur et un texte : « Complète » en vert, « À compléter » en orange, « Personne pour l'instant » en rouge ; une soirée fermée affiche « Fermée » en gris.
- **R6 Désinscription** : en autonomie jusqu'à la veille de l'activité à 23 h 59, heure de Paris. L'activité est le soir J pour tous les types, petit-déjeuner du matin J+1 compris : la soirée est l'unité du planning, et un désistement à minuit pour un petit-déjeuner à 7 h ne laisserait pas le temps de trouver quelqu'un. Une soirée fermée n'empêche pas de se désinscrire. Ensuite, l'appli affiche le téléphone des coordinateurs (configuration du serveur, puis réglages de la saison en S4b). Une désinscription n'efface pas l'inscription : elle la marque annulée, et une inscription annulée ne compte plus dans R1, R2, R3 ni R5 ([ADR-0008](adr/0008-annulation-logique-et-anonymisation.md)).
- **R7 Fenêtre** : inscriptions possibles de l'ouverture à la fin de saison, jamais sur une soirée passée. Une soirée est passée à partir de minuit le lendemain, heure de Paris : on peut encore s'inscrire le jour même. Un coordinateur peut fermer une soirée. La page publique n'affiche pas les soirées passées.

## 6. Parcours

**S'inscrire** :

1. Le bénévole choisit une soirée et un type d'inscription encore disponible.
2. Il saisit son e-mail dans un formulaire protégé par Turnstile. Le message affiché est le même, que l'adresse soit connue ou non.
3. Il ouvre le lien reçu (usage unique, valable 15 minutes) et appuie sur « Me connecter ». La connexion se fait en POST, car les filtres des messageries ouvrent les liens et consommeraient le jeton. Le lien porte aussi le choix de l'étape 1 (soirée, type, plat, sans donnée personnelle) : il fonctionne sur un autre appareil que celui de la demande.
4. La première fois, il donne son nom, son prénom, un **pseudo public** et son téléphone (obligatoire ; indicatif +33 par défaut, numéros étrangers acceptés, enregistré au format international). Le pseudo est proposé sous la forme prénom + initiale du nom (« MarieD ») et modifiable ; l'aide du champ dit qu'il est visible par tous sur le planning et qu'on peut choisir autre chose que son prénom. Il est unique sans tenir compte de la casse : en cas de doublon, une variante est proposée (« MarieD2 »). Une case « Me recontacter la saison prochaine », décochée par défaut, et un lien vers la notice d'information complètent le formulaire.
5. Il confirme ; un e-mail récapitule la date, les horaires, l'adresse, les consignes et le numéro des coordinateurs.
6. Il reste connecté 30 jours sur cet appareil (hypothèse) : session stockée en base, 30 jours au plus après la connexion, même utilisée chaque jour ([ADR-0007](adr/0007-session-jdbc-plutot-que-se-souvenir-de-moi.md)).

L'adresse du lieu n'apparaît pas sur la page publique, seulement dans l'e-mail et l'espace bénévole (hypothèse, pour la sécurité des personnes accueillies).

**Se désinscrire** : depuis « Mes inscriptions » ou le lien de l'e-mail (S5), jusqu'à la veille à 23 h 59 (R6). Le lien ouvre une page de confirmation, et l'action se fait en POST. La place libérée est immédiatement reprenable. Après la limite, le bouton disparaît et l'appli affiche le téléphone des coordinateurs. Si une soirée des 7 prochains jours (aujourd'hui compris) perd un minimum, les coordinateurs sont alertés : l'alerte est enregistrée dès S4 et envoyée par e-mail à partir de S5.

**Mon compte** : le bénévole voit ses données (e-mail, nom, prénom, pseudo, téléphone, accord de recontact), modifie son téléphone et son pseudo (mêmes règles qu'au premier profil), donne ou retire son accord de conservation, ou supprime son compte. L'e-mail n'est pas modifiable dans cette version ; nom et prénom se corrigent auprès d'un coordinateur. La suppression, après une page de confirmation, annule toutes ses inscriptions dont la soirée n'est pas passée (y compris après la limite de R6, avec l'alerte éventuelle) et anonymise son historique : plus aucun e-mail, nom, prénom, pseudo ni téléphone en base ; ses inscriptions passées restent comptées sans être rattachables à une personne ; il est déconnecté de tous ses appareils. Une nouvelle connexion avec la même adresse crée un nouveau compte.

**Back-office des coordinateurs** :

- Tableau de bord, avec les soirées incomplètes des 14 prochains jours en tête
- Fiche soirée : inscrits et coordonnées ; inscription par téléphone, même sans e-mail ; annulation ou déplacement
- Bénévoles : liste, nouveaux bénévoles signalés, suppression sur demande
- Saison : dates, besoins, horaires, soirées fermées, adresse et consignes
- Export PDF et CSV, pour l'affichage sur place et le plan B
- Journal d'audit des actions des coordinateurs

**E-mails automatiques** :

| Pour | E-mail | Quand |
|---|---|---|
| Bénévole | Lien de connexion | À la demande |
| Bénévole | Confirmation d'inscription ou de désinscription | Un seul e-mail par session |
| Bénévole | Rappel | La veille à 18 h (hypothèse) |
| Coordinateurs | Récap des 7 prochains jours (sert aussi de plan B) | Chaque jour à 7 h |
| Coordinateurs | Soirée devenue incomplète | Immédiatement |
| Coordinateurs | Nouveau bénévole | À chaque première inscription |

L'offre gratuite de Brevo permet 300 e-mails par jour. Avec une vingtaine de bénévoles, c'est largement suffisant.

## 7. Données personnelles (RGPD)

- **Responsable** : l'association (nom à préciser).
- **Données** : nom, prénom, pseudo, e-mail, téléphone, inscriptions, accord de conservation, horodatages. Aucun champ libre, pour éviter la saisie de données sensibles, sauf le pseudo, étroitement borné : 3 à 20 caractères, lettres latines (accents compris), chiffres, tiret et souligné. Tous les bénévoles sont majeurs. Rien sur les personnes accueillies.
- **Suppression à la demande** : depuis « Mon compte », par anonymisation de la ligne du bénévole ([ADR-0008](adr/0008-annulation-logique-et-anonymisation.md)) ; ses liens de connexion en cours et ses sessions sont supprimés. Les sauvegardes antérieures en gardent une copie jusqu'à leur expiration (à préciser dans la notice).
- **Création du compte** : à la première connexion par lien magique, avec l'e-mail seul (le contrôle de l'adresse est prouvé) ; nom, prénom et téléphone suivent au formulaire de première connexion. Un compte resté sans profil sera supprimé par la purge (S5). Sessions stockées en base pendant 30 jours au plus.
- **Visibilité** : le public voit les pseudos et les soirées où ils sont inscrits, rien d'autre ; le bénévole voit ses propres données ; les coordinateurs voient tout. Le bénévole est informé du caractère public de son pseudo par l'aide du champ, puis par la notice (S5).
- **Base légale** : intérêt légitime de l'association pour organiser l'accueil (à confirmer). La conservation entre saisons repose sur le consentement, retirable à tout moment.
- **Durées proposées** : sans accord, suppression un mois après la fin de saison ; avec accord, jusqu'au retrait et au plus 3 ans sans participation ; journaux techniques, 6 mois.
- **Sous-traitants à citer dans la notice** : Cloudflare (réseau, tunnel, Access, Turnstile) et Brevo (e-mails). Hébergement sur un serveur personnel situé en France.
- **Livrables** : notice d'information, fiche du registre des traitements.

## 8. Exigences non fonctionnelles

**Sécurité** :

- Cloudflare Tunnel sortant, aucun port entrant ; WAF, limitation de débit, HSTS
- Lien magique via Spring Security (jeton à usage unique de 15 minutes, validé en POST). Seule l'empreinte SHA-256 du jeton est stockée, et sa consommation est atomique. La réponse est la même que le compte existe ou non ; les demandes sont limitées par e-mail (3 par 15 minutes) et par IP (10 par heure), en mémoire pour une instance unique, en plus de Turnstile.
- Cookie `__Host-`, HttpOnly, Secure, SameSite=Lax ; nouvel identifiant de session à la connexion ; protection CSRF active
- Autorisation vérifiée côté serveur sur chaque action, avec des tests d'accès croisé
- Back-office protégé par le rôle coordinateur et par Cloudflare Access sur `/admin/*`, avec des sessions de 8 h
- CSP stricte sans script inline (htmx servi localement), `frame-ancestors 'none'`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`
- Conteneurs non-root, système de fichiers en lecture seule, aucune capability, limites CPU et mémoire, réseau Docker dédié ; base et endpoints de supervision jamais exposés
- Secrets hors du dépôt, dans un fichier d'environnement sur le serveur, illisibles par Claude Code (règles deny)
- En CI : Dependabot, revue des dépendances et scan de l'image. Pas de runner auto-hébergé sur un dépôt public ; déploiement en pull depuis le homelab, sans clé du homelab dans GitHub.
- Aucune donnée personnelle dans les logs applicatifs ; audit des actions des coordinateurs ; modèle de menaces (STRIDE allégé) documenté avant l'ouverture

**Disponibilité et continuité** :

- Sonde HTTP externe toutes les 5 minutes, avec alerte sur téléphone
- Sauvegardes quotidiennes chiffrées hors site, restauration testée avant l'ouverture
- Objectifs : coupure de moins de 4 h (RTO), perte de moins de 24 h de données (RPO)
- Plan B : récap quotidien et export PDF chez les coordinateurs, Google Form de l'an dernier prêt à être réactivé, runbook « site indisponible »

**Ergonomie et accessibilité** : mobile d'abord, grandes zones tactiles, français simple, objectif WCAG 2.2 AA, pages légères sans framework JavaScript lourd.

**Qualité** : tests unitaires des règles R1 à R7, tests d'intégration sur un vrai PostgreSQL (Testcontainers), tests de sécurité, quelques tests de bout en bout du parcours d'inscription. CI bloquante, migrations versionnées.

## 9. Architecture et stack

Navigateur → Cloudflare (DNS, WAF, Turnstile, Access sur `/admin/*`) → tunnel sortant → cloudflared → app Spring Boot → PostgreSQL. L'appli envoie les e-mails via Brevo, et la base est sauvegardée chiffrée hors site. L'appli tourne sur le homelab, à côté du cv-assistant mais sur son propre réseau Docker. Seul cloudflared parle à l'appli, et seule l'appli parle à la base.

| Couche | Choix |
|---|---|
| Application | Java 21 (LTS, la version fournie dans les sessions cloud de Claude Code), Spring Boot 4.1, Spring Security 7, Spring Data JPA, Thymeleaf + htmx, Maven |
| Données | PostgreSQL, migrations Flyway |
| E-mails | Brevo, domaine d'envoi authentifié (SPF, DKIM, DMARC) sans toucher aux MX existants de `marcantoine-meunier.com` |
| CI/CD | Dépôt GitHub public, GitHub Actions, image publiée sur GHCR, déploiement en pull |

Les décisions d'architecture sont consignées dans [docs/adr](adr/README.md).

## 10. Hors périmètre du MVP

Rappels par SMS (payants), export iCal, messagerie relayée entre bénévoles d'une même soirée, statistiques de saison, notifications push, plusieurs lieux d'accueil.

## 11. Questions ouvertes

1. Une seule inscription par bénévole et par soirée ? Appliqué en S3, garanti par une contrainte en base. Décidé le 25 octobre 2026 : l'exception décidée par un coordinateur fera l'objet d'une migration en S4.
2. Dates de la saison 2026-2027, adresse et consignes du lieu. En attendant, une saison d'exemple aux dates fictives (4 janv. → 21 mars 2027 au matin) est en place, signalée comme telle sur la page publique ; elle sera remplacée depuis le back-office (S4).
3. Nom exact de l'association, pour la notice et l'expéditeur des e-mails.
4. Durées de conservation proposées dans la section RGPD.
5. Durée de connexion des bénévoles (30 jours, appliquée en S3 et modifiable) et heure du rappel (18 h).
6. L'adresse du lieu reste-t-elle absente de la page publique ? Par défaut, oui : la page publique (S2) ne l'affiche pas, et le modèle de données ne la stocke pas encore.
7. La notice d'information (S5) doit mentionner le pseudo public : son affichage sur le planning, visible par tous, sa finalité et la façon d'en changer.
8. Modification du pseudo : le bénévole la fait dans « Mon compte » depuis S4a. Reste pour S4b : la modification par un coordinateur, pour régler un pseudo usurpé.
9. Désinscription : l'activité de R6 est le soir J pour tous les types, petit-déjeuner compris (décision proposée en S4a, à confirmer avec les coordinateurs). La suppression d'un compte annule aussi les inscriptions de la soirée même ou du lendemain, après la limite de R6 : la page de confirmation demande de prévenir les coordinateurs.

## 12. Méthode de travail avec Claude

- **Projet claude.ai** : le cahier des charges et les échanges de conception y sont partagés, pour que chaque conversation parte du même contexte.
- **Claude Code dans le dépôt** : un [CLAUDE.md](../CLAUDE.md) (stack, commandes, conventions, règles de sécurité) ; pour chaque fonctionnalité, explorer, planifier, puis coder en écrivant les tests d'abord ; `/security-review` avant chaque merge ; règles deny sur les secrets.
- **Sessions cadrées** : chaque session a son prompt dans [docs/prompts](prompts/README.md), avec ses livrables et ses critères de fin.
- **Revue** : une PR par fonctionnalité, chaque diff relu.
- **Rituel** : une revue de jalon chaque semaine, avec mise à jour de ce document.

## Sources

- [Cloudflare Turnstile – plans](https://developers.cloudflare.com/turnstile/plans/)
- [Spring Security – One-Time Token Login](https://docs.spring.io/spring-security/reference/servlet/authentication/onetimetoken.html)
- [Spring Boot – versions supportées](https://isitpatched.com/spring-boot)
- [Claude Code – sessions cloud](https://code.claude.com/docs/en/claude-code-on-the-web.md)
- [Claude Code – environnements cloud](https://code.claude.com/docs/en/cloud-environments.md)
- [Claude Code – bonnes pratiques](https://code.claude.com/docs/en/best-practices.md)
