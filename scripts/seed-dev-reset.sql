-- Vidage de la base de DÉVELOPPEMENT avant le jeu de données (scripts/seed-dev.sh --reset). Jamais en production.
-- Vide les e-mails automatiques (pour les recevoir de nouveau dans Mailpit), les inscriptions, les alertes et le journal
-- d'audit, puis supprime les bénévoles factices du script
-- (identifiants 5eed5eed-0000-…) ; les comptes créés à la main sont gardés, sans leurs inscriptions.
-- Exécuté dans la même transaction que seed-dev.sql : une erreur annule tout.

DO $$
DECLARE
    schema_version integer;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'seed-dev : schéma inattendu (aucune migration Flyway) ; démarrer l''appli une fois pour migrer la base';
    END IF;
    SELECT max(version::integer) INTO schema_version
    FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';
    IF schema_version IS NULL OR schema_version < 17 THEN
        RAISE EXCEPTION 'seed-dev : schéma inattendu (version % au lieu de 17 ou plus) ; démarrer l''appli une fois pour migrer la base',
            schema_version;
    END IF;
END
$$;

-- Table d'envois (V17) : ses lignes renvoient aux bénévoles, aux alertes et aux inscriptions.
DELETE FROM mail_delivery;
DELETE FROM coordinator_alert;
DELETE FROM registration;

-- Le journal d'audit est en ajout seul : son déclencheur (V10) refuse UPDATE et DELETE ligne par ligne, mais un
-- déclencheur FOR EACH ROW ne s'applique pas à TRUNCATE. Si un jour TRUNCATE est bloqué aussi, repartir d'une base
-- vide : docker compose down -v, docker compose up -d, démarrer l'appli une fois, puis seed-dev.sh.
TRUNCATE audit_log;

-- Au cas où on s'est connecté avec l'adresse d'un bénévole factice : ses sessions (PRINCIPAL_NAME = identifiant du
-- bénévole) et ses jetons de connexion encore valides.
DELETE FROM spring_session WHERE principal_name LIKE '5eed5eed-0000-%';
DELETE FROM one_time_token
WHERE username IN (SELECT email FROM volunteer WHERE id::text LIKE '5eed5eed-0000-%' AND email IS NOT NULL);
DELETE FROM volunteer WHERE id::text LIKE '5eed5eed-0000-%';
