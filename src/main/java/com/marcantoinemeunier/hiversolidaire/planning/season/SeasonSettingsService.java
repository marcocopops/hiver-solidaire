package com.marcantoinemeunier.hiversolidaire.planning.season;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings.NeedSetting;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réglages et création de la saison (cahier des charges §6 « Saison », ADR-0006). Une seule saison active à la fois :
 * la saison courante est la première qui n'est pas terminée ({@code SeasonRepository.findCurrent}). Les soirées sont
 * générées par {@link Season#eveningDates()} ; une soirée qui a des inscriptions actives n'est jamais supprimée.
 *
 * <p>Écrit en SQL paramétré : l'entité {@code SeasonEntity} reste en lecture seule pour le planning.
 */
@Service
public class SeasonSettingsService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    SeasonSettingsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Des soirées à supprimer ont encore des inscriptions actives : rien n'est modifié. */
    public static class EveningsWithRegistrationsException extends RuntimeException {

        private final List<LocalDate> dates;

        EveningsWithRegistrationsException(List<LocalDate> dates) {
            super("Evenings with active registrations would be removed");
            this.dates = List.copyOf(dates);
        }

        public List<LocalDate> dates() {
            return dates;
        }
    }

    /** Une vraie saison est en cours ou à venir : on la modifie, on n'en crée pas une seconde. */
    public static class SeasonAlreadyActiveException extends RuntimeException {
    }

    /** La saison d'exemple a des inscriptions actives : elle ne peut pas être remplacée. */
    public static class SampleSeasonInUseException extends RuntimeException {
    }

    /**
     * Résultat d'une création.
     *
     * @param replacedSampleId saison d'exemple supprimée par la création, ou {@code null}
     */
    public record Created(UUID seasonId, UUID replacedSampleId) {
    }

    /** Réglages de la saison courante, vide s'il n'y en a aucune. */
    @Transactional(readOnly = true)
    public Optional<SeasonSettings> current() {
        var today = ParisTime.today(clock.instant());
        var ids = jdbc.queryForList("""
                SELECT id FROM season WHERE last_evening >= ? ORDER BY first_evening ASC LIMIT 1
                """, UUID.class, today);
        if (ids.isEmpty()) {
            ids = jdbc.queryForList("SELECT id FROM season ORDER BY first_evening DESC LIMIT 1", UUID.class);
        }
        return ids.stream().findFirst().flatMap(this::find);
    }

    /** Nombre d'inscriptions actives d'une saison. */
    @Transactional(readOnly = true)
    public int activeRegistrations(UUID seasonId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.season_id = ? AND r.cancelled_at IS NULL
                """, Integer.class, seasonId);
    }

    /** Une nouvelle saison peut être créée : aucune vraie saison n'est en cours ni à venir. */
    @Transactional(readOnly = true)
    public boolean creationAllowed() {
        return jdbc.queryForObject("SELECT count(*) FROM season WHERE NOT sample AND last_evening >= ?", Integer.class,
                ParisTime.today(clock.instant())) == 0;
    }

    private Optional<SeasonSettings> find(UUID id) {
        var needs = jdbc.query("SELECT post, minimum, maximum, schedule FROM season_need WHERE season_id = ?",
                (row, index) -> new NeedSetting(Post.valueOf(row.getString("post")), row.getInt("minimum"),
                        row.getInt("maximum"), row.getString("schedule")), id);
        return jdbc.query("""
                SELECT id, first_evening, last_evening, registrations_open_at, sample, coordinators_contact, organizer
                FROM season WHERE id = ?
                """, (row, index) -> new SeasonSettings(row.getObject("id", UUID.class),
                row.getObject("first_evening", LocalDate.class), row.getObject("last_evening", LocalDate.class),
                LocalDateTime.ofInstant(row.getTimestamp("registrations_open_at").toInstant(), ParisTime.ZONE),
                row.getBoolean("sample"), needs, row.getString("coordinators_contact"),
                row.getString("organizer")), id).stream().findFirst();
    }

    /**
     * Modifie la saison et régénère ses soirées : ajoute les nouvelles dates, supprime celles qui sortent de la saison
     * avec leurs inscriptions annulées et leurs alertes, mais refuse tout si l'une d'elles a une inscription active.
     * Les soirées à supprimer sont verrouillées d'abord, dans l'ordre des dates (même verrou que l'inscription, R1) :
     * aucune inscription ne peut s'y glisser entre le contrôle et la suppression.
     *
     * @param contact contact des coordinateurs déjà validé et mis en forme, ou {@code null}
     * @throws EveningsWithRegistrationsException si une soirée à supprimer a des inscriptions actives
     */
    @Transactional
    public void update(UUID seasonId, SeasonForm form, String contact) {
        jdbc.queryForList("SELECT id FROM season WHERE id = ? FOR UPDATE", UUID.class, seasonId).stream().findFirst()
                .orElseThrow(IllegalArgumentException::new);
        var removed = jdbc.queryForList("""
                SELECT id FROM evening WHERE season_id = ? AND (evening_date < ? OR evening_date > ?)
                ORDER BY evening_date FOR UPDATE
                """, UUID.class, seasonId, form.getFirstEvening(), form.getLastEvening());
        if (!removed.isEmpty()) {
            var blocking = jdbc.queryForList("""
                    SELECT DISTINCT e.evening_date FROM evening e JOIN registration r ON r.evening_id = e.id
                    WHERE e.season_id = ? AND (e.evening_date < ? OR e.evening_date > ?) AND r.cancelled_at IS NULL
                    ORDER BY e.evening_date
                    """, LocalDate.class, seasonId, form.getFirstEvening(), form.getLastEvening());
            if (!blocking.isEmpty()) {
                throw new EveningsWithRegistrationsException(blocking);
            }
            for (var eveningId : removed) {
                deleteEvening(eveningId);
            }
        }
        jdbc.update("""
                UPDATE season SET first_evening = ?, last_evening = ?, registrations_open_at = ?,
                    coordinators_contact = ?, organizer = ?
                WHERE id = ?
                """, form.getFirstEvening(), form.getLastEvening(), opensAt(form), contact,
                SeasonForm.blankToNull(form.getOrganizer()), seasonId);
        writeNeeds(seasonId, form);
        addMissingEvenings(seasonId, form);
    }

    /**
     * Crée la saison (une seule active à la fois). Si la saison courante est la saison d'exemple, elle est supprimée
     * dans la même transaction, seulement si elle n'a aucune inscription active.
     *
     * @param contact contact des coordinateurs déjà validé et mis en forme, ou {@code null}
     * @throws SeasonAlreadyActiveException si une vraie saison est en cours ou à venir
     * @throws SampleSeasonInUseException si la saison d'exemple a des inscriptions actives
     */
    @Transactional
    public Created create(SeasonForm form, String contact) {
        // Verrou sur toutes les saisons : deux créations simultanées s'exécutent l'une après l'autre.
        jdbc.queryForList("SELECT id FROM season ORDER BY id FOR UPDATE", UUID.class);
        if (!creationAllowed()) {
            throw new SeasonAlreadyActiveException();
        }
        UUID replaced = null;
        var samples = jdbc.queryForList("SELECT id FROM season WHERE sample ORDER BY first_evening", UUID.class);
        for (var sample : samples) {
            var evenings = jdbc.queryForList("SELECT id FROM evening WHERE season_id = ? ORDER BY evening_date FOR UPDATE",
                    UUID.class, sample);
            if (activeRegistrations(sample) > 0) {
                throw new SampleSeasonInUseException();
            }
            for (var eveningId : evenings) {
                deleteEvening(eveningId);
            }
            jdbc.update("DELETE FROM season_need WHERE season_id = ?", sample);
            jdbc.update("DELETE FROM season WHERE id = ?", sample);
            replaced = sample;
        }
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO season (id, first_evening, last_evening, registrations_open_at, sample, coordinators_contact,
                    organizer)
                VALUES (?, ?, ?, ?, false, ?, ?)
                """, id, form.getFirstEvening(), form.getLastEvening(), opensAt(form), contact,
                SeasonForm.blankToNull(form.getOrganizer()));
        writeNeeds(id, form);
        addMissingEvenings(id, form);
        return new Created(id, replaced);
    }

    /** Soirée sortie de la saison, sans inscription active : ses inscriptions annulées et ses alertes partent avec. */
    private void deleteEvening(UUID eveningId) {
        jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = ?", eveningId);
        jdbc.update("DELETE FROM registration WHERE evening_id = ? AND cancelled_at IS NOT NULL", eveningId);
        jdbc.update("DELETE FROM evening WHERE id = ?", eveningId);
    }

    private void writeNeeds(UUID seasonId, SeasonForm form) {
        jdbc.update("DELETE FROM season_need WHERE season_id = ?", seasonId);
        for (var need : form.getNeeds()) {
            jdbc.update("INSERT INTO season_need (season_id, post, minimum, maximum, schedule) VALUES (?, ?, ?, ?, ?)",
                    seasonId, need.getPost().name(), need.getMinimum(), need.getMaximum(),
                    SeasonForm.blankToNull(need.getSchedule()));
        }
    }

    /** ADR-0006 : une soirée par date de la saison, bornes comprises ({@link Season#eveningDates()}). */
    private void addMissingEvenings(UUID seasonId, SeasonForm form) {
        var season = new Season(form.getFirstEvening(), form.getLastEvening(), opensAt(form).toInstant());
        var existing = new HashSet<>(jdbc.queryForList("SELECT evening_date FROM evening WHERE season_id = ?",
                LocalDate.class, seasonId));
        for (var date : season.eveningDates()) {
            if (!existing.contains(date)) {
                jdbc.update("INSERT INTO evening (id, season_id, evening_date) VALUES (?, ?, ?)", UUID.randomUUID(),
                        seasonId, date);
            }
        }
    }

    private static Timestamp opensAt(SeasonForm form) {
        return Timestamp.from(form.getRegistrationsOpenAt().atZone(ParisTime.ZONE).toInstant());
    }
}
