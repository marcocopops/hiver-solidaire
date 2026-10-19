# Hiver Solidaire — planning des bénévoles

Application web de planning pour l'accueil hivernal de personnes sans abri. Une vingtaine de bénévoles s'y inscrivent en quelques secondes sur les dîners, les nuits et les petits-déjeuners de la saison.

> *EN — Volunteer scheduling app for a small association's winter homeless shelter: Spring Boot with server-side rendering, magic-link login, self-hosted on a homelab behind Cloudflare Tunnel, built end to end with Claude Code.*

**Statut** : en construction. Ouverture des inscriptions prévue le 14 décembre 2026.

## Ce que fait l'application

- Planning public des soirées, avec les places restantes et sans aucune donnée personnelle
- Inscription et désinscription en autonomie, connexion par lien magique (pas de mot de passe)
- Back-office des coordinateurs : soirées à compléter, inscriptions par téléphone, exports
- E-mails de confirmation, rappel la veille, alertes et récap quotidien aux coordinateurs

## Choix techniques

| Couche | Choix |
|---|---|
| Application | Java 21, Spring Boot 4.1, Spring Security 7, Thymeleaf + htmx |
| Données | PostgreSQL, Flyway |
| Hébergement | Docker Compose sur un homelab, exposé par Cloudflare Tunnel (aucun port entrant) |
| CI/CD | GitHub Actions, image publiée sur GHCR, déploiement en pull |

Sécurité et données personnelles : §7 et §8 du [cahier des charges](docs/cahier-des-charges.md).

## Documentation

- [Cahier des charges](docs/cahier-des-charges.md) : la référence fonctionnelle et technique
- [Décisions d'architecture (ADR)](docs/adr/)
- [Prompts des sessions Claude Code](docs/prompts/) : comment le projet est construit avec Claude
- [CLAUDE.md](CLAUDE.md) : les règles que suit Claude Code dans ce dépôt
- [Signaler une vulnérabilité](SECURITY.md)

## Démarrer en local

Disponible à la fin de la semaine 1 (squelette).
