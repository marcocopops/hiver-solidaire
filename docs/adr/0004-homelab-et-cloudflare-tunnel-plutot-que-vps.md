# ADR-0004 — Homelab et Cloudflare Tunnel plutôt qu'un VPS ou un PaaS

- **Statut** : proposé
- **Date** : 2026-10-24

## Contexte

L'appli doit être disponible toute la saison (de janvier à mars), avec un objectif de 99,5 %, une coupure de moins de 4 h (RTO) et une perte de moins de 24 h de données (RPO) ([cahier des charges](../cahier-des-charges.md) §1, §8). Le budget est nul ou minimal, le projet est aussi un terrain d'apprentissage, et un homelab héberge déjà le cv-assistant.

Les données hébergées sont personnelles (nom, e-mail, téléphone) mais d'ampleur limitée.

## Options envisagées

1. **Homelab + Cloudflare Tunnel** : l'appli tourne chez soi, un tunnel sortant la publie, sans port entrant ni IP exposée. Coût marginal faible et contrôle complet des données (hébergées en France). En contrepartie, la disponibilité dépend de l'électricité, de la box et du disque d'un particulier, d'un seul serveur sans redondance, et de Cloudflare, qui devient un point de passage obligé ; l'exploitation (mises à jour, sauvegardes, supervision) est entièrement à la charge de l'auteur.
2. **VPS** : disponibilité et réseau d'un hébergeur, IP fixe. En contrepartie, un coût mensuel, un système à durcir et à tenir à jour, des ports exposés à protéger, et moins d'apprentissage sur le sujet visé.
3. **PaaS** : le moins d'exploitation, déploiement simple. En contrepartie, un coût récurrent (ou des limites d'une offre gratuite qui peut changer), une base managée à part et moins de maîtrise de l'emplacement des données.

## Décision

Option 1. L'appli tourne sur le homelab, sur son propre réseau Docker, et n'est joignable que par `cloudflared` (tunnel sortant) ; la base n'est jamais exposée. Le déploiement se fait en pull depuis le homelab.

Le choix est fait pour le coût, l'apprentissage et l'absence de port entrant, en acceptant une disponibilité moindre qu'un hébergeur. Les risques sont traités par la supervision, des sauvegardes testées et un plan B (récap quotidien, export PDF, ancien Google Form) plutôt que par de la redondance.

D'après la [documentation de Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) (consultée le 24 octobre 2026), `cloudflared` « crée des connexions sortantes uniquement » vers le réseau de Cloudflare, ce qui justifie l'absence de port entrant.

**Non vérifié** : la gratuité du tunnel et de Cloudflare Access (et le nombre d'utilisateurs inclus), car les pages de tarifs sont rendues côté client et n'ont pas pu être lues depuis la session. À confirmer sur la [page des offres Zero Trust](https://www.cloudflare.com/plans/zero-trust-services/) avant l'acceptation de cet ADR.

## Conséquences

- La supervision externe (sonde toutes les 5 minutes, alerte téléphone) est indispensable : si le homelab et la box tombent, rien d'interne ne peut prévenir.
- Les sauvegardes quotidiennes chiffrées doivent être hors site, et la restauration testée avant l'ouverture.
- Le plan B doit marcher sans le homelab : les coordinateurs ont le récap et l'export en dehors du serveur.
- Un dépôt, une chaîne CI et un registre d'images sont accessibles depuis Internet ; le serveur ne doit donner aucun accès à GitHub (pas de runner auto-hébergé, pas de clé).
- Si le homelab s'avère trop fragile en bêta, repasser sur un VPS reste possible : l'appli est conteneurisée et sans dépendance au tunnel.
