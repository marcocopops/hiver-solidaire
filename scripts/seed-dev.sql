-- Jeu de données de DÉVELOPPEMENT (lot 1), lancé par scripts/seed-dev.sh dans le conteneur PostgreSQL de développement.
-- Jamais en production : ce fichier n'est pas une migration Flyway et l'appli ne le connaît pas.
--
-- Tout est factice : bénévoles prenom.nom@example.org, téléphones de la plage réservée à la fiction par l'ARCEP
-- (06 39 98 00 xx, 01 99 00 01 xx), aucune donnée sur les personnes accueillies.
--
-- Remplit environ 60 % des soirées des 4 premières semaines de la saison (dates relatives à sa première soirée), en
-- respectant R1 à R4 et R7 par construction, puis le vérifie (bloc de contrôle à la fin : une violation annule tout).
-- Idempotent : identifiants fixes et ON CONFLICT DO NOTHING ; relancé, il n'ajoute rien.
-- Identifiants : bénévoles 5eed5eed-0000-…, inscriptions 5eed5eed-0001-…, alertes 5eed5eed-0002-….
-- SQL pur, sans méta-commande psql : SeedDevScriptTest exécute ce même fichier. À exécuter dans une transaction
-- (seed-dev.sh : psql --single-transaction).

-- Garde-fou : schéma migré par Flyway jusqu'à V12 au moins, une seule saison, au moins 4 semaines de soirées.
DO $$
DECLARE
    schema_version integer;
    seasons        integer;
    s              record;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'seed-dev : schéma inattendu (aucune migration Flyway) ; démarrer l''appli une fois pour migrer la base';
    END IF;
    SELECT max(version::integer) INTO schema_version
    FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';
    IF schema_version IS NULL OR schema_version < 12 THEN
        RAISE EXCEPTION 'seed-dev : schéma inattendu (version % au lieu de 12 ou plus) ; démarrer l''appli une fois pour migrer la base',
            schema_version;
    END IF;

    SELECT count(*) INTO seasons FROM season;
    IF seasons <> 1 THEN
        RAISE EXCEPTION 'seed-dev : il faut une seule saison en base (trouvé : %)', seasons;
    END IF;
    SELECT * INTO s FROM season;
    IF (SELECT count(*) FROM evening
        WHERE season_id = s.id AND evening_date BETWEEN s.first_evening AND s.first_evening + 27) < 28 THEN
        RAISE EXCEPTION 'seed-dev : la saison doit compter au moins 28 soirées consécutives';
    END IF;
    IF s.registrations_open_at > (s.first_evening + TIME '12:00') AT TIME ZONE 'Europe/Paris' THEN
        RAISE EXCEPTION 'seed-dev : les inscriptions doivent ouvrir avant la première soirée';
    END IF;
    -- Les gabarits ci-dessous supposent au moins les maximums de 2025-2026 (§5).
    IF EXISTS (SELECT 1 FROM (VALUES ('MAIN_DISH', 1), ('SIDES', 1), ('DINNER_PRESENCE', 2), ('NIGHT', 2),
                                     ('BREAKFAST', 1)) AS required (post, maximum)
               LEFT JOIN season_need n ON n.season_id = s.id AND n.post = required.post
               WHERE n.maximum IS NULL OR n.maximum < required.maximum) THEN
        RAISE EXCEPTION 'seed-dev : besoins de la saison trop bas (il faut au moins 1 plat, 1 accompagnement, 2 présences, 2 nuits, 1 petit-déjeuner)';
    END IF;
END
$$;

