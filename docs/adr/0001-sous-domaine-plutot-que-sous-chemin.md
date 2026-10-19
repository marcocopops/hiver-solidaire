# ADR-0001 — Héberger l'appli sur un sous-domaine plutôt que sous un chemin du portfolio

- **Statut** : accepté
- **Date** : 2026-10-18

## Contexte

L'idée de départ était de servir l'appli sous `marcantoine-meunier.com/hiver-solidaire`. Cette origine héberge déjà le portfolio et le cv-assistant, un chat LLM qui affiche des réponses générées.

L'appli manipule des données personnelles de bénévoles (nom, e-mail, téléphone) et des sessions de coordinateurs qui voient tout.

Or le navigateur isole les sites par origine (schéma + hôte + port), jamais par chemin.

## Options envisagées

1. **Sous-chemin** `marcantoine-meunier.com/hiver-solidaire` : l'URL souhaitée, mais une seule origine pour trois applications.
2. **Sous-domaine** `hiver-solidaire.marcantoine-meunier.com` : une origine propre, sans coût supplémentaire.
3. **Domaine dédié** (par exemple un `.fr`) : isolation complète, mais un coût annuel et un projet détaché du portfolio.

## Décision

Option 2 : l'appli est servie sur `hiver-solidaire.marcantoine-meunier.com`. Une règle de redirection Cloudflare (301) envoie `marcantoine-meunier.com/hiver-solidaire` vers le sous-domaine, pour que l'URL d'origine reste utilisable.

Avec un sous-chemin, une faille XSS dans le portfolio ou le cv-assistant exécuterait du script sur la même origine que l'appli. Afficher du Markdown généré par un LLM est un vecteur classique. Ce script pourrait lire les pages de l'appli et agir avec la session d'un coordinateur. L'attribut `Path` des cookies ne protège pas contre cela.

## Conséquences

- Le cookie de session peut porter le préfixe `__Host-` (Secure, `Path=/`, sans `Domain`), qui n'a de sens que si toute l'origine appartient à l'appli.
- L'appli a sa propre CSP et ses propres en-têtes, sans contraindre le portfolio.
- Le routage reste simple : une route par nom d'hôte dans le tunnel, sans `context-path` Spring ni proxy devant la racine du portfolio.
- Le sous-domaine reste du même site (*same-site*) que le portfolio : `SameSite` ne suffit donc pas, et la protection CSRF par jeton reste obligatoire (déjà prévue).
- Le portfolio ne doit jamais poser de cookie avec `Domain=marcantoine-meunier.com`, car il serait envoyé à l'appli.
- Il faut une entrée DNS et une route de tunnel de plus.
- Si l'association veut un jour sa propre identité, l'option 3 reste possible sans changer l'appli.
