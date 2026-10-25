-- Pseudo public du bénévole, affiché sur le planning (cahier des charges §4, §7).
-- Une migration déjà fusionnée ne se modifie jamais : toute évolution passe par une nouvelle migration.

ALTER TABLE volunteer ADD COLUMN pseudo varchar(20);

-- Bénévoles déjà présents (bases de développement ; en production la table est vide) : prénom + initiale du nom,
-- mêmes règles que la suggestion de l'appli (benevole.Pseudo.suggest), numéroté par ordre d'arrivée en cas de
-- doublon (« MarieD », « MarieD2 »…). Les bénévoles sans profil choisiront leur pseudo en le remplissant.
DO $$
DECLARE
    v          record;
    -- Même liste que benevole.Pseudo : sans ı, ĸ, Ŀ, ŀ, ŉ, ſ, qui imitent une lettre ASCII.
    allowed    constant text := '[^A-Za-zÀ-ÖØ-öø-ÿĀ-İĲ-ķĹ-ľŁ-ňŊ-ž0-9_-]';
    first_part text;
    initial    text;
    base       text;
    candidate  text;
    n          integer;
BEGIN
    FOR v IN SELECT id, first_name, last_name FROM volunteer
             WHERE profile_completed_at IS NOT NULL ORDER BY created_at, id LOOP
        first_part := regexp_replace(regexp_replace(normalize(coalesce(v.first_name, ''), NFC), allowed, '', 'g'),
                                     '^[_-]+', '');
        IF first_part = '' THEN
            first_part := 'Benevole';
        END IF;
        initial := upper(left(regexp_replace(regexp_replace(normalize(coalesce(v.last_name, ''), NFC), allowed, '',
                                                            'g'), '^[_-]+', ''), 1));
        base := left(first_part, 20 - char_length(initial)) || initial;
        IF char_length(base) < 3 THEN
            base := base || repeat('1', 3 - char_length(base));
        END IF;
        candidate := base;
        n := 1;
        WHILE EXISTS (SELECT 1 FROM volunteer WHERE lower(pseudo) = lower(candidate)) LOOP
            n := n + 1;
            candidate := left(base, 20 - char_length(n::text)) || n::text;
        END LOOP;
        UPDATE volunteer SET pseudo = candidate WHERE id = v.id;
    END LOOP;
END
$$;

-- Unicité sans tenir compte de la casse : « MarieD » et « maried » sont le même pseudo. lower() suit la locale de
-- la base (en_US.utf8 dans l'image postgres:17-alpine) : « Élodie » et « élodie » aussi, vérifié par un test.
CREATE UNIQUE INDEX volunteer_pseudo_unique ON volunteer (lower(pseudo));

-- Défense en profondeur : l'appli applique les règles exactes (benevole.Pseudo) ; la base refuse au moins une
-- longueur hors bornes et tout caractère de balisage ou d'espacement.
ALTER TABLE volunteer
    ADD CONSTRAINT volunteer_pseudo_shape CHECK (
        pseudo IS NULL
        OR (char_length(pseudo) BETWEEN 3 AND 20 AND pseudo !~ '[<>&"''[:space:][:cntrl:]]')),
    ADD CONSTRAINT volunteer_profile_has_pseudo CHECK (profile_completed_at IS NULL OR pseudo IS NOT NULL);
