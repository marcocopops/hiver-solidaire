package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lecture seule : aucune méthode d'écriture n'est exposée avant les fonctionnalités qui en ont besoin. */
public interface SeasonRepository extends Repository<SeasonEntity, UUID> {

    @Query("select s from SeasonEntity s where s.lastEvening >= :today order by s.firstEvening asc")
    List<SeasonEntity> findNotEndedBefore(@Param("today") LocalDate today);

    Optional<SeasonEntity> findFirstByOrderByFirstEveningDesc();

    /** Saison courante : la première qui n'est pas terminée, sinon la plus récente. */
    default Optional<SeasonEntity> findCurrent(LocalDate today) {
        return findNotEndedBefore(today).stream().findFirst().or(this::findFirstByOrderByFirstEveningDesc);
    }
}
