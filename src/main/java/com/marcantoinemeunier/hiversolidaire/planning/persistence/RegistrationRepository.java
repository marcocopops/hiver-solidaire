package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Une inscription n'est créée ou annulée que sous le verrou de la soirée ({@link EveningRepository#lockById(UUID)}).
 * Toutes les lectures ignorent les inscriptions annulées.
 */
public interface RegistrationRepository extends Repository<RegistrationEntity, UUID> {

    /** Inscriptions actives des soirées d'une saison à partir d'une date, en une seule requête. */
    @Query("""
            select r from RegistrationEntity r
            where r.cancelledAt is null and r.eveningId in (
                select e.id from EveningEntity e where e.seasonId = :seasonId and e.date >= :from)
            """)
    List<RegistrationEntity> findFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);

    /**
     * Pseudos publics des inscrits aux soirées d'une saison à partir d'une date : le pseudo, jamais le nom, l'e-mail
     * ni le téléphone. L'identifiant du bénévole ne sert qu'à reconnaître le bénévole connecté ; il n'est pas rendu.
     */
    @Query("""
            select new com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrantPseudo(
                r.eveningId, r.volunteerId, r.type, r.dishChoice, v.pseudo)
            from RegistrationEntity r, VolunteerEntity v
            where v.id = r.volunteerId and v.pseudo is not null and r.cancelledAt is null and r.eveningId in (
                select e.id from EveningEntity e where e.seasonId = :seasonId and e.date >= :from)
            """)
    List<RegistrantPseudo> findPseudosFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);

    @Query("select r from RegistrationEntity r where r.eveningId = :eveningId and r.cancelledAt is null")
    List<RegistrationEntity> findActiveByEvening(@Param("eveningId") UUID eveningId);

    /** Inscription active d'un bénévole sur une soirée (au plus une : R2). */
    @Query("""
            select r from RegistrationEntity r
            where r.eveningId = :eveningId and r.volunteerId = :volunteerId and r.cancelledAt is null
            """)
    Optional<RegistrationEntity> findActive(@Param("eveningId") UUID eveningId,
            @Param("volunteerId") UUID volunteerId);

    /** Soirées à venir où le bénévole a une inscription active, dans l'ordre des dates (ordre des verrous). */
    @Query("""
            select e.id from RegistrationEntity r, EveningEntity e
            where r.eveningId = e.id and r.volunteerId = :volunteerId and r.cancelledAt is null and e.date >= :from
            order by e.date asc
            """)
    List<UUID> findActiveEveningIds(@Param("volunteerId") UUID volunteerId, @Param("from") LocalDate from);

    /** Inscriptions actives à venir d'un bénévole, et seulement les siennes. */
    @Query("""
            select new com.marcantoinemeunier.hiversolidaire.planning.persistence.UpcomingRegistration(
                e.id, e.date, r.type, r.dishChoice)
            from RegistrationEntity r, EveningEntity e
            where r.eveningId = e.id and r.volunteerId = :volunteerId and r.cancelledAt is null and e.date >= :from
            order by e.date asc
            """)
    List<UpcomingRegistration> findUpcoming(@Param("volunteerId") UUID volunteerId, @Param("from") LocalDate from);

    RegistrationEntity saveAndFlush(RegistrationEntity registration);
}
