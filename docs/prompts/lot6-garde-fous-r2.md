# Lot 6 — Garde-fous de consommation Cloudflare R2

- **Quand** : après S6 (sauvegardes restic vers R2), mergée
- **Où** : Claude Code en local, en mode plan ; effort par défaut
- **Message de lancement** : « Lis CLAUDE.md et docs/prompts/lot6-garde-fous-r2.md, propose un plan, puis attends ma validation. »

## Objectif

Ne jamais dépasser l'offre gratuite de R2 : **10 Go-mois de stockage, 1 million d'opérations de classe A et 10 millions de classe B par mois**. Cloudflare ne propose pas de plafond de dépenses : ses alertes budgétaires préviennent par e-mail mais ne coupent rien. Le plafond doit donc venir de nous.

L'usage attendu est minuscule (une base de quelques Mo, une sauvegarde par nuit, quelques dizaines d'opérations par sauvegarde) : ces garde-fous protègent d'un **bug** (boucle, cron mal réglé, rétention cassée) et d'une **clé R2 volée**, pas de l'usage normal.

## Livrables

1. **Seuils dans le script de sauvegarde** (`deploy/backup.sh`) :
   - avant d'envoyer, mesurer la taille du dépôt restic ; au-delà de **1 Go** (10 % de l'offre), ne pas envoyer, alerter ;
   - un verrou (`flock`) empêche deux sauvegardes simultanées ;
   - un « interrupteur » (fichier sur le serveur, documenté) bloque toute écriture vers R2 tant qu'il est présent.
2. **Relevé mensuel de consommation** (`deploy/r2-usage.sh`, cron quotidien) :
   - interroge l'API GraphQL Analytics de Cloudflare (`r2StorageAdaptiveGroups`, `r2OperationsAdaptiveGroups`) pour le bucket, depuis le début du mois ;
   - classe les opérations en A et B selon la liste officielle de Cloudflare (à citer dans le script et la PR) ;
   - **alerte à 50 %** d'un des trois seuils, et **à 80 % pose l'interrupteur** du point 1 (plus aucune écriture jusqu'au mois suivant ou jusqu'à son retrait manuel), avec une alerte ;
   - jeton d'API **distinct** de celui de R2, en lecture seule sur les statistiques du compte (permission exacte à vérifier dans la documentation Cloudflare et à citer), dans le fichier de secrets du serveur, jamais dans le dépôt ;
   - si l'API ne répond pas plusieurs jours de suite, alerter (un relevé muet ne protège rien).
3. **Alertes** : par le même canal que les échecs de sauvegarde de S6 (pas de nouveau service si possible).
4. **Documentation** (`deploy/README.md` et runbook) :
   - créer une **alerte budgétaire Cloudflare** (Manage Account → Billing → Billable Usage → Create budget alert) au montant le plus bas accepté : filet de sécurité indépendant de nos scripts ;
   - vérifier que le jeton R2 est limité au seul bucket ;
   - que faire en cas d'alerte : vérifier la taille du dépôt, la rétention, les crons ; en cas de doute sur une fuite, **révoquer le jeton R2** et en créer un autre.

## Contraintes

- Aucun secret dans le dépôt ; scripts `bash` avec `set -euo pipefail`, `shellcheck` sans avertissement.
- Les appels à l'API Cloudflare utilisent `curl` (déjà présent) ; toute autre dépendance justifiée.
- Ne jamais supprimer de sauvegarde pour rester sous un seuil : on alerte et on arrête d'écrire, la décision reste humaine.

## Tests

- Classement des opérations A et B et calcul des pourcentages, sur des réponses de l'API enregistrées (fixtures factices), sans appel réseau.
- Interrupteur : présent, la sauvegarde n'écrit rien et alerte ; absent, elle fonctionne.
- Seuil de taille du dépôt restic.

## Critères de fin

- [ ] CI verte (`shellcheck` et tests des scripts)
- [ ] Relevé lancé à la main sur le serveur par le porteur du projet, avec des chiffres cohérents avec le tableau de bord R2
- [ ] Alerte budgétaire Cloudflare créée
- [ ] PR ouverte : résumé, gestes à faire sur le serveur et chez Cloudflare, sources de la classification A/B
