package com.marcantoinemeunier.hiversolidaire.privacy;

import com.marcantoinemeunier.hiversolidaire.benevole.AccountDeletionService;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Purge quotidienne des données personnelles (cahier des charges §7, ADR-0014), selon {@link RetentionPolicy} :
 * <ol>
 *   <li>comptes restés sans profil depuis sept jours : supprimés, avec leurs liens de connexion et leurs sessions ;</li>
 *   <li>bénévoles sans accord « Me recontacter », un mois après la fin de la saison : anonymisés ;</li>
 *   <li>bénévoles avec accord, après trois ans sans participation : anonymisés ;</li>
 *   <li>données techniques de plus de six mois (envois d'e-mails terminés, alertes, journal d'audit) : supprimées.</li>
 * </ol>
 * L'anonymisation est celle de la suppression du compte ({@link AccountDeletionService}, ADR-0008) : les inscriptions
 * passées restent comptées, rattachées à personne. Un bénévole qui a une inscription à venir n'est jamais touché.
 *
 * <p>Idempotente : chaque étape ne retient que ce qui a dépassé sa durée et n'est pas déjà traité, donc deux exécutions
 * le même jour ne font rien de plus, et une exécution manquée est rattrapée à la suivante. Une ligne de log par
 * exécution, avec des comptes, jamais une identité.
 */
@Component
public class DataRetention {

    private static final Logger LOG = LoggerFactory.getLogger(DataRetention.class);

    /**
     * Comptes d'une exécution.
     *
     * @param incompleteAccounts comptes sans profil supprimés
     * @param withoutConsent bénévoles sans accord anonymisés
     * @param inactiveWithConsent bénévoles avec accord anonymisés après trois ans sans participation
     * @param mailDeliveries envois d'e-mails supprimés
     * @param alerts alertes aux coordinateurs supprimées
     * @param auditEntries lignes du journal d'audit supprimées
     */
    public record Report(int incompleteAccounts, int withoutConsent, int inactiveWithConsent, int mailDeliveries,
            int alerts, int auditEntries) {

        public int total() {
            return incompleteAccounts + withoutConsent + inactiveWithConsent + mailDeliveries + alerts + auditEntries;
        }
    }

