package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.CoordinatorOutcome;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * R1 sous concurrence entre une action d'un coordinateur et une inscription en ligne : le même verrou sur la soirée,
 * jamais deux personnes sur la dernière place.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class CoordinatorConcurrencyTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 2, 3);
    private static final LocalDate OTHER = LocalDate.of(2027, 2, 4);

    @Autowired
    CoordinatorActions actions;

    @Autowired
    RegistrationService registrations;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        for (var date : new LocalDate[] {EVENING, OTHER}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private UUID volunteer() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, ?, 'Martin', 'Anne', '+33600000001', ?, now())
                """, id, id + "@example.org", Browser.uniquePseudo());
        return id;
    }

    private static RegistrationChoice night(LocalDate date) {
        return RegistrationChoice.onEvening(date, RegistrationType.NIGHT_AND_DINNER, null);
    }

    private int nights(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.type = 'NIGHT_AND_DINNER' AND r.cancelled_at IS NULL
                """, Integer.class, date);
    }

    private <T> List<T> simultaneously(List<Callable<T>> attempts) throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts.size());
        try {
            var futures = new ArrayList<Future<T>>();
            for (var attempt : attempts) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return attempt.call();
                }));
            }
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void a_coordinator_registration_and_an_online_registration_on_the_last_place_only_one_succeeds()
            throws Exception {
        for (int round = 0; round < 5; round++) {
            clean();
            registrations.register(volunteer(), night(EVENING));
            var byPhone = volunteer();
            var online = volunteer();
            var coordinator = UUID.randomUUID();

            var decisions = simultaneously(List.<Callable<RegistrationDecision>>of(
                    () -> actions.register(coordinator, byPhone, night(EVENING)).decision(),
                    () -> registrations.register(online, night(EVENING))));

            assertThat(decisions).containsExactlyInAnyOrder(RegistrationDecision.ACCEPTED, RegistrationDecision.FULL);
            assertThat(nights(EVENING)).isEqualTo(2);
        }
    }

    @Test
    void a_move_and_an_online_registration_on_the_last_place_only_one_succeeds() throws Exception {
        for (int round = 0; round < 5; round++) {
            clean();
            registrations.register(volunteer(), night(EVENING));
            var moved = volunteer();
            registrations.register(moved, night(OTHER));
            var registrationId = jdbc.queryForObject(
                    "SELECT id FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL", UUID.class, moved);
            var online = volunteer();
            var coordinator = UUID.randomUUID();

            var results = simultaneously(List.<Callable<Object>>of(
                    () -> actions.move(coordinator, registrationId, EVENING).outcome(),
                    () -> registrations.register(online, night(EVENING))));

            assertThat(nights(EVENING)).isEqualTo(2);
            assertThat(results).containsAnyOf(CoordinatorOutcome.DONE, RegistrationDecision.ACCEPTED);
            assertThat(results.get(0) == CoordinatorOutcome.DONE).isNotEqualTo(results.get(1) == RegistrationDecision.ACCEPTED);
            // L'inscription déplacée n'est jamais perdue : elle est sur l'une des deux soirées.
            assertThat(nights(OTHER) + (results.get(0) == CoordinatorOutcome.DONE ? 1 : 0)).isEqualTo(1);
        }
    }

    @Test
    void two_crossed_moves_do_not_deadlock() throws Exception {
        var alice = volunteer();
        var bob = volunteer();
        registrations.register(alice, night(EVENING));
        registrations.register(bob, night(OTHER));
        var aliceRegistration = jdbc.queryForObject(
                "SELECT id FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL", UUID.class, alice);
        var bobRegistration = jdbc.queryForObject(
                "SELECT id FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL", UUID.class, bob);
        var coordinator = UUID.randomUUID();

        var outcomes = simultaneously(List.<Callable<CoordinatorOutcome>>of(
                () -> actions.move(coordinator, aliceRegistration, OTHER).outcome(),
                () -> actions.move(coordinator, bobRegistration, EVENING).outcome()));

        assertThat(outcomes).containsOnly(CoordinatorOutcome.DONE);
        assertThat(nights(EVENING)).isEqualTo(1);
        assertThat(nights(OTHER)).isEqualTo(1);
    }
}
