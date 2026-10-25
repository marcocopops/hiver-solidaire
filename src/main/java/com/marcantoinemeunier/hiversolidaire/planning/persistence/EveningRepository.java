package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Aucune méthode d'écriture : les soirées sont créées par migration, puis par le back-office (S4). */
public interface EveningRepository extends Repository<EveningEntity, UUID> {

    @Query("select e from EveningEntity e where e.seasonId = :seasonId and e.date >= :from order by e.date asc")
    List<EveningEntity> findFromDate(@Param("seasonId") UUID seasonId, @Param("from") LocalDate from);

    Optional<EveningEntity> findById(UUID id);

    /**
     * R1 : verrou exclusif sur la ligne de la soirée (ADR-0005, ADR-0006), tenu jusqu'à la fin de la transaction.
     * Deux inscriptions sur la même soirée s'exécutent l'une après l'autre : la seconde voit la première.
     */
    @Query(value = "SELECT * FROM evening WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<EveningEntity> lockById(@Param("id") UUID id);
}
