package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Instant;
import java.util.List;
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

    /** Pseudo déjà pris, sans tenir compte de la casse (même règle que l'index unique {@code lower(pseudo)}). */
    @Query("select count(v) > 0 from VolunteerEntity v where lower(v.pseudo) = lower(:pseudo)")
    boolean pseudoTaken(@Param("pseudo") String pseudo);

    /** Pseudo pris par un autre bénévole que celui-ci : on peut garder le sien ou en changer la casse. */
    @Query("select count(v) > 0 from VolunteerEntity v where lower(v.pseudo) = lower(:pseudo) and v.id <> :id")
    boolean pseudoTakenByOther(@Param("pseudo") String pseudo, @Param("id") UUID id);

    /** Nouveaux bénévoles à signaler (§2) : profil rempli, compte actif, pas encore vus par un coordinateur. */
    @Query("""
            select v from VolunteerEntity v
            where v.profileCompletedAt is not null and v.deletedAt is null and v.reviewedAt is null
            order by v.createdAt asc, v.id asc
            """)
    List<VolunteerEntity> findToReview();

    /** Bénévoles actifs au profil rempli, par nom puis prénom : ceux qu'un coordinateur peut inscrire. */
    @Query("""
            select v from VolunteerEntity v
            where v.profileCompletedAt is not null and v.deletedAt is null
            order by lower(v.lastName) asc, lower(v.firstName) asc, v.id asc
            """)
    List<VolunteerEntity> findActiveWithProfile();

    VolunteerEntity saveAndFlush(VolunteerEntity volunteer);
}
