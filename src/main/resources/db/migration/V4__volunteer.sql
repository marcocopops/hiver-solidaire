-- S3 : bénévole (cahier des charges §7) et rattachement des inscriptions (R2).
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Le bénévole est créé à sa première connexion, une fois le contrôle de son adresse prouvé par le lien
-- magique (ADR-0003) : il n'a alors que son e-mail. Nom, prénom et téléphone arrivent avec le formulaire de
-- première connexion (§6, étape 4), qui pose profile_completed_at. Aucun champ libre (§7).
CREATE TABLE volunteer (
    id                    uuid         PRIMARY KEY,
    -- Normalisé par l'appli (espaces retirés, minuscules) : la contrainte garantit qu'aucune variante de casse
    -- ne crée un second compte.
    email                 varchar(254) NOT NULL,
    last_name             varchar(100),
    first_name            varchar(100),
    phone                 varchar(20),
    -- « Me recontacter la saison prochaine » : décoché par défaut (§6, §7).
    recontact_next_season boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    profile_completed_at  timestamptz,
    CONSTRAINT volunteer_email_unique UNIQUE (email),
    CONSTRAINT volunteer_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT volunteer_profile_complete CHECK (
        profile_completed_at IS NULL
        OR (last_name IS NOT NULL AND first_name IS NOT NULL AND phone IS NOT NULL))
);

-- R2 : une inscription par bénévole et par soirée, garantie aussi en base.
-- NOT NULL sans valeur par défaut : passe sur une table registration vide (cas de la production, aucune route
-- n'en créait avant S3). Une base de développement qui contient des inscriptions de test doit être vidée
-- d'abord : TRUNCATE registration;
ALTER TABLE registration
    ADD COLUMN volunteer_id uuid NOT NULL REFERENCES volunteer (id),
    ADD CONSTRAINT registration_one_per_volunteer_and_evening UNIQUE (evening_id, volunteer_id);

CREATE INDEX registration_volunteer_idx ON registration (volunteer_id);
