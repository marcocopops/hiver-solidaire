package com.marcantoinemeunier.hiversolidaire.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vérification auprès de {@code siteverify}, avec le client HTTP du JDK (aucune dépendance de plus). Délais courts :
 * le bénévole attend la réponse. Tout ce qui n'est pas un succès explicite est un refus ; une erreur réseau, un délai
 * dépassé, une erreur HTTP ou une réponse illisible rendent la vérification indisponible, et la demande est refusée.
 *
 * <p>Logs : l'issue et les codes d'erreur de Cloudflare seulement ; jamais le jeton, la clé secrète ni l'adresse IP.
 */
class CloudflareTurnstile implements Turnstile {

    static final URI SITEVERIFY = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
    /** Longueur maximale d'un jeton, selon la documentation de Cloudflare. */
    static final int MAX_TOKEN_LENGTH = 2048;

    private static final Logger LOG = LoggerFactory.getLogger(CloudflareTurnstile.class);

    private final String siteKey;
    private final String secretKey;
    private final URI verifyUrl;
    private final Duration responseTimeout;
    private final HttpClient http;
    private final JsonMapper json;

    CloudflareTurnstile(String siteKey, String secretKey, URI verifyUrl, Duration connectTimeout,
            Duration responseTimeout, JsonMapper json) {
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
        this.responseTimeout = responseTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.json = json;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String siteKey() {
        return siteKey;
    }

    @Override
    public Outcome verify(String token, String remoteIp) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Outcome.FAILED;
        }
        var form = new LinkedHashMap<String, String>();
        form.put("secret", secretKey);
        form.put("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.put("remoteip", remoteIp);
        }
        var request = HttpRequest.newBuilder(verifyUrl).timeout(responseTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Turnstile check unavailable ({})", e.getClass().getSimpleName());
            return Outcome.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.UNAVAILABLE;
        }
        if (response.statusCode() != 200) {
            LOG.warn("Turnstile check unavailable (HTTP {})", response.statusCode());
            return Outcome.UNAVAILABLE;
        }
        try {
            var body = json.readTree(response.body());
            var success = body.get("success");
            if (success != null && success.isBoolean() && success.booleanValue()) {
                return Outcome.PASSED;
            }
            var codes = new ArrayList<String>();
            var errors = body.get("error-codes");
            if (errors != null && errors.isArray()) {
                errors.forEach(code -> codes.add(code.asString()));
            }
            // Codes fixes de Cloudflare (invalid-input-response, timeout-or-duplicate…) : aucune donnée personnelle.
            LOG.info("Turnstile check failed {}", codes);
            return Outcome.FAILED;
        } catch (JacksonException e) {
            LOG.warn("Turnstile check unavailable (unreadable answer)");
            return Outcome.UNAVAILABLE;
        }
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
