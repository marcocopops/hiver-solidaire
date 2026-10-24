-- S2 : modèle de données du planning (cahier des charges §5, ADR-0006 : soirées stockées).
-- Identifiants UUID, non devinables (modèle de menaces, M9).
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Saison : première et dernière soirée (date du soir J), ouverture des inscriptions (R7).
-- « sample » marque une saison d'exemple, à remplacer depuis le back-office (S4).
CREATE TABLE season (
    id                    uuid         PRIMARY KEY,
    label                 varchar(100) NOT NULL,
    first_evening         date         NOT NULL,
    last_evening          date         NOT NULL,
    registrations_open_at timestamptz  NOT NULL,
    sample                boolean      NOT NULL DEFAULT false,
    CONSTRAINT season_dates_ordered CHECK (last_evening >= first_evening)
);

-- Besoins par poste, paramétrables par saison (§5, tableau « Besoins par soirée »).
CREATE TABLE season_need (
    season_id uuid        NOT NULL REFERENCES season (id),
    post      varchar(30) NOT NULL,
    minimum   integer     NOT NULL,
    maximum   integer     NOT NULL,
    PRIMARY KEY (season_id, post),
    CONSTRAINT season_need_post CHECK (post IN ('MAIN_DISH', 'SIDES', 'DINNER_PRESENCE', 'NIGHT', 'BREAKFAST')),
    CONSTRAINT season_need_bounds CHECK (minimum >= 0 AND maximum >= minimum)
);

-- Soirée : une ligne par soir J de la saison. Cible du verrou de R1 (SELECT ... FOR UPDATE, ADR-0005) en S3.
CREATE TABLE evening (
    id           uuid    PRIMARY KEY,
    season_id    uuid    NOT NULL REFERENCES season (id),
    evening_date date    NOT NULL,
    closed       boolean NOT NULL DEFAULT false,
    CONSTRAINT evening_unique_date UNIQUE (season_id, evening_date)
);

-- Inscription : seulement ce qu'il faut pour calculer capacité et statut (R1, R5).
-- Le lien avec le bénévole (volunteer_id) arrive en S3 avec l'inscription ; aucune route ne crée
-- d'inscription d'ici là, la table reste donc vide en production.
CREATE TABLE registration (
    id          uuid        PRIMARY KEY,
    evening_id  uuid        NOT NULL REFERENCES evening (id),
    type        varchar(30) NOT NULL,
    dish_choice varchar(30),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT registration_type CHECK (
        type IN ('PREPARE_AND_SHARE', 'PREPARE_AT_HOME', 'PICK_UP_AND_SHARE', 'NIGHT_AND_DINNER', 'BREAKFAST')),
    -- R3 : qui prépare choisit plat principal ou accompagnements ; les autres types ne choisissent rien.
    -- IS NOT NULL explicite : sans lui, « NULL IN (...) » vaut NULL et le CHECK laisserait passer.
    CONSTRAINT registration_dish_choice CHECK (
        (type IN ('PREPARE_AND_SHARE', 'PREPARE_AT_HOME')
            AND dish_choice IS NOT NULL AND dish_choice IN ('MAIN_DISH', 'SIDES'))
        OR (type NOT IN ('PREPARE_AND_SHARE', 'PREPARE_AT_HOME') AND dish_choice IS NULL))
);

CREATE INDEX registration_evening_idx ON registration (evening_id);

-- R3 : un plat principal et un accompagnement au plus par soirée.
CREATE UNIQUE INDEX registration_one_dish_per_evening
    ON registration (evening_id, dish_choice)
    WHERE dish_choice IS NOT NULL;
