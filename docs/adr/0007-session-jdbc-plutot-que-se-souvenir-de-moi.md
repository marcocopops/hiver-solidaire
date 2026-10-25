# ADR-0007 — Session de 30 jours en base plutôt que « se souvenir de moi »

- **Statut** : accepté
- **Date** : 2026-10-25

## Contexte

Le bénévole reste connecté 30 jours sur son appareil ([cahier des charges](../cahier-des-charges.md) §6, étape 6). L'[ADR-0003](0003-lien-magique-plutot-que-mot-de-passe.md) renvoie le choix du mécanisme à S3 : chaque connexion coûte un e-mail, et redemander un lien à chaque visite ferait perdre l'objectif « s'inscrire en moins de 30 secondes ».

Contraintes :

- la session survit à un redémarrage de l'appli (mise à jour en pull, [deploy/README.md](../../deploy/README.md)) ;
- cookie `__Host-`, HttpOnly, Secure, SameSite=Lax, nouvel identifiant à la connexion (§8) ;
- une seule instance de l'appli, une base PostgreSQL déjà sauvegardée ([ADR-0005](0005-postgresql-plutot-que-sqlite.md)) ;
- les coordinateurs auront des sessions de 8 h (S4).

## Options envisagées

1. **Session serveur en base (Spring Session JDBC)**, durée 30 jours.
   - Avantages : une seule notion de session et un seul cookie ; elle survit au redémarrage, jeton CSRF compris (un formulaire ouvert avant une mise à jour reste valable) ; révocable côté serveur en supprimant la ligne (perte d'un téléphone, compte compromis) ; purge des sessions expirées intégrée.
   - Inconvénients : une nouvelle dépendance (`spring-boot-starter-session-jdbc`) et deux tables ; une lecture et une écriture en base par requête (négligeable pour une vingtaine de bénévoles) ; une ligne aussi pour un visiteur qui ouvre un formulaire, à cause du jeton CSRF ; le contenu de la session est sérialisé en Java dans la base, sauvegardes comprises.
2. **« Se souvenir de moi » persistant** (`PersistentTokenBasedRememberMeServices`, table `persistent_logins`) et session Tomcat courte.
   - Avantages : aucune dépendance nouvelle ; mécanisme classique de Spring Security, avec détection du vol de jeton (série et jeton renouvelés à chaque usage).
   - Inconvénients : deux cookies et deux durées à raisonner ; à chaque redémarrage, la session Tomcat et son jeton CSRF sont perdus, et un formulaire ouvert avant la mise à jour est refusé ; le cookie de rappel stocke le nom d'utilisateur en base ; plus de code de sécurité à relire ; son intégration avec le One-Time Token Login demande de propager le choix « se souvenir de moi » depuis la page « Me connecter ».
3. **Session Tomcat longue** (`server.servlet.session.timeout=30d`), en mémoire.
   - Avantage : rien à ajouter.
   - Inconvénient : perdue à chaque redémarrage, donc contraire à l'exigence. Écartée.

## Décision

Option 1 : Spring Session JDBC. C'est la traduction la plus directe de « rester connecté 30 jours » avec une seule notion de session, qui survit aux mises à jour et se révoque côté serveur.

Mise en œuvre :

- schéma de Spring Session versionné par Flyway (`V6__spring_session.sql`, `spring.session.jdbc.initialize-schema: never`) ;
- cookie `__Host-SESSION` déclaré dans `auth.SessionCookieConfig` (Secure forcé, HttpOnly, SameSite=Lax, `Path=/`, sans `Domain`, `Max-Age` 30 jours), plutôt que par `server.servlet.session.cookie.*` : Spring Boot n'applique ces propriétés qu'avec un serveur embarqué, et les tests verraient un autre cookie que la production ;
- durée d'inactivité maximale de 30 jours (`spring.session.timeout`) **et** durée absolue de 30 jours après la connexion (`auth.SessionLifetimeFilter`, qui compare l'heure de connexion notée dans la session à l'horloge injectée) : une session utilisée tous les jours expire quand même ;
- le nom du principal en base (`spring_session.principal_name`) est l'identifiant technique du bénévole, jamais son e-mail.

## Conséquences

- Les sessions font partie de la base : elles sont sauvegardées avec elle (chiffrées, hors site). Une restauration rouvre les sessions encore valides, ce qui est acceptable.
- Révoquer toutes les sessions d'un bénévole : `DELETE FROM spring_session WHERE principal_name = '<uuid>'`. À exposer dans le back-office si le besoin apparaît (S4).
- Les coordinateurs (S4) réutiliseront le même filtre avec une durée de 8 h, choisie selon le rôle.
- Le cookie est `Secure` y compris en local : Chrome et Firefox l'acceptent sur `http://localhost` (vérifié avec Chromium le 25 octobre 2026), mais pas sur une adresse IP du réseau local ; pour tester depuis un téléphone, passer par un tunnel HTTPS.
- Le contenu de la session est sérialisé en Java : changer la forme d'une classe stockée en session (principal, contexte de sécurité) peut invalider les sessions existantes lors d'une montée de version de Spring Security. Conséquence acceptée : les bénévoles redemandent un lien.
