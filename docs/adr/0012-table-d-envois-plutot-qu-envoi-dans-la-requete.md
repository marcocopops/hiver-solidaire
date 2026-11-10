# ADR-0012 — Table d'envois et tâche planifiée plutôt qu'envoi dans la requête

- **Statut** : proposé
- **Date** : 2026-11-10

## Contexte

S5a ajoute les e-mails automatiques du [cahier des charges](../cahier-des-charges.md) §6 : confirmation des inscriptions et désinscriptions, rappel la veille à 18 h, alerte immédiate aux coordinateurs, récap quotidien à 20 h. Jusqu'ici, seul le lien de connexion partait, sur deux fils dédiés, sans trace en base (`SmtpMagicLinkSender`) ; les alertes étaient écrites dans `coordinator_alert` (ADR-0008) sans être envoyées.

Contraintes :

- **envoi une seule fois, même si l'appli redémarre** : un rappel ou une alerte ne part pas deux fois, et ce qui n'est pas parti reprend proprement ;
- **heures fixes à Paris**, changements d'heure compris, avec l'horloge injectée (`MutableClock` en test) ; si l'appli était arrêtée à l'heure prévue, rattraper ce qui a encore un sens, jamais un e-mail sur une soirée passée ;
- un échec d'envoi (Brevo indisponible, quota) ne bloque jamais une inscription, et l'e-mail est réessayé, mais pas indéfiniment ;
- les confirmations d'une même « session » se regroupent (§6) : décision de S5a, un e-mail 10 minutes après le dernier changement ;
- aucune donnée personnelle dans les logs, et le moins possible en base ;
- une seule instance de l'appli sur le homelab ([ADR-0004](0004-homelab-et-cloudflare-tunnel-plutot-que-vps.md)).

## Options envisagées

1. **Envoi dans la requête ou juste après** (comme le lien de connexion, fil d'exécution en mémoire), avec des tâches `@Scheduled` qui envoient à heure fixe.
   - Avantages : peu de code, aucune table.
   - Inconvénients : un redémarrage perd ce qui était en mémoire, et une tâche relancée après un redémarrage ne sait pas ce qui est déjà parti (rappel en double ou oublié) ; aucun regroupement possible ; aucune reprise après un échec.
2. **Table d'envois** (`mail_delivery`) : chaque e-mail est une ligne, écrite dans la transaction de sa cause (inscription, désinscription, alerte) ou par la tâche planifiée à son heure ; une tâche unique, toutes les 30 secondes, envoie les lignes dues et enregistre l'issue.
   - Avantages : la base garantit l'unicité (index uniques partiels : un rappel par bénévole et par journée, un récap par jour, un e-mail par alerte) ; tout survit à un redémarrage ; une ligne de confirmation reste ouverte pour regrouper les changements ; essais et abandon tracés.
   - Inconvénients : une migration, une tâche planifiée, et un contenu à recalculer à l'envoi.
3. **File de messages ou planificateur externe** (Quartz en mode JDBC, file dédiée).
   - Avantages : outillage éprouvé, plusieurs instances possibles.
   - Inconvénients : une dépendance de plus et son schéma, pour une seule instance et quelques dizaines d'e-mails par jour.

## Décision

Option 2 : une table d'envois et une tâche planifiée dans l'appli. C'est la plus petite solution qui garantit « une seule fois » par la base, sans nouvelle dépendance.

Mise en œuvre :

- **Pas de donnée personnelle dans la table** (V17) : des identifiants (bénévole, alerte, inscriptions d'une confirmation) et des dates. L'adresse et le contenu sont calculés **à l'envoi**, à partir de l'état courant : une inscription annulée entre-temps disparaît de l'e-mail, un compte supprimé ou sans adresse ne reçoit rien, et une ligne qui n'a plus rien à dire est abandonnée.
- **Création idempotente** : `INSERT … ON CONFLICT DO NOTHING` sur les index uniques partiels. La tâche peut créer le rappel ou le récap à chaque tour, ou après un redémarrage, sans rien doubler.
- **Créneaux** (`MailSchedule`, Java pur, à l'heure de Paris) : rappel de la journée D créé le jour D-1 de 18 h à 22 h ; récap du jour X créé de 20 h au lendemain 8 h. Si l'appli était arrêtée à l'heure prévue, la tâche rattrape le rappel jusqu'à 22 h et le récap jusqu'à 8 h, puis y renonce : pas d'e-mail la nuit, et jamais sur une soirée passée. Chaque ligne porte une limite (`not_after`) au-delà de laquelle elle est abandonnée.
- **Envoi en trois temps** : une transaction courte réserve les lignes dues (`FOR UPDATE SKIP LOCKED`) et les ferme (une confirmation fermée ne reçoit plus de changement ; le suivant en ouvre une nouvelle) ; le contenu est calculé et l'e-mail envoyé **hors transaction** (un serveur SMTP lent ne bloque aucune inscription) ; une transaction enregistre l'issue.
- **Échecs** : nouvel essai à 1, 5, 15, 30 puis 60 minutes, 6 essais au plus, jamais après la limite de la ligne ; ensuite, abandon (`FAILED`). Les logs ne donnent que l'identifiant de la ligne, son type, le nombre d'essais et la classe de l'exception (le message d'une exception SMTP peut contenir l'adresse).
- **Une seule instance** : pas de verrou distribué. Un verrou en mémoire empêche deux tours simultanés dans l'appli ; les index uniques et `SKIP LOCKED` garderaient la base cohérente même si deux tours se chevauchaient.
- La tâche est désactivable (`app.mail.scheduler.enabled`, coupée dans les tests, qui appellent `MailJobs.tick()` avec l'horloge réglable). Le lien de connexion garde son envoi direct : il contient un jeton qu'on ne stocke jamais en clair (§8).

## Conséquences

- **Garantie effective** : un e-mail n'est créé qu'une fois. Reste une fenêtre assumée : si l'appli s'arrête entre l'acceptation du message par le serveur SMTP et l'enregistrement de `sent_at` (quelques millisecondes), la ligne repart au redémarrage. Un rappel en double vaut mieux qu'une alerte perdue.
- Un e-mail part au plus 30 secondes après son heure ; l'alerte « immédiate » aussi.
- Passer à plusieurs instances demanderait de revoir ce choix : le verrou en mémoire ne suffirait plus à éviter deux envois simultanés de la même ligne, faute de marquer la ligne « en cours d'envoi » en base (et les limites de demande de lien sont aussi en mémoire). Un nouvel ADR remplacerait celui-ci.
- La table grandit d'environ 50 lignes par jour de saison, sans donnée personnelle ; sa purge sera traitée avec les durées de conservation (§7, session RGPD).
- Sans `MAIL_HOST`, les e-mails attendent, puis sont abandonnés à leur limite (`EXPIRED`) : configurer le SMTP plus tard ne déclenche pas de rafale d'e-mails périmés.
