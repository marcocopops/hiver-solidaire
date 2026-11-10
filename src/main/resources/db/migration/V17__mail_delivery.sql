-- S5a : e-mails automatiques (cahier des charges §6, ADR-0012). Table d'envois : chaque e-mail est une ligne, écrite dans
-- la transaction de sa cause ou par la tâche planifiée, puis envoyée par celle-ci. Une ligne ne contient aucune donnée
-- personnelle : des identifiants et des dates. Le contenu est calculé à l'envoi, à partir de l'état courant.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

CREATE TABLE mail_delivery (
    id              uuid        PRIMARY KEY,
    kind            varchar(20) NOT NULL,
    -- Destinataire d'une confirmation ou d'un rappel (son adresse est lue à l'envoi, jamais copiée ici).
    volunteer_id    uuid        REFERENCES volunteer (id),
    -- Alerte d'un e-mail immédiat aux coordinateurs. Supprimée avec sa soirée (saison d'exemple remplacée).
    alert_id        uuid        REFERENCES coordinator_alert (id) ON DELETE CASCADE,
    -- Journée d'un rappel (la carte du lendemain, ADR-0009) ou jour d'un récap.
    reference_date  date,
    created_at      timestamptz NOT NULL,
    -- Plus rien ne s'ajoute à la ligne (une confirmation se ferme quand son envoi commence ; les autres naissent fermées).
    closed_at       timestamptz,
    next_attempt_at timestamptz NOT NULL,
    -- Au-delà, l'e-mail n'a plus de sens : il est abandonné (EXPIRED), jamais envoyé en retard.
    not_after       timestamptz NOT NULL,
    -- Échecs d'envoi déjà subis.
    attempts        smallint    NOT NULL DEFAULT 0,
    sent_at         timestamptz,
    abandoned_at    timestamptz,
    abandon_reason  varchar(20),
    CONSTRAINT mail_delivery_kind CHECK (kind IN ('CONFIRMATION', 'REMINDER', 'DAILY_RECAP', 'ALERT')),
    CONSTRAINT mail_delivery_shape CHECK (
        (kind = 'CONFIRMATION' AND volunteer_id IS NOT NULL AND alert_id IS NULL AND reference_date IS NULL)
        OR (kind = 'REMINDER' AND volunteer_id IS NOT NULL AND alert_id IS NULL AND reference_date IS NOT NULL)
        OR (kind = 'DAILY_RECAP' AND volunteer_id IS NULL AND alert_id IS NULL AND reference_date IS NOT NULL)
        OR (kind = 'ALERT' AND volunteer_id IS NULL AND alert_id IS NOT NULL AND reference_date IS NULL)),
    CONSTRAINT mail_delivery_closed CHECK (kind = 'CONFIRMATION' OR closed_at IS NOT NULL),
    CONSTRAINT mail_delivery_attempts CHECK (attempts >= 0),
    -- Envoyée ou abandonnée, jamais les deux ; dans les deux cas, fermée.
    CONSTRAINT mail_delivery_outcome CHECK (sent_at IS NULL OR abandoned_at IS NULL),
    CONSTRAINT mail_delivery_done_is_closed CHECK ((sent_at IS NULL AND abandoned_at IS NULL) OR closed_at IS NOT NULL),
    CONSTRAINT mail_delivery_abandon_reason CHECK (
        (abandoned_at IS NULL AND abandon_reason IS NULL)
        -- IS NOT NULL explicite : sans lui, « NULL IN (...) » vaut NULL et le CHECK laisserait passer.
        OR (abandoned_at IS NOT NULL AND abandon_reason IS NOT NULL
            AND abandon_reason IN ('NO_RECIPIENT', 'NOTHING_TO_SEND', 'EXPIRED', 'FAILED')))
);

-- Envoi une seule fois, garanti par la base : la tâche planifiée crée ses lignes par INSERT … ON CONFLICT DO NOTHING,
-- et peut donc reprendre après un redémarrage sans doubler ce qui existe.
CREATE UNIQUE INDEX mail_delivery_one_reminder ON mail_delivery (volunteer_id, reference_date)
    WHERE kind = 'REMINDER';
CREATE UNIQUE INDEX mail_delivery_one_recap ON mail_delivery (reference_date)
    WHERE kind = 'DAILY_RECAP';
CREATE UNIQUE INDEX mail_delivery_one_per_alert ON mail_delivery (alert_id)
    WHERE kind = 'ALERT';
-- Une seule confirmation ouverte par bénévole : ses changements s'y regroupent jusqu'à l'envoi.
CREATE UNIQUE INDEX mail_delivery_one_open_confirmation ON mail_delivery (volunteer_id)
    WHERE kind = 'CONFIRMATION' AND closed_at IS NULL;

CREATE INDEX mail_delivery_pending_idx ON mail_delivery (next_attempt_at)
    WHERE sent_at IS NULL AND abandoned_at IS NULL;
CREATE INDEX mail_delivery_volunteer_idx ON mail_delivery (volunteer_id);

-- Changements d'inscription regroupés dans une confirmation. Les inscriptions annulées d'une saison d'exemple remplacée
-- sont supprimées avec leur soirée : leurs changements partent avec elles.
CREATE TABLE mail_delivery_change (
    delivery_id     uuid        NOT NULL REFERENCES mail_delivery (id) ON DELETE CASCADE,
    registration_id uuid        NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    change          varchar(10) NOT NULL,
    by_coordinator  boolean     NOT NULL,
    occurred_at     timestamptz NOT NULL,
    PRIMARY KEY (delivery_id, registration_id, change),
    CONSTRAINT mail_delivery_change_kind CHECK (change IN ('REGISTERED', 'CANCELLED'))
);

CREATE INDEX mail_delivery_change_registration_idx ON mail_delivery_change (registration_id);
