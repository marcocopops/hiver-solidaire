-- Lot 3 : R2 assouplie, un poste par bénévole et par soirée (ADR-0011), et alerte « récupération sans repas » (R4).
-- Une migration déjà fusionnée ne se modifie jamais : V4 et V8 restent telles quelles, celle-ci les complète.

-- R2 : un bénévole cumule des postes distincts d'une soirée (plat principal, accompagnements, nuit, petit-déjeuner),
-- mais ne prend pas deux fois le même. Le poste d'une inscription est son plat pour qui prépare (chez soi ou en
-- partageant), et son type sinon (récupération, nuit, petit-déjeuner) : COALESCE(dish_choice, type), le même découpage
-- que Registration.ownPost() dans le domaine. Cet index est moins strict que celui qu'il remplace : toute base
-- existante le respecte. Les règles qui croisent deux postes (« Récupérer » seulement avec le petit-déjeuner, pas plus
-- de récupérations que de repas préparés chez soi) restent vérifiées sous le verrou de la soirée, comme R1.
DROP INDEX registration_one_active_per_volunteer_and_evening;
CREATE UNIQUE INDEX registration_one_active_post_per_volunteer_and_evening
    ON registration (evening_id, volunteer_id, (COALESCE(dish_choice, type)))
    WHERE cancelled_at IS NULL;

-- R4 : la désinscription d'une préparation chez soi déjà récupérée est acceptée ; la récupération reste, sans repas, et
-- les coordinateurs sont alertés. Comme les autres alertes, la soirée seule, aucune donnée personnelle.
ALTER TABLE coordinator_alert
    DROP CONSTRAINT coordinator_alert_kind,
    ADD CONSTRAINT coordinator_alert_kind CHECK (kind IN ('EVENING_INCOMPLETE', 'PICKUP_WITHOUT_MEAL'));
