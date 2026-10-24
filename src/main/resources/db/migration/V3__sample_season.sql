-- SAISON D'EXEMPLE, DATES FICTIVES.
-- Les dates réelles de la saison 2026-2027 ne sont pas encore connues (cahier des charges §11.2).
-- Cette saison, marquée sample = true et signalée comme telle sur la page publique, est calquée sur
-- 2025-2026 (76 soirées, du lundi soir au dimanche matin) : du lundi 4 janvier au soir au dimanche
-- 21 mars 2027 au matin. Elle sera remplacée depuis le back-office des coordinateurs (S4).
-- Aucune inscription, aucune donnée personnelle, aucune adresse.
WITH sample_season AS (
    INSERT INTO season (id, label, first_evening, last_evening, registrations_open_at, sample)
    VALUES (gen_random_uuid(), 'Saison d''exemple (dates à confirmer)',
            DATE '2027-01-04', DATE '2027-03-20', TIMESTAMPTZ '2026-12-14 00:00:00 Europe/Paris', true)
    RETURNING id, first_evening, last_evening
),
sample_needs AS (
    INSERT INTO season_need (season_id, post, minimum, maximum)
    SELECT sample_season.id, need.post, need.minimum, need.maximum
    FROM sample_season,
         (VALUES ('MAIN_DISH', 1, 1),
                 ('SIDES', 1, 1),
                 ('DINNER_PRESENCE', 2, 2),
                 ('NIGHT', 1, 2),
                 ('BREAKFAST', 1, 1)) AS need (post, minimum, maximum)
)
INSERT INTO evening (id, season_id, evening_date)
SELECT gen_random_uuid(), sample_season.id, day::date
FROM sample_season,
     generate_series(sample_season.first_evening, sample_season.last_evening, INTERVAL '1 day') AS day;
