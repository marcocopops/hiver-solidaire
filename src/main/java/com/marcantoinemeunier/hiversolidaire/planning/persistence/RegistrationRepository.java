package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lecture seule : la création d'une inscription arrive en S3, avec le verrou de R1. */
public interface RegistrationRepository extends Repository<RegistrationEntity, UUID> {

    /** Inscriptions des soirées d'une saison à partir d'une date, en une seule requête. */
    @Query("""
            select r from RegistrationEntity r
            where r.eveningId in (
                select e.id from EveningEntity e where e.seasonId = :seasonId and e.date >= :from)
            """)
    List<RegistrationEntity> findFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);
}
