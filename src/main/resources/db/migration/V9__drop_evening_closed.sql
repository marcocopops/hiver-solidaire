-- S4 : plus de soirée fermée (R7, ADR-0009). Le planning s'affiche par journée et un coordinateur ne ferme plus de
-- soirée : la colonne disparaît. Les inscriptions ne sont pas touchées.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.
ALTER TABLE evening DROP COLUMN closed;
