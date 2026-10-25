package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;

/** Stockage des jetons du lien magique : empreinte seule en base, usage unique atomique, 15 minutes. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class HashedJdbcOneTimeTokenServiceTest {

    private static final String EMAIL = "token-test@example.org";

    @Autowired
    HashedJdbcOneTimeTokenService tokens;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetClock() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
    }

    private OneTimeToken generate() {
        return tokens.generate(new GenerateOneTimeTokenRequest(EMAIL, Duration.ofMinutes(15)));
    }

    private OneTimeToken consume(String value) {
        return tokens.consume(OneTimeTokenAuthenticationToken.unauthenticated(value));
    }

    @Test
    void only_the_hash_of_the_token_is_stored() {
        var token = generate();

        assertThat(token.getTokenValue()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE token_hash = ?", Integer.class,
                token.getTokenValue())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE token_hash = ?", Integer.class,
                HashedJdbcOneTimeTokenService.hash(token.getTokenValue()))).isOne();
    }

    @Test
    void peeking_reads_the_address_without_consuming_the_token() {
        var token = generate();

        assertThat(tokens.peekUsername(token.getTokenValue())).contains(EMAIL);
        assertThat(tokens.peekUsername(token.getTokenValue())).contains(EMAIL);
        assertThat(consume(token.getTokenValue())).isNotNull();
        assertThat(tokens.peekUsername(token.getTokenValue())).isEmpty();
    }

    @Test
    void peeking_an_expired_or_unknown_token_finds_nothing() {
        var token = generate();
        clock.advance(Duration.ofMinutes(15));

        assertThat(tokens.peekUsername(token.getTokenValue())).isEmpty();
        assertThat(tokens.peekUsername("not-a-token")).isEmpty();
        assertThat(tokens.peekUsername(null)).isEmpty();
    }

    @Test
    void tokens_are_unpredictable() {
        assertThat(generate().getTokenValue()).isNotEqualTo(generate().getTokenValue());
    }

    @Test
    void token_is_valid_for_15_minutes() {
        var token = generate();

        assertThat(token.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(consume(token.getTokenValue())).isNotNull()
                .extracting(OneTimeToken::getUsername).isEqualTo(EMAIL);
    }

    @Test
    void token_is_single_use() {
        var token = generate();

        assertThat(consume(token.getTokenValue())).isNotNull();
        assertThat(consume(token.getTokenValue())).isNull();
    }

    @Test
    void expired_token_is_refused() {
        var token = generate();

        clock.advance(Duration.ofMinutes(15));
        assertThat(consume(token.getTokenValue())).isNull();
    }

    @Test
    void unknown_or_malformed_token_is_refused() {
        assertThat(consume("not-a-token")).isNull();
        assertThat(consume("")).isNull();
        assertThat(consume("x".repeat(10_000))).isNull();
    }

    @Test
    void concurrent_consumptions_of_the_same_token_succeed_only_once() throws Exception {
        var token = generate();
        var threads = 8;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var results = new ArrayList<Future<OneTimeToken>>();
            for (int i = 0; i < threads; i++) {
                Callable<OneTimeToken> attempt = () -> {
                    start.await();
                    return consume(token.getTokenValue());
                };
                results.add(executor.submit(attempt));
            }
            start.countDown();
            var successes = 0;
            for (var result : results) {
                if (result.get() != null) {
                    successes++;
                }
            }
            assertThat(successes).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cleanup_removes_expired_tokens_only() {
        var expired = generate();
        clock.advance(Duration.ofMinutes(20));
        var fresh = generate();

        tokens.deleteExpiredTokens();

        assertThat(stored(expired)).isFalse();
        assertThat(stored(fresh)).isTrue();
    }

    private boolean stored(OneTimeToken token) {
        return jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE token_hash = ?", Integer.class,
                HashedJdbcOneTimeTokenService.hash(token.getTokenValue())) == 1;
    }
}
