package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lecture seule : aucune méthode d'écriture n'est exposée avant les fonctionnalités qui en ont besoin. */
public interface EveningRepository extends Repository<EveningEntity, UUID> {

    @Query("select e from EveningEntity e where e.seasonId = :seasonId and e.date >= :from order by e.date asc")
    List<EveningEntity> findFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);
}
