package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Une inscription n'est créée que sous le verrou de la soirée ({@link EveningRepository#lockById(UUID)}). */
public interface RegistrationRepository extends Repository<RegistrationEntity, UUID> {

    /** Inscriptions des soirées d'une saison à partir d'une date, en une seule requête. */
    @Query("""
            select r from RegistrationEntity r
            where r.eveningId in (
                select e.id from EveningEntity e where e.seasonId = :seasonId and e.date >= :from)
            """)
    List<RegistrationEntity> findFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);

    /**
     * Pseudos publics des inscrits aux soirées d'une saison à partir d'une date : seulement le pseudo, jamais le
     * nom, l'e-mail, le téléphone ni l'identifiant du bénévole.
     */
    @Query("""
            select new com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrantPseudo(
                r.eveningId, r.type, r.dishChoice, v.pseudo)
            from RegistrationEntity r, VolunteerEntity v
            where v.id = r.volunteerId and v.pseudo is not null and r.eveningId in (
                select e.id from EveningEntity e where e.seasonId = :seasonId and e.date >= :from)
            """)
    List<RegistrantPseudo> findPseudosFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);

    @Query("select r from RegistrationEntity r where r.eveningId = :eveningId")
    List<RegistrationEntity> findByEvening(@Param("eveningId") UUID eveningId);

    /** Inscriptions à venir d'un bénévole, et seulement les siennes. */
    @Query("""
            select new com.marcantoinemeunier.hiversolidaire.planning.persistence.UpcomingRegistration(
                e.id, e.date, r.type, r.dishChoice)
            from RegistrationEntity r, EveningEntity e
            where r.eveningId = e.id and r.volunteerId = :volunteerId and e.date >= :from
            order by e.date asc
            """)
    List<UpcomingRegistration> findUpcoming(@Param("volunteerId") UUID volunteerId, @Param("from") LocalDate from);

    RegistrationEntity saveAndFlush(RegistrationEntity registration);
}
