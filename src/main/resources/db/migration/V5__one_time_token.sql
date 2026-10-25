-- S3 : jetons du lien magique (ADR-0003), stockés en base pour survivre à un redémarrage.
-- Seule l'empreinte SHA-256 du jeton est stockée : lire la table ne donne aucun lien utilisable.
-- username contient l'adresse e-mail normalisée, le temps de la validité du jeton (15 minutes) ; les jetons
-- expirés sont purgés toutes les heures.
CREATE TABLE one_time_token (
    token_hash char(64)     PRIMARY KEY,
    username   varchar(254) NOT NULL,
    expires_at timestamptz  NOT NULL
);

CREATE INDEX one_time_token_expires_at_idx ON one_time_token (expires_at);
