-- S4b : journal d'audit des actions des coordinateurs (cahier des charges §6, §8 ; modèle de menaces M6, M8), et ce
-- dont le tableau de bord a besoin : alertes traitées, nouveaux bénévoles vus.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Qui (identifiant technique du coordinateur), quand, quelle action, sur quoi (identifiants seulement). Aucun texte
-- libre, aucune colonne pour un nom, un e-mail ou un téléphone. Pas de clé étrangère vers les objets visés : le journal
-- survit à la suppression d'une soirée sortie de la saison ou de la saison d'exemple.
CREATE TABLE audit_log (
    id                uuid        PRIMARY KEY,
    occurred_at       timestamptz NOT NULL,
    coordinator_id    uuid        NOT NULL,
    action            varchar(40) NOT NULL,
    volunteer_id      uuid,
    registration_id   uuid,
    evening_id        uuid,
    target_evening_id uuid,
    season_id         uuid,
    alert_id          uuid,
    period_from       date,
    period_to         date,
    CONSTRAINT audit_log_action CHECK (action IN (
        'ALERT_HANDLED', 'VOLUNTEER_REVIEWED', 'REGISTRATION_CANCELLED', 'REGISTRATION_MOVED',
        'REGISTRATION_CREATED', 'VOLUNTEER_CREATED', 'VOLUNTEER_IDENTITY_CORRECTED', 'VOLUNTEER_DELETED',
        'SEASON_CREATED', 'SEASON_UPDATED', 'SAMPLE_SEASON_DELETED', 'REGISTRATIONS_EXPORTED',
        'PRINTABLE_PLANNING_VIEWED'))
);

CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at DESC);

-- Journal en ajout seul : ni modification ni suppression, y compris par l'appli. Une purge future (durées de
-- conservation, S5) passera par une migration qui le dira.
CREATE FUNCTION audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END
$$;

CREATE TRIGGER audit_log_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();

-- Tableau de bord : une alerte reste affichée tant qu'un coordinateur ne l'a pas marquée traitée.
ALTER TABLE coordinator_alert
    ADD COLUMN handled_at timestamptz,
    ADD COLUMN handled_by uuid,
    ADD CONSTRAINT coordinator_alert_handled CHECK ((handled_at IS NULL) = (handled_by IS NULL));

-- Nouveaux bénévoles à signaler (§2) : tant qu'aucun coordinateur ne les a vus.
ALTER TABLE volunteer ADD COLUMN reviewed_at timestamptz;
