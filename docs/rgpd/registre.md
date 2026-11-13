# Registre des traitements — fiche « Planning des bénévoles »

*Gabarit rempli au 16 novembre 2026 (S5b), à compléter et à valider par l'association. Aucune donnée réelle dans le dépôt : les champs entre crochets sont à renseigner dans l'exemplaire tenu par l'association, hors du dépôt.*

Référence : article 30 du RGPD ; modèle de registre de la CNIL. Sources dans le dépôt : [cahier des charges](../cahier-des-charges.md) §7 et §8, [ADR-0008](../adr/0008-annulation-logique-et-anonymisation.md), [ADR-0013](../adr/0013-turnstile-exception-a-la-regle-sans-cdn.md), [ADR-0014](../adr/0014-purge-par-anonymisation-et-journal-d-audit-borne.md), [modèle de menaces](../securite/modele-de-menaces.md).

## 1. Responsable du traitement

| Rubrique | Contenu |
|---|---|
| Responsable | [l'organisateur de la saison] : nom, adresse, représentant légal |
| Contact pour ce traitement | [contact des coordinateurs, tel que saisi dans les réglages de la saison] |
| Délégué à la protection des données | [aucun / nom et contact s'il en existe un] |
| Date de création de la fiche | 16 novembre 2026 |
| Dernière mise à jour | [date] |

## 2. Finalités

- Organiser l'accueil hivernal de personnes sans abri : planning public des soirées, inscriptions et désinscriptions des bénévoles, contact des bénévoles par les coordinateurs en cas d'imprévu.
- Envoyer aux bénévoles le lien de connexion, la confirmation de leurs inscriptions et un rappel la veille ; envoyer aux coordinateurs les alertes et le récapitulatif quotidien.
- Avec l'accord du bénévole : le recontacter la saison suivante.
- Sécurité du service : protection contre les robots, journal des actions des coordinateurs.

Aucune prise de décision automatisée, aucun profilage, aucune publicité.

## 3. Base légale

- Organisation de l'accueil : intérêt légitime du responsable (article 6.1.f). **À confirmer par l'association.**
- Conservation d'une saison à l'autre : consentement du bénévole (article 6.1.a), case « Me recontacter la saison prochaine », décochée par défaut, retirable à tout moment dans « Mon compte ».

## 4. Personnes concernées

- Bénévoles, tous majeurs (une vingtaine), dont les 2 ou 3 coordinateurs.
- Aucune donnée sur les personnes accueillies.

## 5. Données traitées

| Catégorie | Données | Origine |
|---|---|---|
| Identification | Nom, prénom | Le bénévole (premier profil) ou un coordinateur (inscription par téléphone) |
| Pseudo public | 3 à 20 caractères, choisi par le bénévole, **visible par tous sur le planning** | Le bénévole |
| Coordonnées | Adresse e-mail, numéro de téléphone | Le bénévole ; le téléphone seul pour une inscription par téléphone |
| Vie du service | Inscriptions (soirées, postes), annulations, accord de recontact, dates de création et de modification | Le site |
| Techniques | Session de connexion (cookie), jetons de connexion (empreinte seulement), envois d'e-mails (identifiants et dates), alertes, journal des actions des coordinateurs (identifiants techniques) | Le site |

Aucun champ libre, aucune donnée sensible (article 9). L'adresse IP est vue par Cloudflare et utilisée en mémoire par le site pour limiter les demandes ; elle n'est pas enregistrée en base.

## 6. Destinataires

- Coordinateurs (2 ou 3) : toutes les données des bénévoles, pour organiser les soirées.
- Public (sans connexion) : le pseudo et les journées où le bénévole vient.
- Chaque bénévole : ses propres données.

### Sous-traitants

| Sous-traitant | Rôle | Données | Localisation, garanties |
|---|---|---|---|
| Cloudflare | Réseau, tunnel, protection du site, Access (back-office), Turnstile (page de connexion) | Adresse IP, pages consultées, en-têtes | [à compléter : contrat (DPA) de Cloudflare, transferts hors UE et garanties] |
| Brevo | Envoi des e-mails | Adresses e-mail, contenu des e-mails (dont le récap des coordinateurs : nom, pseudo, téléphone des inscrits) | [à compléter : contrat (DPA) de Brevo, hébergement] |

Hébergement : serveur personnel situé en France, [nom de la personne qui l'administre et cadre de la mise à disposition, à préciser par l'association].

## 7. Transferts hors de l'Union européenne

[À compléter selon les contrats de Cloudflare et de Brevo.]

## 8. Durées de conservation

Appliquées chaque jour par une purge automatique (ADR-0014) :

| Données | Durée |
|---|---|
| Bénévole sans accord de recontact | Anonymisé un mois après la fin de la saison |
| Bénévole avec accord | Jusqu'au retrait de l'accord, au plus 3 ans sans participation |
| Compte créé sans profil complété | Supprimé au bout de 7 jours |
| Envois d'e-mails, alertes, journal d'audit | 6 mois au plus |
| Liens de connexion | 15 minutes (purgés toutes les heures) |
| Sessions | 30 jours au plus (8 heures pour un coordinateur), purgées après expiration |
| Sauvegardes chiffrées | [durée à reprendre de S6 ; la notice annonce « au plus 30 jours »] |
| Exports CSV et pages imprimées des coordinateurs | Le temps de l'usage, puis destruction (consigne aux coordinateurs, [à rédiger]) |

« Anonymisé » : la ligne du bénévole perd e-mail, nom, prénom, pseudo et téléphone ; ses inscriptions passées restent comptées sans être rattachables à une personne.

## 9. Mesures de sécurité

- Connexion par lien à usage unique (15 minutes), seule l'empreinte du jeton est stockée ; session en base, cookie `__Host-`, HttpOnly, Secure, SameSite=Lax.
- Vérification anti-robot (Turnstile) et limitation des demandes de lien par adresse et par IP.
- Back-office réservé au rôle coordinateur, vérifié par l'application et par Cloudflare Access ; journal d'audit des actions.
- CSP stricte, protection CSRF, aucune donnée personnelle dans les journaux techniques.
- Aucun port entrant (tunnel sortant), base de données non exposée, conteneurs sans privilèges.
- Aucun e-mail vers les domaines réservés aux tests.
- Sauvegardes chiffrées hors site, restauration testée ([à compléter en S6]).

Détail : [modèle de menaces](../securite/modele-de-menaces.md).

## 10. Droits des personnes

- Information : notice publique sur le site (`/donnees-personnelles`), liée depuis le premier profil, « Mon compte » et le pied de chaque page.
- Accès et rectification : « Mon compte » (téléphone, pseudo) ; nom et prénom auprès d'un coordinateur.
- Effacement : « Mon compte », effet immédiat.
- Retrait du consentement : « Mon compte ».
- Autres demandes (copie, opposition, limitation) : contact des coordinateurs ; [délai de réponse et personne chargée, à préciser].
- Réclamation : CNIL.

## 11. Violations de données

[Procédure à préciser par l'association : qui est prévenu, sous quel délai (72 heures pour la CNIL), où les violations sont consignées.]
