package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface VolunteerRepository extends Repository<VolunteerEntity, UUID> {

    Optional<VolunteerEntity> findById(UUID id);

    @Query("select v.id from VolunteerEntity v where v.email = :email")
    Optional<UUID> findIdByEmail(@Param("email") String email);

    /** Création idempotente : deux premières connexions simultanées avec la même adresse ne créent qu'un compte. */
    @Modifying
    @Query(value = """
            INSERT INTO volunteer (id, email, created_at, updated_at)
            VALUES (:id, :email, :now, :now)
            ON CONFLICT (email) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("email") String email, @Param("now") Instant now);

    VolunteerEntity save(VolunteerEntity volunteer);
}
