# ADR-0016 — Sauvegardes restic vers R2, depuis un conteneur épinglé, surveillées par healthchecks.io

- **Statut** : proposé
- **Date** : 2026-11-13

## Contexte

Le cahier des charges (§8) exige des sauvegardes quotidiennes chiffrées hors site, une restauration testée avant l'ouverture, une perte de moins de 24 h (RPO) et une coupure de moins de 4 h (RTO). La notice RGPD (S5b) annonce que les sauvegardes gardent une copie des données « au plus 30 jours ».

Décisions du porteur du projet (prompt S6) : `pg_dump` quotidien, chiffré et envoyé par **restic** vers **Cloudflare R2** (offre gratuite de 10 Go, sortie gratuite), 7 sauvegardes quotidiennes et 4 hebdomadaires, `restic check` chaque semaine. Restaient à décider : où tourne restic, comment le dump évite le disque, et comment un échec se fait entendre.

Contraintes : rien qui écoute sur un nouveau port, images épinglées par empreinte, aucun secret dans le dépôt, scripts `bash` vérifiés par `shellcheck`, aucune donnée personnelle dans les logs.

## Options envisagées

### Où tourne restic

1. **Binaire sur l'hôte** (paquet de la distribution ou binaire officiel vérifié) : permet `restic backup --stdin-from-command`, qui n'enregistre pas de sauvegarde si `pg_dump` échoue. Mais le paquet de la distribution est souvent ancien, le binaire officiel se met à jour à la main, et Dependabot ne le suit pas.
2. **Conteneur `restic/restic` épinglé par empreinte**, déclaré comme service de `compose.prod.yaml` sous un profil (`backup`) : jamais démarré par `up -d`, lancé par `docker compose run` depuis les scripts. Il est durci comme les autres (lecture seule, aucune capability, utilisateur du script), sur son propre réseau, sans accès à la base ni à l'appli, et ne reçoit que ses secrets (`backup.env`). Dependabot peut suivre l'empreinte (écosystème `docker-compose`).

### Le dump en clair

1. **Flux `pg_dump | restic backup --stdin`** : rien sur le disque, mais un dump tronqué (base qui tombe pendant le dump) deviendrait quand même une sauvegarde, la dernière.
2. **Fichier en mémoire** (`/dev/shm`, tmpfs) : `pg_dump` écrit le fichier, `pg_restore --list` relit sa table des matières, puis restic l'envoie ; le fichier est supprimé dans tous les cas (`trap`). Le script refuse de tourner si `/dev/shm` n'est pas un tmpfs. La base fait quelques Mo.

### L'alerte en cas d'échec

1. **E-mail envoyé par le script** : muet si le script ne tourne plus du tout (serveur éteint, cron supprimé, disque plein).
2. **Service de surveillance de tâches, healthchecks.io (offre gratuite)** : le script envoie un ping au départ et son code de sortie à la fin ; sans ping de succès dans le délai prévu, healthchecks.io prévient par e-mail. Il détecte donc aussi l'absence d'exécution. Il ne reçoit que des horaires et un code de sortie, aucune donnée personnelle ni log.

## Décision

restic dans un **conteneur épinglé** (option 2), le dump dans un **fichier en mémoire contrôlé avant l'envoi** (option 2), et **healthchecks.io** pour l'alerte (option 2) :

- c'est le seul choix qui garde toutes les images épinglées et suivies, et isole restic de la base ;
- un dump tronqué n'est jamais envoyé, sans jamais toucher le disque ;
- seul un service extérieur au serveur peut dire qu'une tâche ne tourne plus.

Rétention : `forget --keep-daily 7 --keep-weekly 4 --prune` après chaque sauvegarde, sous un nom d'hôte fixe (sinon restic ne regroupe pas les sauvegardes des conteneurs successifs, et n'oublie rien). Tant que les tranches ne sont pas remplies, restic garde aussi la plus ancienne : au plus 4 semaines civiles en arrière, sous les 30 jours de la notice. Le dimanche, `restic check --read-data-subset=10%` (la sortie de R2 est gratuite). Avant une restauration en production, la sauvegarde préalable se fait sans `forget`, qui oublierait sinon la sauvegarde du jour qu'on va restaurer.

La restauration de contrôle se fait dans un PostgreSQL jetable (même image que la production, sans réseau, données en mémoire), jamais dans la base de production.

## Conséquences

- Plus simple : un seul fichier Compose à copier sur le serveur, les images suivies par Dependabot, une procédure de restauration éprouvée par un test qui lance les vrais scripts (`BackupRestoreScriptTest`).
- Le **mot de passe restic** est la clé de toutes les sauvegardes : gardé dans `backup.env` sur le serveur et dans un gestionnaire de mots de passe hors du serveur. Perdu, les sauvegardes sont illisibles.
- La clé R2 est en lecture et écriture sur le bucket (restic doit pouvoir oublier les anciennes sauvegardes) : un serveur compromis peut effacer les sauvegardes. Risque accepté (modèle de menaces, M32), le bucket ne contient que des données chiffrées.
- Nouveaux tiers : Cloudflare R2 (données chiffrées seulement, juridiction UE) et healthchecks.io (horaires seulement), cités dans le registre RGPD.
- Une restauration en production ramène la base à la nuit précédente : inscriptions et comptes du jour perdus, rappel du jour possiblement renvoyé, sessions de la veille ; le runbook dit qui prévenir.
