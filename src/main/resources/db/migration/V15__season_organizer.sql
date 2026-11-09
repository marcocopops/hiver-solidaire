-- Lot 4 : organisateur de la saison (nom de la paroisse ou de l'association), affiché sur la page d'accueil.
-- Saisi par un coordinateur dans le back-office : jamais dans le dépôt. Vide : la phrase n'est pas affichée.
-- Une ligne de 100 caractères au plus, validée par le serveur (SeasonForm).
ALTER TABLE season ADD COLUMN organizer varchar(100);
