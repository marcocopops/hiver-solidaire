# Hiver Solidaire — Cahier des charges du MVP

*Version 0.7 · 9 novembre 2026 (plusieurs postes par personne et récupération, lot 3) · Marc-Antoine Meunier · statut : à valider*

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

- **Visiteur** : voit le planning des journées à venir, leur statut et, par poste, les pseudos des inscrits et les places « À pourvoir ». Aucune autre donnée personnelle : ni nom, ni e-mail, ni téléphone. Les pages demandent aux moteurs de recherche de ne pas les indexer (`noindex`).
- **Bénévole** : se connecte par lien magique, s'inscrit, se désinscrit, consulte et supprime ses données. Connecté, il voit en plus sur le planning « Vous » à la place de son pseudo, ce qu'il fait, de quoi se désinscrire, et un filtre sur ses propres journées ; rien sur les autres.
- **Coordinateur (2 à 3)** : a un accès complet au planning, aux bénévoles et au paramétrage, par le back-office (`/admin`). C'est un bénévole dont l'adresse figure dans la configuration du serveur (`APP_COORDINATOR_EMAILS`, [ADR-0010](adr/0010-coordinateurs-par-variable-d-environnement.md)) : il reçoit le rôle à la connexion par lien magique et peut aussi s'inscrire comme les autres.
- **Personnes accueillies** : aucune donnée les concernant n'est saisie.

## 5. Modèle métier

L'unité des données et des règles est la soirée : du soir J (dîner) au matin J+1 (petit-déjeuner). La saison 2025-2026 en comptait 76.

Le planning s'affiche par **journée calendaire** ([ADR-0009](adr/0009-affichage-par-journee-donnees-par-soiree.md)) : « Lun. 4 janv. » (« Lun. 1er févr. » le premier du mois). La carte du jour D montre le petit-déjeuner du matin D (soirée D-1), puis le plat principal, les accompagnements, les présences au dîner et la nuit du soir D (soirée D). Le premier jour de la saison n'a pas de petit-déjeuner ; le lendemain de la dernière soirée n'a que lui. Ailleurs (« Mes inscriptions », confirmations, désinscription), une inscription se désigne par le jour de son activité : « Petit-déjeuner du mar. 5 janv. », « Dîner du lun. 4 janv. », « Dîner et nuit du lun. 4 janv. ».

Une saison est définie par sa première et sa dernière soirée (dates du soir J) et par la date d'ouverture des inscriptions. Ses soirées sont stockées en base, une ligne par soirée, générées à partir de ces dates ([ADR-0006](adr/0006-soirees-stockees-plutot-que-calculees.md)).

**Besoins par soirée** (minimum et maximum paramétrables ; les horaires sont des textes fixes de l'appli, non saisis dans le back-office) :

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
| Récupérer le dîner et le partager | 1 présence + récupération d'un repas préparé chez soi (seulement s'il en reste un à récupérer, R4) |
| Passer la nuit et partager le dîner | 1 nuit (ne compte pas dans les 2 présences au dîner) |
| Fournir et partager le petit-déjeuner | 1 petit-déjeuner |

**Comptage** (lot 3) : chaque inscription compte pour les postes qu'elle couvre. Une personne qui prépare et partage le plat **et** les accompagnements a deux inscriptions « préparer et partager » : elle compte pour deux présences au dîner. En pratique, c'est un couple qui vient ; les coordinateurs vérifient auprès d'eux, et la fiche journée du back-office le signale. Le bénévole de nuit ne compte toujours pas dans les présences.

**Règles** :

