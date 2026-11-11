# Décisions d'architecture

Chaque décision structurante est consignée ici, au format du [modèle](template.md). Un ADR accepté ne se réécrit pas : une nouvelle décision le remplace.

| N° | Décision | Statut |
|---|---|---|
| [0001](0001-sous-domaine-plutot-que-sous-chemin.md) | Héberger l'appli sur un sous-domaine plutôt que sous un chemin du portfolio | Accepté |
| [0002](0002-rendu-serveur-plutot-que-spa.md) | Rendu serveur plutôt que SPA | Accepté |
| [0003](0003-lien-magique-plutot-que-mot-de-passe.md) | Lien magique plutôt que mot de passe | Accepté |
| [0004](0004-homelab-et-cloudflare-tunnel-plutot-que-vps.md) | Homelab + Cloudflare Tunnel plutôt que VPS | Accepté |
| [0005](0005-postgresql-plutot-que-sqlite.md) | PostgreSQL plutôt que SQLite | Accepté |
| [0006](0006-soirees-stockees-plutot-que-calculees.md) | Soirées stockées plutôt que calculées | Accepté |
| [0007](0007-session-jdbc-plutot-que-se-souvenir-de-moi.md) | Session de 30 jours en base plutôt que « se souvenir de moi » | Accepté |
| [0008](0008-annulation-logique-et-anonymisation.md) | Annulation logique et anonymisation plutôt que suppression | Proposé |
| [0009](0009-affichage-par-journee-donnees-par-soiree.md) | Affichage par journée, données par soirée | Proposé |
| [0010](0010-coordinateurs-par-variable-d-environnement.md) | Coordinateurs désignés par variable d'environnement | Proposé |
| [0011](0011-un-poste-par-benevole-et-par-soiree.md) | Un poste par bénévole et par soirée, plutôt qu'une inscription par soirée | Proposé |
| [0012](0012-table-d-envois-plutot-qu-envoi-dans-la-requete.md) | Table d'envois et tâche planifiée plutôt qu'envoi dans la requête | Proposé |
| [0013](0013-turnstile-exception-a-la-regle-sans-cdn.md) | Cloudflare Turnstile sur la demande de lien, exception à la règle « pas de CDN » | Proposé |
| [0014](0014-purge-par-anonymisation-et-journal-d-audit-borne.md) | Purge quotidienne par anonymisation, journal d'audit borné à six mois | Proposé |
