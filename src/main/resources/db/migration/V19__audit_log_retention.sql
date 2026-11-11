-- S5b : durées de conservation (cahier des charges §7, ADR-0014). Le journal d'audit reste en ajout seul (V10), avec une
-- seule exception, contrôlée par la base elle-même : une ligne de plus de six mois peut être supprimée (purge
-- quotidienne). Une modification reste toujours refusée, et une suppression plus récente aussi, même par l'appli.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.
CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.occurred_at <= now() - interval '6 months' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_log is append-only';
END
$$;