-- Bénévoles : 20 avec e-mail, 1 inscrit par téléphone (sans e-mail). Les 4 derniers arrivés ne sont pas encore « vus »
-- par un coordinateur (tableau de bord, « nouveaux bénévoles »).
CREATE TEMP TABLE seed_volunteer ON COMMIT DROP AS
SELECT ('5eed5eed-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid AS id, n, first_name, last_name, email, phone,
       first_name || left(last_name, 1) AS pseudo
FROM (VALUES
    (1, 'Camille', 'Martin', 'camille.martin@example.org', '+33639980001'),
    (2, 'Lucas', 'Bernard', 'lucas.bernard@example.org', '+33639980002'),
    (3, 'Léa', 'Dubois', 'lea.dubois@example.org', '+33639980003'),
    (4, 'Hugo', 'Thomas', 'hugo.thomas@example.org', '+33639980004'),
    (5, 'Chloé', 'Robert', 'chloe.robert@example.org', '+33639980005'),
    (6, 'Louis', 'Richard', 'louis.richard@example.org', '+33639980006'),
    (7, 'Manon', 'Petit', 'manon.petit@example.org', '+33639980007'),
    (8, 'Jules', 'Durand', 'jules.durand@example.org', '+33639980008'),
    (9, 'Inès', 'Leroy', 'ines.leroy@example.org', '+33639980009'),
    (10, 'Gabriel', 'Moreau', 'gabriel.moreau@example.org', '+33639980010'),
    (11, 'Sarah', 'Simon', 'sarah.simon@example.org', '+33639980011'),
    (12, 'Arthur', 'Laurent', 'arthur.laurent@example.org', '+33639980012'),
    (13, 'Zoé', 'Lefebvre', 'zoe.lefebvre@example.org', '+33639980013'),
    (14, 'Paul', 'Michel', 'paul.michel@example.org', '+33639980014'),
    (15, 'Jeanne', 'Garcia', 'jeanne.garcia@example.org', '+33639980015'),
    (16, 'Nathan', 'David', 'nathan.david@example.org', '+33639980016'),
    (17, 'Alice', 'Bertrand', 'alice.bertrand@example.org', '+33639980017'),
    (18, 'Théo', 'Roux', 'theo.roux@example.org', '+33639980018'),
    (19, 'Clara', 'Vincent', 'clara.vincent@example.org', '+33639980019'),
    (20, 'Victor', 'Fournier', 'victor.fournier@example.org', '+33639980020'),
    (21, 'Odette', 'Morel', NULL, '+33199000101')
) AS v (n, first_name, last_name, email, phone);

-- Arrivés juste après l'ouverture, avant leur première inscription (R7).
INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, recontact_next_season, created_at, updated_at,
                       profile_completed_at, reviewed_at)
SELECT v.id, v.email, v.last_name, v.first_name, v.phone, v.pseudo, v.n % 3 = 0, at, at, at,
       CASE WHEN v.n BETWEEN 17 AND 20 THEN NULL ELSE at + INTERVAL '1 day' END
FROM seed_volunteer v
CROSS JOIN LATERAL (SELECT (SELECT registrations_open_at FROM season) + (v.n - 1) * INTERVAL '2 minutes' AS at) t
ON CONFLICT DO NOTHING;

