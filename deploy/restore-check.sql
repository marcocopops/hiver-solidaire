-- Contrôle d'une base restaurée (deploy/restore.sh, BackupRestoreScriptTest). Une ligne « mesure=valeur » par mesure :
-- des comptes seulement, aucune donnée personnelle. Lu avec psql -At -F=.
SELECT 'flyway_version', coalesce((SELECT version FROM flyway_schema_history
                                   WHERE success AND version IS NOT NULL
                                   ORDER BY installed_rank DESC LIMIT 1), '');
SELECT 'flyway_failed', count(*) FROM flyway_schema_history WHERE NOT success;
SELECT 'seasons', count(*) FROM season;
SELECT 'evenings', count(*) FROM evening;
SELECT 'volunteers', count(*) FROM volunteer WHERE deleted_at IS NULL;
SELECT 'registrations', count(*) FROM registration WHERE cancelled_at IS NULL;
SELECT 'audit_log', count(*) FROM audit_log;
SELECT 'mail_delivery', count(*) FROM mail_delivery;
