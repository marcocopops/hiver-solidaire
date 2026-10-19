-- Migration de référence volontairement vide (S1, squelette).
-- Elle permet à Flyway de s'exécuter réellement et de créer son historique
-- (flyway_schema_history) dès le premier déploiement, sans aucune table métier :
-- le modèle de données arrive en S2 par de nouvelles migrations (V2, ...).
-- Une migration déjà fusionnée ne se modifie jamais.
SELECT 1;
