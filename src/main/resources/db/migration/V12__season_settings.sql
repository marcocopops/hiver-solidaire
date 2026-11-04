-- S4b : réglages de la saison saisis par les coordinateurs (cahier des charges §6 « Saison », §11 question 2) :
-- adresse du lieu, consignes, téléphone des coordinateurs, horaires affichés par poste.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- L'adresse et les consignes ne s'affichent jamais sur la page publique, seulement dans l'espace bénévole (§6). Le
-- téléphone des coordinateurs remplace APP_COORDINATORS_PHONE dès qu'il est saisi. Aucune donnée de bénévole ici.
ALTER TABLE season
    ADD COLUMN venue_address      varchar(300),
    ADD COLUMN instructions       varchar(2000),
    ADD COLUMN coordinators_phone varchar(30);

-- Horaires affichés de chaque poste (§5, tableau « Besoins par soirée »), repris pour la saison d'exemple.
ALTER TABLE season_need ADD COLUMN schedule varchar(100);

UPDATE season_need SET schedule = CASE post
        WHEN 'MAIN_DISH' THEN 'Livré pour 19 h'
        WHEN 'SIDES' THEN 'Livrés pour 19 h'
        WHEN 'DINNER_PRESENCE' THEN '19 h → 21 h-21 h 30'
        WHEN 'NIGHT' THEN 'Du dîner au petit-déjeuner'
        WHEN 'BREAKFAST' THEN 'Le lendemain : 7 h en semaine, 8 h le week-end'
    END
WHERE season_id IN (SELECT id FROM season WHERE sample);
