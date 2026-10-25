package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
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

/** R1 et R2 sous concurrence : la base garantit qu'il n'y a jamais deux personnes sur la dernière place. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class RegistrationConcurrencyTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 2, 10);

    @Autowired
    RegistrationService registrations;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                EVENING);
    }

    private UUID volunteer() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, profile_completed_at)
                VALUES (?, ?, 'Martin', 'Anne', '0600000001', now())
                """, id, id + "@example.org");
        return id;
    }

    private static RegistrationChoice choice(RegistrationType type, DishChoice dish) {
        return new RegistrationChoice(EVENING, type, dish);
    }

    /** Lance toutes les tentatives en même temps et renvoie leurs décisions. */
    private List<RegistrationDecision> simultaneously(List<Callable<RegistrationDecision>> attempts)
            throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts.size());
        try {
            var futures = new ArrayList<Future<RegistrationDecision>>();
            for (var attempt : attempts) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return attempt.call();
                }));
            }
            start.countDown();
            var decisions = new ArrayList<RegistrationDecision>();
            for (var future : futures) {
                decisions.add(future.get());
            }
            return decisions;
        } finally {
            executor.shutdownNow();
        }
    }

    private int count(String type) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.type = ?
                """, Integer.class, EVENING, type);
    }

    @Test
    void two_simultaneous_registrations_on_the_last_night_place_only_one_succeeds() throws Exception {
        assertThat(registrations.register(volunteer(), choice(RegistrationType.NIGHT_AND_DINNER, null)))
                .isEqualTo(RegistrationDecision.ACCEPTED);
        var alice = volunteer();
        var bob = volunteer();

        var decisions = simultaneously(List.of(
                () -> registrations.register(alice, choice(RegistrationType.NIGHT_AND_DINNER, null)),
                () -> registrations.register(bob, choice(RegistrationType.NIGHT_AND_DINNER, null))));

        assertThat(decisions).containsExactlyInAnyOrder(RegistrationDecision.ACCEPTED, RegistrationDecision.FULL);
        assertThat(count("NIGHT_AND_DINNER")).isEqualTo(2);
    }

    @Test
    void many_simultaneous_registrations_on_one_dinner_presence_left_only_one_succeeds() throws Exception {
        registrations.register(volunteer(), choice(RegistrationType.PICK_UP_AND_SHARE, null));
        var attempts = new ArrayList<Callable<RegistrationDecision>>();
        for (int i = 0; i < 6; i++) {
            var candidate = volunteer();
            attempts.add(() -> registrations.register(candidate, choice(RegistrationType.PICK_UP_AND_SHARE, null)));
        }

        var decisions = simultaneously(attempts);

        assertThat(decisions).filteredOn(RegistrationDecision::accepted).hasSize(1);
        assertThat(count("PICK_UP_AND_SHARE")).isEqualTo(2);
    }

    @Test
    void two_simultaneous_registrations_on_the_main_dish_only_one_succeeds() throws Exception {
        var alice = volunteer();
        var bob = volunteer();

        var decisions = simultaneously(List.of(
                () -> registrations.register(alice, choice(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH)),
                () -> registrations.register(bob, choice(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH))));

        assertThat(decisions).filteredOn(RegistrationDecision::accepted).hasSize(1);
        assertThat(decisions).filteredOn(decision -> !decision.accepted())
                .containsExactly(RegistrationDecision.DISH_TAKEN);
    }

    @Test
    void the_same_volunteer_registering_twice_at_once_gets_a_single_registration() throws Exception {
        var alice = volunteer();

        var decisions = simultaneously(List.of(
                () -> registrations.register(alice, choice(RegistrationType.BREAKFAST, null)),
                () -> registrations.register(alice, choice(RegistrationType.NIGHT_AND_DINNER, null))));

        assertThat(decisions).containsExactlyInAnyOrder(
                RegistrationDecision.ACCEPTED, RegistrationDecision.ALREADY_REGISTERED);
    }
}
