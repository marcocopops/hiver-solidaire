-- Lot 2 : réglages de la saison simplifiés (cahier des charges §6 « Saison »).
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Le nom de la saison (V2) n'est plus affiché ni saisi. L'adresse du lieu et les consignes (V12) ne sont plus
-- stockées : les bénévoles les connaissent déjà.
ALTER TABLE season
    DROP COLUMN label,
    DROP COLUMN venue_address,
    DROP COLUMN instructions;

-- Le téléphone des coordinateurs devient leur contact : un numéro de téléphone français ou une adresse e-mail
-- (254 caractères au plus, RFC 5321). La valeur déjà saisie, un numéro, est conservée.
ALTER TABLE season RENAME COLUMN coordinators_phone TO coordinators_contact;
ALTER TABLE season ALTER COLUMN coordinators_contact TYPE varchar(254);
