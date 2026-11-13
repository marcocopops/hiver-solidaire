package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Clés Turnstile (cahier des charges §8, ADR-0013) : sans clé, l'appli démarre et la demande de lien se déclare
 * indisponible, comme sans SMTP. Les clés de test publiées par Cloudflare (toujours valides) ne sont acceptées qu'avec
 * une origine locale : copier {@code .env.example} sur le serveur ne laisse pas passer les robots.
 */
@ExtendWith(OutputCaptureExtension.class)
class TurnstileConfigTest {

    private static final String TEST_SITE_KEY = "1x00000000000000000000AA";
    private static final String TEST_SECRET_KEY = "1x0000000000000000000000000000000AA";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TurnstileConfig.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void without_keys_the_check_is_not_configured(CapturedOutput output) {
        runner.withPropertyValues("app.base-url=https://hiver.example.org").run(context -> {
            assertThat(context.getBean(Turnstile.class).configured()).isFalse();
        });
        assertThat(output).contains("Turnstile keys are not configured");
    }

    @Test
    void with_only_one_key_the_check_is_not_configured() {
        runner.withPropertyValues("app.base-url=https://hiver.example.org", "app.turnstile.site-key=0x4AAAAAAAexemple")
                .run(context -> assertThat(context.getBean(Turnstile.class).configured()).isFalse());
    }

    @Test
    void cloudflare_test_keys_work_on_localhost() {
        runner.withPropertyValues("app.base-url=http://localhost:8080", "app.turnstile.site-key=" + TEST_SITE_KEY,
                "app.turnstile.secret-key=" + TEST_SECRET_KEY).run(context -> {
                    var turnstile = context.getBean(Turnstile.class);
                    assertThat(turnstile).isInstanceOf(CloudflareTurnstile.class);
                    assertThat(turnstile.configured()).isTrue();
                    assertThat(turnstile.siteKey()).isEqualTo(TEST_SITE_KEY);
                });
    }

    @Test
    void cloudflare_test_keys_are_refused_outside_localhost(CapturedOutput output) {
        for (var secret : new String[] {TEST_SECRET_KEY, "2x0000000000000000000000000000000AA"}) {
            runner.withPropertyValues("app.base-url=https://hiver.example.org",
                    "app.turnstile.site-key=" + TEST_SITE_KEY, "app.turnstile.secret-key=" + secret)
                    .run(context -> assertThat(context.getBean(Turnstile.class).configured()).isFalse());
        }
        runner.withPropertyValues("app.base-url=https://hiver.example.org",
                "app.turnstile.site-key=" + TEST_SITE_KEY, "app.turnstile.secret-key=0x4AAAAAAAsecretfactice")
                .run(context -> assertThat(context.getBean(Turnstile.class).configured()).isFalse());
        assertThat(output).contains("Cloudflare test keys").doesNotContain(TEST_SECRET_KEY);
    }

    @Test
    void real_keys_are_used_in_production() {
        runner.withPropertyValues("app.base-url=https://hiver.example.org",
                "app.turnstile.site-key=0x4AAAAAAAsitefactice", "app.turnstile.secret-key=0x4AAAAAAAsecretfactice")
                .run(context -> assertThat(context.getBean(Turnstile.class).configured()).isTrue());
    }
}
