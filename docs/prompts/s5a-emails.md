# S5a — E-mails automatiques (Brevo)

- **Semaine** : S5, du 10 au 16 novembre 2026 (après le lot 5)
- **Où** : Claude Code en local, en mode plan ; **Opus, effort `high`** (envois garantis une seule fois, planification, données personnelles)
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/s5a-emails.md, propose un plan, puis attends ma validation. »

## Objectif

Envoyer les e-mails prévus au §6 du cahier des charges : confirmations et rappel aux bénévoles, alertes et récap quotidien aux coordinateurs. Brevo est déjà configuré en SMTP (le lien de connexion part par ce chemin depuis S3) : cette session ajoute les autres messages, sans nouveau fournisseur.

## Décisions du porteur du projet

1. **Rappel aux bénévoles : la veille à 18 h**, heure de Paris, pour chaque inscription du lendemain (un seul e-mail par bénévole, qui liste tous ses postes du lendemain). Le petit-déjeuner du jour D appartient à la soirée D-1 (ADR-0009) : son rappel part le jour D-1 à 18 h, avec celui de la soirée D-1 s'il y en a un.
2. **Récap quotidien aux coordinateurs : chaque jour à 20 h** (et non 7 h comme l'écrit le cahier des charges : le mettre à jour). Il couvre les 7 prochains jours : ce qui manque par poste, les inscrits par journée (nom, pseudo, téléphone), les alertes non traitées. Il sert aussi de plan B (§8) : il doit rester lisible sans le site.
3. **Alertes aux coordinateurs** :
   - **immédiates** (e-mail dès l'enregistrement de l'alerte) : « soirée devenue incomplète » pour une soirée des **48 prochaines heures** ;
   - **dans le récap seulement** : toutes les autres (soirée incomplète au-delà de 48 h, récupération sans repas, nouveau bénévole).
   Le cahier des charges (§6, tableau des e-mails) est mis à jour en conséquence.
4. **Confirmations aux bénévoles** : après une inscription et après une désinscription, faites par le bénévole lui-même ou par un coordinateur. Le §6 dit « un seul e-mail par session » : proposer dans le plan comment regrouper (par exemple un e-mail par action confirmée, qui liste tous les postes concernés).
5. **« Répondre à »** : le contact des coordinateurs de la saison quand c'est une adresse e-mail ; sinon, pas d'en-tête et le message indique le numéro.
6. **Aucun suivi** : aucun lien réécrit, aucun pixel de suivi. Les liens pointent directement vers `APP_BASE_URL`.

## Contraintes

- **Envoi une seule fois, même si l'appli redémarre** : un envoi doit être enregistré en base (table d'envois, ou `sent_at` comme `coordinator_alert`), et la tâche planifiée doit reprendre proprement ce qui n'est pas parti sans doubler ce qui l'est. Une seule instance de l'appli : pas de verrou distribué, mais le dire dans un ADR.
- **Planification** en `Europe/Paris`, avec l'horloge injectée (`MutableClock` en test) ; changements d'heure compris. Si l'appli était arrêtée à l'heure prévue, proposer dans le plan ce qui se passe au redémarrage (rattraper le rappel du jour s'il est encore temps, jamais celui d'une soirée passée).
- **Bénévole sans e-mail** (inscrit par téléphone) : il ne reçoit rien ; le récap des coordinateurs le signale (« à prévenir par téléphone »).
- **Comptes supprimés** : aucun e-mail.
- **Contenu** : aucune adresse du lieu ni consigne (l'appli ne les stocke pas). Les horaires viennent des textes fixes du §5. Lien de désinscription : il ouvre la page de confirmation existante, jamais d'action en GET.
- **Format** : texte brut et HTML simple (gabarits Thymeleaf, `th:text`), textes dans `messages.properties`, nom d'expéditeur « Hiver Solidaire ».
- **Échec d'envoi** (Brevo indisponible, quota) : nouvel essai plus tard, borné, sans bloquer l'inscription elle-même ; un échec n'expose jamais l'adresse dans les logs.
- **Logs** : identifiants techniques seulement, jamais d'adresse ni de contenu de message.
- **Dépendances** : `spring-boot-starter-mail` est déjà là ; toute nouvelle dépendance justifiée dans la PR.

## À lire avant de planifier

- `CLAUDE.md` ; `docs/cahier-des-charges.md` §5 (R4, R6), §6 (tableau des e-mails, « S'inscrire », « Se désinscrire »), §7, §8 (plan B)
- Le module `notification` (`MagicLinkSender`, `SmtpMagicLinkSender`, `CoordinatorAlerts`), la table `coordinator_alert` (V8 et suivantes), `RegistrationService`, `admin.CoordinatorActions`
- ADR 0008, 0009, 0011 ; `docs/securite/modele-de-menaces.md`

## Livrables

- Les e-mails ci-dessus, avec leurs gabarits.
- Les tâches planifiées (rappel, récap, envoi des alertes immédiates), et l'enregistrement des envois.
- Une migration si une table d'envois est nécessaire (ne jamais modifier une migration fusionnée).
- En local : tous les e-mails visibles dans Mailpit ; un moyen simple de déclencher le rappel et le récap à la main en développement (par exemple une propriété qui avance l'horloge, ou une commande documentée), jamais exposé en production.
- Documentation : cahier des charges (§6 tableau des e-mails, récap à 20 h, alertes), un ADR (planification et envoi une seule fois), modèle de menaces (e-mails : usurpation, fuite de coordonnées dans le récap), `CLAUDE.md`, `deploy/README.md` (rien de nouveau à configurer si ce n'est pas nécessaire).

## Tests (avant le code)

- Rappel : bonnes personnes, bonne heure, un seul envoi même après redémarrage ; petit-déjeuner rattaché à la bonne soirée ; changement d'heure ; inscription annulée avant 18 h : pas de rappel.
- Récap : contenu par journée, alertes non traitées, bénévole sans e-mail signalé ; aucun e-mail si aucun coordinateur.
- Alertes : immédiate pour une soirée des 48 prochaines heures, sinon seulement dans le récap ; jamais envoyée deux fois.
- Confirmations : après inscription, désinscription, action d'un coordinateur ; regroupement conforme au plan.
- « Répondre à » : adresse ou absence selon le contact de la saison.
- Aucun lien en GET qui modifie ; aucun e-mail pour un compte supprimé ou sans adresse ; aucune adresse dans les logs.

## Critères de fin

- [ ] `./mvnw verify` vert, CI verte
- [ ] Testé à la main avec `scripts/seed-dev.sh` et Mailpit : confirmation, rappel, récap, alerte immédiate
- [ ] `/security-review` lancé, remarques traitées ou listées
- [ ] PR ouverte : résumé, comment tester, points de sécurité, points de déploiement (vérifier les variables `MAIL_*` du serveur), questions ouvertes
