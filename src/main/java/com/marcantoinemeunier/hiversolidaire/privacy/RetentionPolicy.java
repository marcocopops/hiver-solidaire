package com.marcantoinemeunier.hiversolidaire.privacy;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Durées de conservation retenues (cahier des charges §7, ADR-0014), jusqu'à avis contraire de l'association. Java pur,
 * sans Spring : la purge ({@link DataRetention}) lui passe l'heure de l'horloge injectée. Les dates sont celles de Paris.
 * La notice d'information affiche ces mêmes durées.
 */
public final class RetentionPolicy {

    /** Sans accord « Me recontacter » : anonymisation un mois après la fin de la saison. */
    public static final Period AFTER_SEASON_WITHOUT_CONSENT = Period.ofMonths(1);
    /** Avec accord : conservation jusqu'au retrait, et au plus trois ans sans participation. */
    public static final Period WITHOUT_PARTICIPATION_WITH_CONSENT = Period.ofYears(3);
    /** Compte créé mais resté sans profil : supprimé au bout de sept jours. */
    public static final Duration INCOMPLETE_ACCOUNT = Duration.ofDays(7);
    /** Données techniques (envois d'e-mails, alertes, journal d'audit) : six mois au plus. */
    public static final Period TECHNICAL_DATA = Period.ofMonths(6);

    private RetentionPolicy() {
    }

    /**
     * Date à partir de laquelle les bénévoles sans accord d'une saison sont anonymisés : la saison finit au
     * petit-déjeuner qui suit sa dernière soirée, et un mois passe.
     */
    public static LocalDate withoutConsentFrom(LocalDate lastEvening) {
        return lastEvening.plusDays(1).plus(AFTER_SEASON_WITHOUT_CONSENT);
    }

    /** Dernière soirée de la saison la plus récente dont le délai est passé, ou vide si aucune. */
    public static Optional<LocalDate> seasonToPurge(Collection<LocalDate> lastEvenings, LocalDate today) {
        return lastEvenings.stream().filter(last -> !withoutConsentFrom(last).isAfter(today))
                .max(Comparator.naturalOrder());
    }

    /**
     * Vrai si un bénévole qui a donné son accord n'a pas participé depuis trois ans.
     *
     * @param lastParticipation soirée de sa dernière inscription non annulée, ou date de création de son compte
     */
    public static boolean inactiveWithConsent(LocalDate lastParticipation, LocalDate today) {
        return !lastParticipation.plus(WITHOUT_PARTICIPATION_WITH_CONSENT).isAfter(today);
    }

    /** Les comptes sans profil créés à cet instant ou avant sont supprimés. */
    public static Instant incompleteAccountsCreatedBefore(Instant now) {
        return now.minus(INCOMPLETE_ACCOUNT);
    }

    /** Les données techniques créées à cet instant ou avant sont supprimées (six mois, à l'heure de Paris). */
    public static Instant technicalDataCreatedBefore(Instant now) {
        return now.atZone(ParisTime.ZONE).minus(TECHNICAL_DATA).toInstant();
    }
}
