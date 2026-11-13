package com.marcantoinemeunier.hiversolidaire.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Mise en cache du contrôle de la base : une page publique martelée ne doit pas solliciter la base à chaque appel. */
class PublicHealthTest {

    private final AtomicLong nanos = new AtomicLong(1_000);
    private final AtomicInteger checks = new AtomicInteger();
    private final AtomicBoolean databaseUp = new AtomicBoolean(true);

    private PublicHealth health(Duration ttl) {
        return new PublicHealth(() -> {
            checks.incrementAndGet();
            if (!databaseUp.get()) {
                throw new IllegalStateException("down");
            }
        }, ttl, nanos::get);
    }

    @Test
    void reuses_the_last_result_within_the_ttl() {
        var health = health(Duration.ofSeconds(10));

        assertThat(health.up()).isTrue();
        databaseUp.set(false);
        nanos.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(health.up()).isTrue();
        assertThat(checks).hasValue(1);

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(health.up()).isFalse();
        assertThat(checks).hasValue(2);
    }

    @Test
    void a_failed_check_is_also_cached() {
        databaseUp.set(false);
        var health = health(Duration.ofSeconds(10));

        assertThat(health.up()).isFalse();
        databaseUp.set(true);
        assertThat(health.up()).isFalse();

        nanos.addAndGet(Duration.ofSeconds(11).toNanos());
        assertThat(health.up()).isTrue();
    }

    @Test
    void zero_ttl_checks_every_time() {
        var health = health(Duration.ZERO);

        health.up();
        health.up();

        assertThat(checks).hasValue(2);
    }
}