- **R1 Capacité** : une inscription n'est acceptée que s'il reste de la place sur chaque poste qu'elle couvre. La base le garantit par une transaction avec verrou sur la soirée : jamais deux personnes sur la dernière place.
- **R2 Unicité** : un poste par bénévole et par soirée ([ADR-0011](adr/0011-un-poste-par-benevole-et-par-soiree.md), lot 3). Le poste d'une inscription est son plat pour qui prépare (chez soi ou en partageant), la récupération, la nuit ou le petit-déjeuner. Un bénévole cumule les postes distincts d'une même soirée : passer la nuit, préparer le plat principal, préparer les accompagnements, fournir le petit-déjeuner du lendemain. Il ne prend jamais deux fois le même (pas deux nuits, pas deux plats principaux). « Récupérer le dîner et le partager » ne se cumule qu'avec le petit-déjeuner : avec sa propre préparation chez soi, ce serait « préparer et partager » ; avec la nuit, le bénévole de nuit compterait dans les présences (§2) ; avec « préparer et partager », une personne seule compterait deux présences (décision du 9 novembre 2026, à confirmer avec les coordinateurs, §11 question 1). La base garantit « jamais deux fois le même poste » ; la règle de la récupération est vérifiée sous le verrou de la soirée, comme R1.
- **R3 Préparation** : qui prépare choisit plat principal ou accompagnements, un de chaque par soirée.
- **R4 Récupération** : chaque « préparer chez soi » doit avoir un « récupérer et partager » distinct le même soir (deux repas préparés chez soi demandent deux récupérations) ; sinon, les coordinateurs sont alertés et organisent la remise. « Récupérer et partager » n'est proposé, et accepté, que s'il existe une préparation chez soi qui n'a pas encore sa récupération (lot 3) : une préparation chez soi en ouvre une, une deuxième en ouvre une deuxième. Le nombre de récupérations d'une soirée ne dépasse donc jamais celui des préparations chez soi (vérifié sous le verrou de la soirée), et les présences au dîner gardent leur maximum (R1). L'annulation d'une préparation chez soi déjà récupérée est acceptée, qu'il s'agisse d'une désinscription, d'une annulation ou d'un déplacement par un coordinateur, ou d'une suppression du compte. La récupération reste et compte toujours une présence au dîner, mais n'a plus de repas : les coordinateurs sont alertés pour prévenir la personne (décision du 9 novembre 2026, à confirmer avec les coordinateurs). Une nouvelle préparation chez soi le même soir la rattache d'elle-même.
- **R5 Statut** : une journée est « Complète » (en vert) si tous les minimums des postes de la journée sont couverts, sinon « À compléter » (en orange), avec ou sans inscrits ; la couleur est toujours accompagnée du texte. Les minimums sont ceux de chaque soirée : le petit-déjeuner de la soirée D-1, le reste de la soirée D.
- **R6 Désinscription** : en autonomie jusqu'à la veille de l'activité à 23 h 59, heure de Paris. L'activité est le soir J pour tous les types, petit-déjeuner du matin J+1 compris : la soirée est l'unité des règles, et un désistement à minuit pour un petit-déjeuner à 7 h ne laisserait pas le temps de trouver quelqu'un. Pour le petit-déjeuner affiché sur la carte du jour D (soirée D-1), la limite tombe donc le jour D-2 à 23 h 59 : l'appli affiche toujours la date limite calculée (« Possible jusqu'au jeu. 7 janv. à 23 h 59 »), jamais une formule. Ensuite, l'appli affiche le contact des coordinateurs saisi dans les réglages de la saison, un numéro de téléphone français ou une adresse e-mail (« Pour vous désinscrire, contactez les coordinateurs via … », en lien `tel:` ou `mailto:`). Il n'y a pas de repli par la configuration du serveur : si la saison n'a pas de contact, l'appli affiche « Contactez un coordinateur. », sans lien, et le tableau de bord du back-office le signale. Un coordinateur peut encore annuler l'inscription (back-office). Une désinscription n'efface pas l'inscription : elle la marque annulée, et une inscription annulée ne compte plus dans R1 à R5 ([ADR-0008](adr/0008-annulation-logique-et-anonymisation.md)).
- **R7 Fenêtre** : inscriptions possibles de l'ouverture à la fin de saison, jamais sur une soirée passée. Une soirée est passée à partir de minuit le lendemain, heure de Paris : on peut encore s'inscrire le jour même. Il n'y a pas de soirée fermée (supprimée en S4, ADR-0009). Le planning n'affiche pas les journées passées : la carte du jour D disparaît à minuit le lendemain ; le petit-déjeuner du jour même y reste affiché, mais n'est plus proposé (sa soirée, celle de la veille, est passée).

## 6. Parcours

**Navigation** (lot 4) : un menu « burger » dans l'en-tête de toutes les pages, sans JavaScript (`<details>`), l'entrée de la page en cours marquée (`aria-current="page"`). Visiteur : Planning, Me connecter (absent des pages de connexion). Bénévole : Planning, Mes inscriptions, Mon compte, Se déconnecter (POST). Coordinateur : les mêmes entrées, plus Tableau de bord, Bénévoles, Saison et Exports en prune, avant la déconnexion. Les pages du back-office ont un en-tête prune avec la mention « Accès coordinateur ».

**Accueil** (lot 4) : présentation des trois façons de participer (petit-déjeuner, dîner, nuit) et bouton vers le planning. Les dates viennent de la saison. L'organisateur (« Site d'inscription à Hiver Solidaire … ») et le contact des coordinateurs (« En cas de question… », en lien `tel:` ou `mailto:`) viennent des réglages de la saison, jamais du dépôt ; vide, la phrase n'est pas affichée. Le contact des coordinateurs est donc public sur cette page (décision du 9 novembre 2026) ; le planning public ne l'affiche toujours pas.

