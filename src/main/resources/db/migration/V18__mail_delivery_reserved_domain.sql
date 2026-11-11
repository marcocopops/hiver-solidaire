-- S5b : garde-fou d'envoi (cahier des charges §7, modèle de menaces M30). Un e-mail dont tous les destinataires sont
-- dans un domaine réservé aux tests (RFC 2606, RFC 6761) est abandonné sans erreur, avec sa propre raison.
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.
ALTER TABLE mail_delivery
    DROP CONSTRAINT mail_delivery_abandon_reason,
    ADD CONSTRAINT mail_delivery_abandon_reason CHECK (
        (abandoned_at IS NULL AND abandon_reason IS NULL)
        -- IS NOT NULL explicite : sans lui, « NULL IN (...) » vaut NULL et le CHECK laisserait passer.
        OR (abandoned_at IS NOT NULL AND abandon_reason IS NOT NULL
            AND abandon_reason IN ('NO_RECIPIENT', 'NOTHING_TO_SEND', 'EXPIRED', 'FAILED', 'RESERVED_DOMAIN')));
