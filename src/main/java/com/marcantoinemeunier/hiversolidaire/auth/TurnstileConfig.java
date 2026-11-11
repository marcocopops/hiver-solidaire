package com.marcantoinemeunier.hiversolidaire.auth;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Clés Turnstile ({@code TURNSTILE_SITE_KEY}, {@code TURNSTILE_SECRET_KEY}, ADR-0013). Sans clé, l'appli démarre et la
 * demande de lien affiche « momentanément indisponible », comme sans SMTP : le planning public et le plan B restent en
 * ligne. Les clés de test publiées par Cloudflare (qui laissent tout passer, ou bloquent tout) ne sont acceptées qu'avec
 * une origine locale ({@code APP_BASE_URL}) : copier {@code .env.example} sur le serveur n'ouvre pas la porte aux robots.
 */
@Configuration
class TurnstileConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TurnstileConfig.class);

    /** Clés de test de Cloudflare : « 1x », « 2x » ou « 3x », des zéros, puis deux lettres (AA, AB, BB, FF…). */
    private static final Pattern TEST_KEY = Pattern.compile("^[0-9]x0+[A-F]{2}$");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    @Bean
    Turnstile turnstile(@Value("${app.turnstile.site-key:}") String siteKey,
            @Value("${app.turnstile.secret-key:}") String secretKey,
            @Value("${app.turnstile.verify-url:}") String verifyUrl,
            @Value("${app.base-url}") String baseUrl, JsonMapper json) {
        if (!StringUtils.hasText(siteKey) || !StringUtils.hasText(secretKey)) {
            LOG.warn("Turnstile keys are not configured: magic link login is unavailable");
            return new UnconfiguredTurnstile();
        }
        var site = siteKey.strip();
        var secret = secretKey.strip();
        if ((TEST_KEY.matcher(site).matches() || TEST_KEY.matcher(secret).matches()) && !local(baseUrl)) {
            LOG.warn("Cloudflare test keys are only allowed on localhost: magic link login is unavailable");
            return new UnconfiguredTurnstile();
        }
        var url = StringUtils.hasText(verifyUrl) ? URI.create(verifyUrl.strip()) : CloudflareTurnstile.SITEVERIFY;
        return new CloudflareTurnstile(site, secret, url, Duration.ofSeconds(3), Duration.ofSeconds(5), json);
    }

    private static boolean local(String baseUrl) {
        try {
            var host = URI.create(baseUrl.strip()).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