    private final JdbcTemplate jdbc;
    private final AccountDeletionService accounts;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DataRetention(JdbcTemplate jdbc, AccountDeletionService accounts, PlatformTransactionManager transactions,
            Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.transactions = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Une exécution complète, à l'heure de l'horloge injectée. */
    public Report run() {
        var now = clock.instant();
        var today = ParisTime.today(now);
        var report = new Report(deleteIncompleteAccounts(now), anonymizeWithoutConsent(today),
                anonymizeInactiveWithConsent(today), deleteMailDeliveries(now), deleteAlerts(now), deleteAuditEntries(now));
        LOG.info("Data retention run: {} incomplete account(s) deleted, {} anonymized without consent, {} anonymized "
                + "after 3 years without participation, {} mail deliveries, {} alerts, {} audit entries deleted",
                report.incompleteAccounts(), report.withoutConsent(), report.inactiveWithConsent(),
                report.mailDeliveries(), report.alerts(), report.auditEntries());
        return report;
    }

    /**
     * Comptes créés à la connexion et jamais complétés : ils n'ont pas d'inscription (le profil est exigé avant), donc
     * rien à garder. Suppression de la ligne, de ses liens de connexion (ils contiennent l'adresse) et de ses sessions.
     */
    private int deleteIncompleteAccounts(Instant now) {
        var cutoff = ts(RetentionPolicy.incompleteAccountsCreatedBefore(now));
        var deleted = transactions.execute(status -> {
            var ids = jdbc.queryForList("""
                    SELECT v.id FROM volunteer v
                    WHERE v.profile_completed_at IS NULL AND v.deleted_at IS NULL AND v.created_at <= ?
                        AND NOT EXISTS (SELECT 1 FROM registration r WHERE r.volunteer_id = v.id)
                    FOR UPDATE SKIP LOCKED
                    """, UUID.class, cutoff);
            for (var id : ids) {
                jdbc.update("""
                        DELETE FROM one_time_token WHERE username = (SELECT email FROM volunteer WHERE id = ?)
                        """, id);
                // Les attributs suivent par ON DELETE CASCADE (V6), les changements d'une confirmation aussi (V17).
                jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", id.toString());
                jdbc.update("DELETE FROM mail_delivery WHERE volunteer_id = ?", id);
                jdbc.update("DELETE FROM volunteer WHERE id = ?", id);
            }
            return ids.size();
        });
        return deleted == null ? 0 : deleted;
    }

    /**
     * Saison de référence : la plus récente dont la fin plus un mois est passée. Ses bénévoles sans accord (créés avant
     * sa fin, sans inscription sur une soirée postérieure, donc pas engagés pour la saison suivante) sont anonymisés.
     * Un accord retiré après la saison est pris en compte à l'exécution suivante.
     */
    private int anonymizeWithoutConsent(LocalDate today) {
        var lastEvenings = jdbc.queryForList("SELECT last_evening FROM season", LocalDate.class);
        var season = RetentionPolicy.seasonToPurge(lastEvenings, today);
        if (season.isEmpty()) {
            return 0;
        }
        var lastEvening = season.get();
        var seasonEnd = ts(lastEvening.plusDays(1).atStartOfDay(ParisTime.ZONE).toInstant());
        var ids = jdbc.queryForList("""
                SELECT v.id FROM volunteer v
                WHERE v.deleted_at IS NULL AND NOT v.recontact_next_season AND v.created_at < ?
                    AND NOT EXISTS (
                        SELECT 1 FROM registration r JOIN evening e ON e.id = r.evening_id
                        WHERE r.volunteer_id = v.id AND r.cancelled_at IS NULL AND e.evening_date > ?)
                ORDER BY v.id
                """, UUID.class, seasonEnd, lastEvening);
        return (int) ids.stream().filter(id -> anonymizeIfIdle(id, today, false)).count();
    }

    /** Bénévoles avec accord : dernière participation (ou création du compte) il y a trois ans ou plus. */
    private int anonymizeInactiveWithConsent(LocalDate today) {
        var candidates = jdbc.query("""
                SELECT v.id, v.created_at,
                    (SELECT max(e.evening_date) FROM registration r JOIN evening e ON e.id = r.evening_id
                     WHERE r.volunteer_id = v.id AND r.cancelled_at IS NULL) AS last_participation
                FROM volunteer v
                WHERE v.deleted_at IS NULL AND v.recontact_next_season
                ORDER BY v.id
                """, (row, index) -> {
                    var last = row.getObject("last_participation", LocalDate.class);
                    var since = last != null ? last : ParisTime.today(row.getTimestamp("created_at").toInstant());
                    return RetentionPolicy.inactiveWithConsent(since, today) ? row.getObject("id", UUID.class) : null;
                });
        return (int) candidates.stream().filter(id -> id != null && anonymizeIfIdle(id, today, true)).count();
    }

    /**
     * Anonymise le bénévole, dans sa propre transaction, après avoir verrouillé sa ligne et vérifié de nouveau qu'il n'a
     * aucune inscription à venir et que son accord n'a pas changé : une inscription faite entre la sélection et
     * l'anonymisation n'est jamais annulée en silence (l'insertion d'une inscription attend le verrou de la ligne).
     */
    private boolean anonymizeIfIdle(UUID volunteerId, LocalDate today, boolean consent) {
        Boolean done = transactions.execute(status -> {
            var locked = jdbc.queryForList("""
                    SELECT id FROM volunteer WHERE id = ? AND deleted_at IS NULL AND recontact_next_season = ?
                    FOR UPDATE
                    """, UUID.class, volunteerId, consent);
            if (locked.isEmpty()) {
                return false;
            }
            var upcoming = jdbc.queryForObject("""
                    SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                    WHERE r.volunteer_id = ? AND r.cancelled_at IS NULL AND e.evening_date >= ?
                    """, Integer.class, volunteerId, today);
            if (upcoming != null && upcoming > 0) {
                return false;
            }
            accounts.delete(volunteerId);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    /** Envois terminés (envoyés ou abandonnés) ; les changements regroupés suivent par ON DELETE CASCADE (V17). */
    private int deleteMailDeliveries(Instant now) {
        return jdbc.update("""
                DELETE FROM mail_delivery
                WHERE (sent_at IS NOT NULL OR abandoned_at IS NOT NULL) AND created_at <= ?
                """, ts(RetentionPolicy.technicalDataCreatedBefore(now)));
    }

    /**
     * Alertes aux coordinateurs : une alerte porte sur une soirée de la saison à venir, donc passée six mois plus tard.
     * Son e-mail éventuel suit par ON DELETE CASCADE (V17).
     */
    private int deleteAlerts(Instant now) {
        return jdbc.update("DELETE FROM coordinator_alert WHERE created_at <= ?",
                ts(RetentionPolicy.technicalDataCreatedBefore(now)));
    }

    /**
     * Journal d'audit (V19) : la base n'accepte de supprimer que les lignes de plus de six mois à sa propre horloge. La
     * requête reprend cette limite, pour qu'une horloge de l'appli en avance ne fasse jamais échouer la purge.
     */
    private int deleteAuditEntries(Instant now) {
        return jdbc.update("""
                DELETE FROM audit_log WHERE occurred_at <= ? AND occurred_at <= now() - interval '6 months'
                """, ts(RetentionPolicy.technicalDataCreatedBefore(now)));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
