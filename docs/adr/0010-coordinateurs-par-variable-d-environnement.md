# ADR-0010 — Coordinateurs désignés par variable d'environnement

- **Statut** : proposé
- **Date** : 2026-11-04

## Contexte

Le back-office (S4b, [cahier des charges](../cahier-des-charges.md) §6) est réservé à 2 ou 3 coordinateurs (§4). Il donne accès à toutes les coordonnées des bénévoles et à des actions d'administration : c'est l'actif A2 du [modèle de menaces](../securite/modele-de-menaces.md) (M6, M7). Il faut décider comment l'appli sait qu'une personne est coordinatrice.

Contraintes :

- la connexion passe uniquement par le lien magique (ADR-0003) : il n'y a ni mot de passe ni compte « admin » à part ;
- un coordinateur reste un bénévole (il s'inscrit comme les autres) ;
- aucune adresse réelle dans le dépôt (CLAUDE.md) ;
- Cloudflare Access protège `/admin/*` (§8), mais l'appli ne doit pas dépendre de lui (défense en profondeur, M7) ;
- la liste change rarement (une fois par saison) et la gère le porteur du projet, qui a accès au serveur.

## Options envisagées

1. **Variable d'environnement `APP_COORDINATOR_EMAILS`** (liste d'adresses) lue au démarrage ; le rôle est attribué à la connexion par lien magique si l'adresse du lien consommé est dans la liste.
   - Avantages : rien en base, aucun écran pour élever les droits, donc pas d'élévation de privilèges par l'appli elle-même (un coordinateur compromis ne peut pas en créer d'autres) ; la liste vit à côté des autres secrets du serveur, hors du dépôt ; simple à tester (liste remplacée en test) ; l'adresse est prouvée par le lien magique.
   - Inconvénients : changer la liste demande un accès au serveur et un redémarrage ; le retrait n'est effectif qu'à la prochaine connexion, au plus tard 8 h après (durée d'une session coordinateur), sauf suppression manuelle des sessions ; la liste doit être tenue à jour en double avec la politique Cloudflare Access.
2. **Rôle en base** (colonne `volunteer.coordinator` ou table de rôles), géré depuis le back-office.
   - Avantages : modifiable sans redémarrage, depuis l'appli ; retrait immédiat possible.
   - Inconvénients : il faut un premier coordinateur (migration ou commande manuelle avec une adresse réelle, hors dépôt) ; un écran qui donne le rôle est une cible de choix (un coordinateur compromis en crée d'autres, persistance après la reprise du compte) ; plus de code et de tests de sécurité.
3. **Faire confiance à Cloudflare Access** : l'appli lit l'en-tête `Cf-Access-Jwt-Assertion` et vérifie le jeton (clé publique de l'équipe Access), le groupe Access fait office de rôle.
   - Avantages : une seule liste, gérée dans Cloudflare ; authentification forte possible (fournisseur d'identité).
   - Inconvénients : l'appli dépend d'Access pour l'autorisation, contraire à la défense en profondeur demandée (M7 : un accès qui contourne Cloudflare ne doit rien ouvrir) ; vérification de JWT à écrire et à maintenir ; tests locaux sans Access plus compliqués.

## Décision

Option 1 : `APP_COORDINATOR_EMAILS`. C'est la plus simple, et elle n'offre aucun moyen d'obtenir le rôle depuis l'appli. Pour 2 à 3 coordinateurs qui changent une fois par saison, redémarrer l'appli n'est pas une gêne.

Mise en œuvre :

- `auth.CoordinatorEmails` lit la liste (séparée par des virgules), normalise chaque adresse comme à la connexion et refuse de démarrer sur une entrée invalide, sans la recopier dans le message ;
- à la consommation du lien magique, le principal (`VolunteerPrincipal`) reçoit `coordinator = true` si l'adresse est dans la liste : rôles `VOLUNTEER` et `COORDINATOR`. Le principal ne contient toujours que l'identifiant technique et ce booléen, jamais l'adresse ;
- `SecurityConfig` exige le rôle `COORDINATOR` sur `/admin` et `/admin/**`, toutes méthodes ;
- la session d'un coordinateur dure 8 h après la connexion (`SessionLifetimeFilter`, ADR-0007), celle d'un bénévole 30 jours ;
- Cloudflare Access sur `/admin/*` reste une barrière en amont, configurée chez Cloudflare (`deploy/README.md`), sans que l'appli s'y fie.

## Conséquences

- Retirer un coordinateur : enlever son adresse, redémarrer, et supprimer ses sessions pour un effet immédiat (`deploy/README.md`). Sans cela, il garde le rôle jusqu'à 8 h.
- Ajouter un coordinateur qui est déjà connecté comme bénévole : il obtient le rôle à sa prochaine connexion.
- Si les coordinateurs devaient changer souvent, ou être plus nombreux, l'option 2 redeviendrait intéressante : elle demanderait alors un ADR qui remplace celui-ci.
- Le journal d'audit identifie le coordinateur par son identifiant technique : retrouver la personne demande la table `volunteer`, ce qui suffit à 2 ou 3 coordinateurs.
