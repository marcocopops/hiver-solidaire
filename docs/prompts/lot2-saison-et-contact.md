# Lot 2 — Saison simplifiée et « contact des coordinateurs »

- **Quand** : après la PR #20 ; peut tourner en parallèle du lot 1
- **Où** : Claude Code en local, en mode plan ; effort `high`
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot2-saison-et-contact.md, propose un plan, puis attends ma validation. »

## Objectif

Retirer des réglages de la saison ce qui ne sert pas, et permettre aux coordinateurs de donner un téléphone **ou** une adresse e-mail comme contact.

## Décisions du porteur du projet

1. **Supprimer le nom de la saison** : il n'est plus affiché en haut du planning ni modifiable dans le back-office.
2. **Supprimer l'adresse du lieu et les consignes** (V12) : les bénévoles les connaissent déjà. Plus rien dans « Mes inscriptions », le back-office ni le cahier des charges.
3. **« Téléphone des coordinateurs » devient « Contact des coordinateurs »** : un champ texte qui accepte un numéro de téléphone **ou** une adresse e-mail.
   - Validation côté serveur : soit un numéro français plausible, soit une adresse e-mail valide ; message clair sinon. Longueur limitée.
   - Côté bénévole (après la limite de R6, et partout où le numéro apparaît aujourd'hui) : « Contactez les organisateurs via {contact}. », avec un lien `tel:` pour un numéro, `mailto:` pour une adresse.
   - Repli de configuration : `APP_COORDINATORS_PHONE` est renommée `APP_COORDINATORS_CONTACT`. Garder la lecture de l'ancienne variable si la nouvelle est vide, le temps de mettre à jour le `.env` du serveur, et le signaler au démarrage par un avertissement sans afficher la valeur.
4. **Dates des formulaires** : ne rien changer (le format jj/mm/aaaa dépend de la langue du navigateur pour les champs de date natifs).

## À lire avant de planifier

- `CLAUDE.md`, `docs/cahier-des-charges.md` §5 (R6), §6 (back-office « Saison »), §11 question 2
- `planning.season.SeasonSettingsService`, les gabarits `admin/saison*.html`, `planning.html`, « Mes inscriptions », la désinscription
- Migrations V2 (`season.name`) et V12

## Livrables

- **Nouvelle migration** : suppression de `season.name`, `season.venue_address`, `season.instructions` ; `season.coordinators_phone` renommée `coordinators_contact` (longueur adaptée à une adresse e-mail). Ne jamais modifier V2 ni V12.
- Code, gabarits et `messages.properties` mis à jour ; `.env.example`, `deploy/README.md` et `CLAUDE.md` pour la variable renommée.
- Cahier des charges mis à jour (§5 R6, §6, §11).

## Tests (avant le code)

- La migration passe sur une base qui contient une saison avec nom, adresse, consignes et téléphone, et conserve le téléphone dans le contact.
- Validation du contact : numéro, adresse, valeur invalide refusée.
- Affichage : lien `tel:` pour un numéro, `mailto:` pour une adresse ; repli sur `APP_COORDINATORS_CONTACT`, puis sur l'ancienne variable.
- `/planning` n'affiche plus de nom de saison ; « Mes inscriptions » n'affiche plus d'adresse ni de consignes.
- Les tests d'accès existants restent verts.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Testé à la main : réglages de la saison, désinscription après la limite avec un numéro puis avec une adresse e-mail
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte, avec en « points de déploiement » : renommer la variable dans le `.env` du serveur
