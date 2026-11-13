package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.auth.Turnstile.Outcome;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vérification d'un jeton Turnstile auprès de {@code siteverify} (cahier des charges §8, ADR-0013), contre un serveur
 * HTTP local : jamais d'appel réseau réel en test. Tout ce qui n'est pas un succès explicite est un refus, et une
 * absence de réponse ne laisse jamais passer. Les logs ne donnent ni le jeton, ni la clé secrète, ni l'adresse IP.
 */
@ExtendWith(OutputCaptureExtension.class)
class CloudflareTurnstileTest {

    private static final String SECRET = "secret-de-test-0123456789";
    private static final String TOKEN = "jeton-de-test-abcdef";
    private static final String IP = "10.1.2.3";

    private HttpServer server;
    private final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{\"success\": true, \"error-codes\": []}";
    private volatile Duration delay = Duration.ZERO;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/turnstile/v0/siteverify", exchange -> {
            var form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(Arrays.stream(form.split("&")).map(pair -> pair.split("=", 2)).collect(Collectors.toMap(
                    pair -> decode(pair[0]), pair -> pair.length > 1 ? decode(pair[1]) : "")));
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private CloudflareTurnstile turnstile() {
        return new CloudflareTurnstile("site-key", SECRET,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/turnstile/v0/siteverify"),
                Duration.ofMillis(500), Duration.ofMillis(500), JsonMapper.builder().build());
    }

    @Test
    void sends_the_secret_the_token_and_the_client_ip() {
        assertThat(turnstile().verify(TOKEN, IP)).isEqualTo(Outcome.PASSED);

        assertThat(requests).singleElement()
                .isEqualTo(Map.of("secret", SECRET, "response", TOKEN, "remoteip", IP));
    }

    @Test
    void a_refused_token_fails_and_only_the_error_codes_are_logged(CapturedOutput output) {
        body = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";

        assertThat(turnstile().verify(TOKEN, IP)).isEqualTo(Outcome.FAILED);
        assertThat(output).contains("invalid-input-response")
                .doesNotContain(TOKEN).doesNotContain(SECRET).doesNotContain(IP);
    }

    @Test
    void anything_but_an_explicit_success_fails() {
        for (var response : List.of("{}", "{\"success\": \"true\"}", "{\"success\": null}", "[]")) {
            body = response;
            assertThat(turnstile().verify(TOKEN, IP)).as(response).isEqualTo(Outcome.FAILED);
        }
    }

    @Test
    void a_server_error_makes_the_check_unavailable(CapturedOutput output) {
        status = 500;
        body = "oups";

        assertThat(turnstile().verify(TOKEN, IP)).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(output).doesNotContain(TOKEN).doesNotContain(SECRET).doesNotContain(IP);
    }

    @Test
    void an_unreadable_answer_makes_the_check_unavailable() {
        body = "<html>pas du JSON</html>";

        assertThat(turnstile().verify(TOKEN, IP)).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void a_slow_answer_makes_the_check_unavailable() {
        delay = Duration.ofSeconds(2);

        assertThat(turnstile().verify(TOKEN, IP)).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void an_unreachable_server_makes_the_check_unavailable() {
        var turnstile = turnstile();
        server.stop(0);

        assertThat(turnstile.verify(TOKEN, IP)).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void a_blank_or_oversized_token_fails_without_calling_cloudflare() {
        var turnstile = turnstile();

        assertThat(turnstile.verify(null, IP)).isEqualTo(Outcome.FAILED);
        assertThat(turnstile.verify("  ", IP)).isEqualTo(Outcome.FAILED);
        assertThat(turnstile.verify("x".repeat(2049), IP)).isEqualTo(Outcome.FAILED);
        assertThat(requests).isEmpty();
    }

    @Test
    void is_configured_with_its_public_site_key() {
        var turnstile = turnstile();

        assertThat(turnstile.configured()).isTrue();
        assertThat(turnstile.siteKey()).isEqualTo("site-key");
    }
}