**Planning** : une carte par journée, repliable (`<details>`), dépliée si la journée est à compléter. Chaque poste est listé dans l'ordre de la journée avec les pseudos des inscrits et une place « À pourvoir » par bénévole manquant jusqu'au minimum ; la ligne « Nuit » précise « jusqu'au matin ». Des filtres en haut de page, en GET et sans JavaScript : « Toutes », « À compléter » et, pour un bénévole connecté, « Les miennes » (journées où il a une inscription active) ; une valeur inconnue revient à « Toutes ». Sur une journée où il est inscrit, le bénévole voit « Vous » à la place de son pseudo, une phrase par inscription qui dit ce qu'il fait (« Vous passez la nuit ») et, par inscription, un bouton « Me désinscrire » qui nomme le poste quand la carte en porte plusieurs (« Me désinscrire du petit-déjeuner », « … du plat principal », « … de la nuit »), avec la date limite exacte ; après la limite, le contact des coordinateurs. Un bénévole qui tient deux présences au dîner apparaît une seule fois sur la ligne, suivi de « (2 places) ». Pendant la saison d'exemple, un bandeau signale que les dates sont à confirmer.

**S'inscrire** :

1. Le bénévole choisit une journée (« Je participe ») et un type d'inscription encore disponible. Le petit-déjeuner s'inscrit sur la soirée de la veille, le reste sur la soirée du jour ; R1 à R4 et R7 s'appliquent soirée par soirée, et R2 poste par poste : un bénévole qui passe la nuit peut aussi préparer le plat ou les accompagnements, ou fournir le petit-déjeuner. « Je participe » ne propose que ce qui reste possible pour lui : ni un poste qu'il tient déjà, ni « Récupérer » sans repas préparé chez soi à récupérer. Le même contrôle est refait à la confirmation, sous le verrou de la soirée.
2. Il saisit son e-mail dans un formulaire protégé par Turnstile. Le message affiché est le même, que l'adresse soit connue ou non.
3. Il ouvre le lien reçu (usage unique, valable 15 minutes) et appuie sur « Me connecter ». La connexion se fait en POST, car les filtres des messageries ouvrent les liens et consommeraient le jeton. Le lien porte aussi le choix de l'étape 1 (journée, type, plat, sans donnée personnelle) : il fonctionne sur un autre appareil que celui de la demande.
4. La première fois, il donne son nom, son prénom, un **pseudo public** et son téléphone (obligatoire ; indicatif +33 par défaut, numéros étrangers acceptés, enregistré au format international). Le pseudo est proposé sous la forme prénom + initiale du nom (« MarieD ») et modifiable ; l'aide du champ dit qu'il est visible par tous sur le planning et qu'on peut choisir autre chose que son prénom. Il est unique sans tenir compte de la casse : en cas de doublon, une variante est proposée (« MarieD2 »). Une case « Me recontacter la saison prochaine », décochée par défaut, et un lien vers la notice d'information complètent le formulaire.
5. Il confirme ; un e-mail récapitule la date, les horaires (textes fixes, tableau du §5) et le contact des coordinateurs.
6. Il reste connecté 30 jours sur cet appareil (hypothèse) : session stockée en base, 30 jours au plus après la connexion, même utilisée chaque jour ([ADR-0007](adr/0007-session-jdbc-plutot-que-se-souvenir-de-moi.md)).

L'appli ne stocke ni l'adresse du lieu ni les consignes : les bénévoles les connaissent déjà (décision du lot 2), et elles n'apparaissent donc nulle part, ni sur la page publique ni dans l'e-mail.