-- Gabarits de soirée : une ligne par inscription, rang = position dans la soirée.
-- cancel : NULL (active), EARLY (annulée peu après, sans conséquence), LATE (annulée 3 jours avant : soirée devenue
-- incomplète, alerte aux coordinateurs).
CREATE TEMP TABLE seed_template ON COMMIT DROP AS
SELECT * FROM (VALUES
    -- Soirée complète : plat et accompagnements préparés et partagés (2 présences), nuit, petit-déjeuner.
    ('COMPLETE', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', NULL),
    ('COMPLETE', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('COMPLETE', 2, 'NIGHT_AND_DINNER', NULL, NULL),
    ('COMPLETE', 3, 'BREAKFAST', NULL, NULL),
    -- Complète avec deux bénévoles de nuit (maximum).
    ('COMPLETE_2N', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', NULL),
    ('COMPLETE_2N', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('COMPLETE_2N', 2, 'NIGHT_AND_DINNER', NULL, NULL),
    ('COMPLETE_2N', 3, 'BREAKFAST', NULL, NULL),
    ('COMPLETE_2N', 4, 'NIGHT_AND_DINNER', NULL, NULL),
    -- R4 : plat préparé chez soi, avec sa récupération ; complète.
    ('HOME_PICKUP', 0, 'PREPARE_AT_HOME', 'MAIN_DISH', NULL),
    ('HOME_PICKUP', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('HOME_PICKUP', 2, 'PICK_UP_AND_SHARE', NULL, NULL),
    ('HOME_PICKUP', 3, 'NIGHT_AND_DINNER', NULL, NULL),
    ('HOME_PICKUP', 4, 'BREAKFAST', NULL, NULL),
    -- R4 : plat préparé chez soi sans récupération (tableau de bord) ; une présence et le petit-déjeuner manquent.
    ('HOME_ALONE', 0, 'PREPARE_AT_HOME', 'MAIN_DISH', NULL),
    ('HOME_ALONE', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('HOME_ALONE', 2, 'NIGHT_AND_DINNER', NULL, NULL),
    -- Presque complète : seul le petit-déjeuner (journée du lendemain) manque.
    ('PARTIAL_HIGH', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', NULL),
    ('PARTIAL_HIGH', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('PARTIAL_HIGH', 2, 'NIGHT_AND_DINNER', NULL, NULL),
    -- À moitié : plat et petit-déjeuner ; la nuit s'est désistée tôt.
    ('PARTIAL_MID', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', NULL),
    ('PARTIAL_MID', 1, 'BREAKFAST', NULL, NULL),
    ('PARTIAL_MID', 2, 'NIGHT_AND_DINNER', NULL, 'EARLY'),
    -- Presque vide : une nuit.
    ('PARTIAL_LOW', 0, 'NIGHT_AND_DINNER', NULL, NULL),
    -- Plat annulé puis repris par quelqu'un d'autre : complète.
    ('CANCEL_REPLACED', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', 'EARLY'),
    ('CANCEL_REPLACED', 1, 'PREPARE_AND_SHARE', 'MAIN_DISH', NULL),
    ('CANCEL_REPLACED', 2, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('CANCEL_REPLACED', 3, 'NIGHT_AND_DINNER', NULL, NULL),
    ('CANCEL_REPLACED', 4, 'BREAKFAST', NULL, NULL),
    -- Plat annulé 3 jours avant, non repris : trou, alerte « soirée devenue incomplète ».
    ('CANCEL_HOLE', 0, 'PREPARE_AND_SHARE', 'MAIN_DISH', 'LATE'),
    ('CANCEL_HOLE', 1, 'PREPARE_AND_SHARE', 'SIDES', NULL),
    ('CANCEL_HOLE', 2, 'NIGHT_AND_DINNER', NULL, NULL),
    ('CANCEL_HOLE', 3, 'BREAKFAST', NULL, NULL)
) AS t (template, rank, type, dish_choice, cancel);

-- Soirées remplies : indice (0 = première soirée), gabarit, rang tenu par le bénévole sans e-mail. 17 sur 28 : les
-- 7 premières journées sont complètes, puis des journées incomplètes à divers degrés, de plus en plus clairsemées.
CREATE TEMP TABLE seed_plan ON COMMIT DROP AS
SELECT * FROM (VALUES
    (0, 'COMPLETE', NULL), (1, 'COMPLETE', NULL), (2, 'HOME_PICKUP', NULL), (3, 'COMPLETE_2N', 4),
    (4, 'CANCEL_REPLACED', NULL), (5, 'COMPLETE', NULL), (6, 'PARTIAL_HIGH', NULL),
    (7, 'COMPLETE', NULL), (8, 'HOME_ALONE', NULL), (10, 'PARTIAL_MID', NULL), (11, 'CANCEL_HOLE', NULL),
    (13, 'COMPLETE', NULL),
    (14, 'PARTIAL_LOW', 0), (16, 'COMPLETE', NULL), (18, 'PARTIAL_MID', NULL),
    (21, 'COMPLETE', NULL), (23, 'PARTIAL_HIGH', NULL)
) AS p (idx, template, phone_rank);

-- Soirées à sauter : elles portent déjà une inscription active qui ne vient pas de ce script (inscription à la main
-- depuis le dernier --reset). On n'y ajoute rien, pour garantir R1 à R3 sans dépendre de ce qui s'y trouve.
CREATE TEMP TABLE seed_evening ON COMMIT DROP AS
SELECT p.idx, p.template, p.phone_rank, e.id AS evening_id, e.evening_date, s.registrations_open_at,
       EXISTS (SELECT 1 FROM registration r
               WHERE r.evening_id = e.id AND r.cancelled_at IS NULL
                 AND r.id::text NOT LIKE '5eed5eed-0001-%') AS skipped
FROM seed_plan p
CROSS JOIN season s
JOIN evening e ON e.season_id = s.id AND e.evening_date = s.first_evening + p.idx;

-- R2 par construction : sur une soirée, le rang (0 à 4) donne des bénévoles tous distincts parmi les 20 avec e-mail ;
-- le bénévole sans e-mail (n° 21) remplace celui du rang qui lui est attribué. Le script ne cumule aucun poste : il
-- reste compatible avec une base en V13 (une inscription par bénévole et par soirée), ce qui permet de tester la
-- migration V14 sur une base remplie (SeedDevScriptTest).
-- R7 par construction : créée après l'ouverture, au plus tard le jour de la soirée à midi.
CREATE TEMP TABLE seed_registration ON COMMIT DROP AS
SELECT ('5eed5eed-0001-4000-8000-' || lpad((e.idx * 10 + t.rank)::text, 12, '0'))::uuid AS id,
       e.evening_id, e.evening_date, e.idx, t.template, t.type, t.dish_choice, t.cancel,
       ('5eed5eed-0000-4000-8000-' || lpad((CASE WHEN t.rank = e.phone_rank THEN 21
                                                ELSE (e.idx * 5 + t.rank) % 20 + 1 END)::text, 12, '0'))::uuid
           AS volunteer_id,
       least(e.registrations_open_at + INTERVAL '2 hours' + e.idx * INTERVAL '1 hour' + t.rank * INTERVAL '10 minutes',
             (e.evening_date + TIME '12:00') AT TIME ZONE 'Europe/Paris') AS created_at
FROM seed_evening e
JOIN seed_template t ON t.template = e.template
WHERE NOT e.skipped;

-- EARLY : 5 minutes après l'inscription (avant la reprise éventuelle, créée 10 minutes après la précédente).
-- LATE : 3 jours avant la soirée à 20 h, dans le délai de R6 (la veille à 23 h 59).
INSERT INTO registration (id, evening_id, type, dish_choice, volunteer_id, created_at, cancelled_at)
SELECT r.id, r.evening_id, r.type, r.dish_choice, r.volunteer_id, r.created_at,
       CASE r.cancel
           WHEN 'EARLY' THEN r.created_at + INTERVAL '5 minutes'
           WHEN 'LATE' THEN greatest(r.created_at + INTERVAL '5 minutes',
                                     (r.evening_date - 3 + TIME '20:00') AT TIME ZONE 'Europe/Paris')
       END
FROM seed_registration r
JOIN volunteer v ON v.id = r.volunteer_id AND v.deleted_at IS NULL
ORDER BY r.created_at
ON CONFLICT DO NOTHING;

-- Alerte non traitée pour la soirée devenue incomplète (ADR-0008), écrite au moment de l'annulation.
INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
SELECT ('5eed5eed-0002-4000-8000-' || lpad(r.idx::text, 12, '0'))::uuid, 'EVENING_INCOMPLETE', r.evening_id,
       registration.cancelled_at
FROM seed_registration r
JOIN registration ON registration.id = r.id AND registration.cancelled_at IS NOT NULL
WHERE r.cancel = 'LATE'
ON CONFLICT DO NOTHING;

-- Contrôle final, sur toutes les inscriptions (celles du script et celles faites à la main) : une violation annule
-- toute la transaction. Le contrôle de R4 ne porte que sur ce script : la désinscription d'une préparation chez soi
-- déjà récupérée est acceptée (lot 3) et laisse légitimement une récupération sans repas.
DO $$
DECLARE
    violations integer;
    s          record;
    target     date;
    offset_days integer;
BEGIN
    -- R1 : occupation de chaque poste au plus égale au maximum de la saison (postes de RegistrationType).
    SELECT count(*) INTO violations
    FROM (SELECT r.evening_id, covered.post, count(*) AS occupied
          FROM registration r
          CROSS JOIN LATERAL (VALUES
              (CASE WHEN r.type IN ('PREPARE_AND_SHARE', 'PREPARE_AT_HOME') THEN r.dish_choice END),
              (CASE WHEN r.type IN ('PREPARE_AND_SHARE', 'PICK_UP_AND_SHARE') THEN 'DINNER_PRESENCE' END),
              (CASE WHEN r.type = 'NIGHT_AND_DINNER' THEN 'NIGHT' END),
              (CASE WHEN r.type = 'BREAKFAST' THEN 'BREAKFAST' END)) AS covered (post)
          WHERE r.cancelled_at IS NULL AND covered.post IS NOT NULL
          GROUP BY r.evening_id, covered.post) o
    JOIN evening e ON e.id = o.evening_id
    JOIN season_need n ON n.season_id = e.season_id AND n.post = o.post
    WHERE o.occupied > n.maximum;
    IF violations > 0 THEN
        RAISE EXCEPTION 'seed-dev : contrôle R1 (capacité) en échec, % poste(s) au-delà du maximum ; rien n''a été écrit', violations;
    END IF;

    -- R2 (lot 3, ADR-0011) : un poste par bénévole et par soirée (le plat pour qui prépare, le type sinon), et
    -- « Récupérer » seulement avec le petit-déjeuner, jamais avec un autre poste du même dîner.
    SELECT count(*) INTO violations
    FROM (SELECT 1 FROM registration WHERE cancelled_at IS NULL
          GROUP BY evening_id, volunteer_id, COALESCE(dish_choice, type) HAVING count(*) > 1) d;
    SELECT violations + count(*) INTO violations
    FROM (SELECT 1 FROM registration WHERE cancelled_at IS NULL
          GROUP BY evening_id, volunteer_id
          HAVING count(*) FILTER (WHERE type = 'PICK_UP_AND_SHARE') > 0
             AND count(*) FILTER (WHERE type NOT IN ('PICK_UP_AND_SHARE', 'BREAKFAST')) > 0) d;
    IF violations > 0 THEN
        RAISE EXCEPTION 'seed-dev : contrôle R2 (un poste par bénévole et par soirée) en échec, % violation(s) ; rien n''a été écrit', violations;
    END IF;

    -- R3 : qui prépare choisit un plat, les autres non ; un plat principal et un accompagnement au plus par soirée.
    SELECT count(*) INTO violations
    FROM registration
    WHERE (type IN ('PREPARE_AND_SHARE', 'PREPARE_AT_HOME')) <> (dish_choice IS NOT NULL);
    SELECT violations + count(*) INTO violations
    FROM (SELECT 1 FROM registration WHERE cancelled_at IS NULL AND dish_choice IS NOT NULL
          GROUP BY evening_id, dish_choice HAVING count(*) > 1) d;
    IF violations > 0 THEN
        RAISE EXCEPTION 'seed-dev : contrôle R3 (préparation) en échec, % violation(s) ; rien n''a été écrit', violations;
    END IF;

    -- R4 : dans les données du script, pas de récupération sans repas préparé chez soi à récupérer.
    SELECT count(*) INTO violations
    FROM (SELECT evening_id FROM registration
          WHERE cancelled_at IS NULL AND id::text LIKE '5eed5eed-0001-%'
          GROUP BY evening_id
          HAVING count(*) FILTER (WHERE type = 'PICK_UP_AND_SHARE')
                 > count(*) FILTER (WHERE type = 'PREPARE_AT_HOME')) d;
    IF violations > 0 THEN
        RAISE EXCEPTION 'seed-dev : contrôle R4 (récupération) en échec sur % soirée(s) ; rien n''a été écrit', violations;
    END IF;

    -- R7 : soirée de la saison, inscription après l'ouverture et au plus tard le jour de la soirée (heure de Paris) ;
    -- annulation après l'inscription et avant la fin de la soirée. Bénévole actif.
    SELECT count(*) INTO violations
    FROM registration r
    JOIN evening e ON e.id = r.evening_id
    JOIN season se ON se.id = e.season_id
    JOIN volunteer v ON v.id = r.volunteer_id
    WHERE e.evening_date NOT BETWEEN se.first_evening AND se.last_evening
       OR r.created_at < se.registrations_open_at
       OR (r.created_at AT TIME ZONE 'Europe/Paris')::date > e.evening_date
       OR r.cancelled_at <= r.created_at
       OR r.cancelled_at >= (e.evening_date + 1)::timestamp AT TIME ZONE 'Europe/Paris'
       OR (r.cancelled_at IS NULL AND v.deleted_at IS NOT NULL);
    IF violations > 0 THEN
        RAISE EXCEPTION 'seed-dev : contrôle R7 (fenêtre) en échec, % inscription(s) ; rien n''a été écrit', violations;
    END IF;

    -- Résumé et décalage d'horloge conseillé : 3 jours avant la première soirée (ou l'ouverture, si elle est plus
    -- tard), pour voir les 4 semaines comme à venir et pouvoir encore s'inscrire.
    SELECT * INTO s FROM season;
    target := greatest(s.first_evening - 3, (s.registrations_open_at AT TIME ZONE 'Europe/Paris')::date);
    offset_days := target - current_date;
    RAISE NOTICE 'seed-dev : % bénévoles factices, % inscriptions actives, % annulées, % alerte(s) ; % soirée(s) sautée(s) car déjà remplies à la main',
        (SELECT count(*) FROM volunteer WHERE id::text LIKE '5eed5eed-0000-%'),
        (SELECT count(*) FROM registration WHERE id::text LIKE '5eed5eed-0001-%' AND cancelled_at IS NULL),
        (SELECT count(*) FROM registration WHERE id::text LIKE '5eed5eed-0001-%' AND cancelled_at IS NOT NULL),
        (SELECT count(*) FROM coordinator_alert WHERE id::text LIKE '5eed5eed-0002-%'),
        (SELECT count(*) FROM seed_evening WHERE skipped);
    IF offset_days > 0 THEN
        RAISE NOTICE 'seed-dev : lancer l''appli avec APP_CLOCK_OFFSET=P%D (horloge au %) pour voir ces soirées comme à venir',
            offset_days, to_char(target, 'DD/MM/YYYY');
    ELSE
        RAISE NOTICE 'seed-dev : aucun décalage d''horloge utile, la saison a déjà commencé (les journées passées ne s''affichent plus)';
    END IF;
END
$$;
