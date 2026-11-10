-- Les horaires par poste (V12) ne sont plus saisis dans le back-office : ils étaient enregistrés mais affichés nulle
-- part. Les horaires sont des textes fixes de l'appli (messages.properties), comme sur la page d'accueil.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.
ALTER TABLE season_need DROP COLUMN schedule;