**Se désinscrire** : depuis la carte de la journée sur le planning, « Mes inscriptions » ou le lien de l'e-mail (S5), jusqu'à la veille de la soirée à 23 h 59 (R6). L'inscription est désignée par sa soirée et son choix (`soiree`, `choix`) : chaque poste se désinscrit seul, les autres postes du bénévole sur la soirée restent. Le lien ouvre une page de confirmation, et l'action se fait en POST. Pour un repas préparé chez soi qu'une personne doit venir récupérer, la page de confirmation le dit, et la désinscription alerte les coordinateurs (R4). La place libérée est immédiatement reprenable. Après la limite, le bouton disparaît et l'appli affiche le contact des coordinateurs. Si une soirée des 7 prochains jours (aujourd'hui compris) perd un minimum, les coordinateurs sont alertés : l'alerte est enregistrée dès S4 et envoyée par e-mail à partir de S5. L'alerte « récupération sans repas » (R4, lot 3) vaut pour toute soirée à venir.

**Mon compte** : le bénévole voit ses données (e-mail, nom, prénom, pseudo, téléphone, accord de recontact), modifie son téléphone et son pseudo (mêmes règles qu'au premier profil), donne ou retire son accord de conservation, ou supprime son compte. L'e-mail n'est pas modifiable dans cette version ; nom et prénom se corrigent auprès d'un coordinateur. La suppression, après une page de confirmation, annule toutes ses inscriptions dont la soirée n'est pas passée (y compris après la limite de R6, avec l'alerte éventuelle) et anonymise son historique : plus aucun e-mail, nom, prénom, pseudo ni téléphone en base ; ses inscriptions passées restent comptées sans être rattachables à une personne ; il est déconnecté de tous ses appareils. Une nouvelle connexion avec la même adresse crée un nouveau compte.

**Back-office des coordinateurs** (`/admin`, livré en S4b) : réservé au rôle coordinateur, vérifié par l'appli sur chaque route, derrière Cloudflare Access (§8). Pensé d'abord pour l'ordinateur, utilisable sur téléphone. Chaque action passe par une page de confirmation (GET sans effet), puis un POST protégé par CSRF, et s'inscrit au journal d'audit.

- **Tableau de bord** : un avertissement en tête si la saison n'a pas de contact des coordinateurs (R6), avec un lien vers « Saison » ; puis les journées à compléter des 14 prochains jours, avec ce qui manque par poste ; les alertes enregistrées non traitées (« Marquer comme traitée ») ; les repas préparés chez soi sans récupération et les récupérations restées sans repas (R4) ; les nouveaux bénévoles à signaler (§2), jusqu'à ce qu'un coordinateur les marque « vus ».
- **Fiche journée** : les deux soirées de la journée (ADR-0009), le statut de chaque poste et les inscrits avec nom, prénom, pseudo, téléphone et e-mail ; un repas préparé chez soi sans récupération, une récupération sans repas (R4) et un bénévole qui compte pour deux présences au dîner (« vérifiez qu'ils viennent à deux ») y sont signalés. Actions :
  - annuler une inscription, même après la limite de R6, jamais sur une soirée passée ; l'alerte « soirée devenue incomplète » est enregistrée comme pour un bénévole ;
  - déplacer une inscription vers une autre journée, avec le même type et le même plat : la soirée cible passe par R1 à R4 et R7 (et R2 poste par poste pour ce bénévole), les deux soirées sont verrouillées ; l'ancienne inscription est annulée, une nouvelle est créée, et la soirée quittée a les mêmes alertes qu'une désinscription ;
  - inscrire un bénévole existant, mêmes règles qu'en ligne ;
  - créer un **bénévole sans e-mail**, inscrit par téléphone : nom, prénom, pseudo et téléphone, mêmes règles qu'au premier profil. Il ne peut pas se connecter et apparaît sur le planning par son pseudo, comme les autres.
- **Bénévoles** : liste avec recherche (nom, prénom, pseudo, e-mail, téléphone), nouveaux bénévoles signalés, fiche avec ses inscriptions, correction du nom, du prénom et du pseudo (pseudo usurpé, §11 question 8), suppression sur demande du bénévole, par la même anonymisation que « Mon compte » (ADR-0008). Un coordinateur supprime son propre compte depuis « Mon compte », pas depuis le back-office.
- **Saison** : une seule saison active à la fois. Réglages : première et dernière soirée, ouverture des inscriptions, besoins minimum et maximum par poste, contact des coordinateurs (un numéro de téléphone français ou une adresse e-mail, 254 caractères au plus, vérifié par le serveur ; vide : « Contactez un coordinateur. » sans lien, signalé au tableau de bord), organisateur affiché sur l'accueil (nom de la paroisse ou de l'association, une ligne de 100 caractères au plus ; vide : non affiché, lot 4). Les soirées sont générées à partir des dates (ADR-0006) ; un changement de dates qui supprimerait une soirée ayant des inscriptions actives est refusé, avec les dates en cause. Créer la saison remplace la saison d'exemple, seulement si celle-ci n'a aucune inscription active ; sinon l'écran l'indique. La saison n'a pas de nom affiché ; l'adresse du lieu et les consignes ne sont pas saisies (lot 2), ni les horaires, fixes dans l'appli (colonne retirée après le lot 4).
- **Exports** (lot 5) : une page sans texte explicatif. En haut, les dates « Du » et « Au » (par défaut les 7 prochains jours, 31 jours au plus) et deux boutons : « Ouvrir la page imprimable » (planning de la période, que le navigateur enregistre en PDF : CSS d'impression, aucune bibliothèque PDF) et « Exporter au format CSV » (séparateur « ; », cellules neutralisées contre l'injection de formule). En dessous, le tableau de la page imprimable pour la période, mis à jour par htmx dès qu'une date change (bouton « Afficher » sans JavaScript). Le tableau, la page imprimable et le CSV donnent nom, pseudo, téléphone et e-mail. La page imprimable et le CSV sont des POST audités ; le tableau à l'écran est une consultation, comme la fiche d'un bénévole, non auditée.
- **Journal d'audit** : qui (identifiant technique du coordinateur), quand, quelle action, sur quoi (identifiants, période d'un export). Aucun nom, e-mail ni téléphone ; table en ajout seul.

