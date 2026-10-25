-- S4a : désinscription (R6) par annulation logique, suppression de compte par anonymisation, alertes aux
-- coordinateurs à envoyer (S5). Voir ADR-0008.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Annulation logique : une désinscription pose cancelled_at au lieu de supprimer la ligne. Les inscriptions annulées
-- restent en base (historique, alerte) mais ne comptent plus dans R1, R2, R3 ni R5.
ALTER TABLE registration ADD COLUMN cancelled_at timestamptz;

-- R3 : un plat principal et un accompagnement au plus par soirée, parmi les inscriptions actives seulement.
DROP INDEX registration_one_dish_per_evening;
CREATE UNIQUE INDEX registration_one_dish_per_evening
    ON registration (evening_id, dish_choice)
    WHERE dish_choice IS NOT NULL AND cancelled_at IS NULL;

-- R2 : une inscription active par bénévole et par soirée. Une contrainte UNIQUE ne peut pas être partielle : elle
-- devient un index unique partiel, qui laisse un bénévole se réinscrire après une annulation.
ALTER TABLE registration DROP CONSTRAINT registration_one_per_volunteer_and_evening;
CREATE UNIQUE INDEX registration_one_active_per_volunteer_and_evening
    ON registration (evening_id, volunteer_id)
    WHERE cancelled_at IS NULL;

-- Suppression du compte : la ligne du bénévole reste (ses inscriptions passées gardent un volunteer_id non nul et
-- restent comptées), mais vidée de toute donnée personnelle. L'e-mail ne peut être nul que pour un compte supprimé,
-- et un compte supprimé n'a plus ni e-mail, ni nom, ni prénom, ni téléphone, ni pseudo. Les unicités de l'e-mail
-- et du pseudo sont inchangées (NULL n'entre en conflit avec rien) : une même adresse peut recréer un compte, un
-- autre bénévole peut reprendre le pseudo.
ALTER TABLE volunteer
    ADD COLUMN deleted_at timestamptz,
    ALTER COLUMN email DROP NOT NULL,
    ADD CONSTRAINT volunteer_deleted_is_anonymous CHECK (
        (deleted_at IS NULL AND email IS NOT NULL)
        OR (deleted_at IS NOT NULL AND email IS NULL AND last_name IS NULL AND first_name IS NULL
            AND phone IS NULL AND pseudo IS NULL AND profile_completed_at IS NULL
            AND recontact_next_season = false));

-- Alertes aux coordinateurs, écrites dans la même transaction que leur cause (table d'événements) : S5 enverra
-- les lignes sans sent_at, puis le posera. Aucune donnée personnelle : la soirée seule.
CREATE TABLE coordinator_alert (
    id         uuid        PRIMARY KEY,
    kind       varchar(30) NOT NULL,
    evening_id uuid        NOT NULL REFERENCES evening (id),
    created_at timestamptz NOT NULL,
    sent_at    timestamptz,
    CONSTRAINT coordinator_alert_kind CHECK (kind IN ('EVENING_INCOMPLETE'))
);

CREATE INDEX coordinator_alert_unsent_idx ON coordinator_alert (created_at) WHERE sent_at IS NULL;
