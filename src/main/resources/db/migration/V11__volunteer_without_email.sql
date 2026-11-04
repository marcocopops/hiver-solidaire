-- S4b : bénévole inscrit par téléphone par un coordinateur, sans e-mail (cahier des charges §6 « Fiche soirée »).
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

-- Un compte actif a une adresse (il s'est connecté par lien magique), ou un profil complet sans adresse (créé par un
-- coordinateur : nom, prénom, pseudo et téléphone, garantis par volunteer_profile_complete et
-- volunteer_profile_has_pseudo). Sans adresse, aucune connexion n'est possible : la connexion cherche le bénévole par
-- son adresse. Un compte supprimé reste anonyme, comme en V8. L'unicité des adresses présentes (volunteer_email_unique)
-- est inchangée : NULL n'entre en conflit avec rien.
ALTER TABLE volunteer DROP CONSTRAINT volunteer_deleted_is_anonymous;
ALTER TABLE volunteer
    ADD CONSTRAINT volunteer_deleted_is_anonymous CHECK (
        (deleted_at IS NULL AND (email IS NOT NULL OR profile_completed_at IS NOT NULL))
        OR (deleted_at IS NOT NULL AND email IS NULL AND last_name IS NULL AND first_name IS NULL
            AND phone IS NULL AND pseudo IS NULL AND profile_completed_at IS NULL
            AND recontact_next_season = false));