**E-mails automatiques** :

| Pour | E-mail | Quand |
|---|---|---|
| Bénévole | Lien de connexion | À la demande |
| Bénévole | Confirmation d'inscription ou de désinscription | Un seul e-mail par session |
| Bénévole | Rappel | La veille à 18 h (hypothèse) |
| Coordinateurs | Récap des 7 prochains jours (sert aussi de plan B) | Chaque jour à 7 h |
| Coordinateurs | Soirée devenue incomplète | Immédiatement |
| Coordinateurs | Récupération sans repas (préparation chez soi annulée, R4) | Immédiatement |
| Coordinateurs | Nouveau bénévole | À chaque première inscription |

L'offre gratuite de Brevo permet 300 e-mails par jour. Avec une vingtaine de bénévoles, c'est largement suffisant.

## 7. Données personnelles (RGPD)

- **Responsable** : l'association (nom à préciser).
- **Données** : nom, prénom, pseudo, e-mail, téléphone, inscriptions, accord de conservation, horodatages. Aucun champ libre, pour éviter la saisie de données sensibles, sauf le pseudo, étroitement borné : 3 à 20 caractères, lettres latines (accents compris), chiffres, tiret et souligné. Tous les bénévoles sont majeurs. Rien sur les personnes accueillies.
- **Suppression à la demande** : depuis « Mon compte », par anonymisation de la ligne du bénévole ([ADR-0008](adr/0008-annulation-logique-et-anonymisation.md)) ; ses liens de connexion en cours et ses sessions sont supprimés. Les sauvegardes antérieures en gardent une copie jusqu'à leur expiration (à préciser dans la notice).
- **Création du compte** : à la première connexion par lien magique, avec l'e-mail seul (le contrôle de l'adresse est prouvé) ; nom, prénom et téléphone suivent au formulaire de première connexion. Un compte resté sans profil sera supprimé par la purge (S5). Sessions stockées en base pendant 30 jours au plus (8 h pour un coordinateur). Un coordinateur peut aussi créer le compte d'un bénévole qui s'inscrit par téléphone : nom, prénom, pseudo et téléphone, sans e-mail.
- **Visibilité** : le public voit les pseudos et les soirées où ils sont inscrits, rien d'autre ; le bénévole voit ses propres données ; les coordinateurs voient tout. Les exports CSV et imprimés contiennent des coordonnées : à ne garder que le temps nécessaire, à détruire après usage. Le bénévole est informé du caractère public de son pseudo par l'aide du champ, puis par la notice (S5).
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
- Back-office protégé par le rôle coordinateur (`APP_COORDINATOR_EMAILS`, ADR-0010), vérifié par l'appli sur chaque route `/admin/**`, et par Cloudflare Access sur `/admin/*`, configuré chez Cloudflare (`deploy/README.md`) ; l'appli ne fait pas confiance à Access (défense en profondeur). Session coordinateur de 8 h après la connexion, puis reconnexion ; celle d'un bénévole reste de 30 jours
- CSP stricte sans script inline (htmx servi localement), `frame-ancestors 'none'`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`
- Conteneurs non-root, système de fichiers en lecture seule, aucune capability, limites CPU et mémoire, réseau Docker dédié ; base et endpoints de supervision jamais exposés
- Secrets hors du dépôt, dans un fichier d'environnement sur le serveur, illisibles par Claude Code (règles deny)
- En CI : Dependabot, revue des dépendances et scan de l'image. Pas de runner auto-hébergé sur un dépôt public ; déploiement en pull depuis le homelab, sans clé du homelab dans GitHub.
- Aucune donnée personnelle dans les logs applicatifs ; audit des actions des coordinateurs ; modèle de menaces (STRIDE allégé) documenté avant l'ouverture

**Disponibilité et continuité** :

- Sonde HTTP externe toutes les 5 minutes, avec alerte sur téléphone
- Sauvegardes quotidiennes chiffrées hors site, restauration testée avant l'ouverture
- Objectifs : coupure de moins de 4 h (RTO), perte de moins de 24 h de données (RPO)
- Plan B : récap quotidien et planning imprimable d'une période choisie, les 7 prochains jours par défaut (enregistré en PDF par le navigateur) chez les coordinateurs, Google Form de l'an dernier prêt à être réactivé, runbook « site indisponible »

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

1. ~~Une seule inscription par bénévole et par soirée ?~~ Close (lot 3) : R2 porte désormais sur le poste ([ADR-0011](adr/0011-un-poste-par-benevole-et-par-soiree.md)). Un bénévole cumule les postes distincts d'une soirée, et l'exception « sur décision d'un coordinateur » (décidée le 25 octobre 2026, jamais livrée) est sans objet pour le cumul de postes. La désinscription, « Mes inscriptions » et le calcul des alertes raisonnent par inscription. Restent à confirmer avec les coordinateurs : « Récupérer » cumulable seulement avec le petit-déjeuner ; désinscription acceptée, avec alerte, d'une préparation chez soi déjà récupérée ; dîner du jour D cumulable avec le petit-déjeuner du lendemain (même soirée), refusé avant le lot 3.
2. Dates de la saison 2026-2027. Une saison d'exemple aux dates fictives (4 janv. → 21 mars 2027 au matin) est en place. Depuis S4b, un coordinateur la remplace depuis le back-office (« Saison ») en saisissant les vraies dates et le contact des coordinateurs, à condition qu'elle n'ait aucune inscription active. Restent à connaître : les dates réelles. L'adresse et les consignes du lieu ne sont plus demandées (lot 2).
3. Nom exact de l'association, pour la notice et l'expéditeur des e-mails.
4. Durées de conservation proposées dans la section RGPD.
5. Durée de connexion des bénévoles (30 jours, appliquée en S3 et modifiable) et heure du rappel (18 h).
6. ~~L'adresse du lieu reste-t-elle absente de la page publique ?~~ Close (lot 2) : l'appli ne stocke plus l'adresse du lieu, elle n'apparaît nulle part.
7. La notice d'information (S5) doit mentionner le pseudo public : son affichage sur le planning, visible par tous, sa finalité et la façon d'en changer.
8. Modification du pseudo : le bénévole la fait dans « Mon compte » depuis S4a ; un coordinateur la fait depuis la fiche du bénévole depuis S4b (pseudo usurpé), avec le nom et le prénom, mêmes règles et même unicité.
9. Désinscription : l'activité de R6 est le soir J pour tous les types, petit-déjeuner compris (décision proposée en S4a, à confirmer avec les coordinateurs). Avec l'affichage par journée (S4), la limite du petit-déjeuner du jour D tombe le jour D-2 à 23 h 59 : à confirmer aussi. La suppression d'un compte annule aussi les inscriptions de la soirée même ou du lendemain, après la limite de R6 : la page de confirmation demande de prévenir les coordinateurs.

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
