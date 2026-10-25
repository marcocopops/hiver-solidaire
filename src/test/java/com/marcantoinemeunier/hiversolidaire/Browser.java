package com.marcantoinemeunier.hiversolidaire;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Navigateur simulé au-dessus de MockMvc : garde le cookie de session (Spring Session), une adresse IP propre
 * (pour ne pas partager la limite de débit avec les autres tests) et lit le jeton CSRF des formulaires.
 */
public final class Browser {

    public static final String SESSION_COOKIE = "__Host-SESSION";

    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final MockMvc mockMvc;
    private final String remoteAddress;
    private Cookie session;
    private String lastCsrfToken;

    public Browser(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
        var random = ThreadLocalRandom.current();
        this.remoteAddress = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    /** Adresse e-mail factice et unique, pour ne pas partager la limite par adresse entre tests. */
    public static String uniqueEmail() {
        return "benevole-" + UUID.randomUUID() + "@example.org";
    }

    /** Pseudo factice et unique (lettres et chiffres), pour ne pas se heurter à l'unicité entre tests. */
    public static String uniquePseudo() {
        return "Test" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public MockHttpServletResponse get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    /** POST de formulaire avec le jeton CSRF de la dernière page lue. */
    public MockHttpServletResponse post(String url, String... params) throws Exception {
        var request = MockMvcRequestBuilders.post(url);
        if (lastCsrfToken != null) {
            request.param("_csrf", lastCsrfToken);
        }
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return perform(request);
    }

    /** POST sans jeton CSRF. */
    public MockHttpServletResponse postWithoutCsrf(String url, String... params) throws Exception {
        var request = MockMvcRequestBuilders.post(url);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return perform(request);
    }

    public MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        request.with(r -> {
            r.setRemoteAddr(remoteAddress);
            return r;
        });
        if (session != null) {
            request.cookie(session);
        }
        var response = mockMvc.perform(request).andReturn().getResponse();
        var cookie = response.getCookie(SESSION_COOKIE);
        if (cookie != null) {
            session = cookie.getMaxAge() == 0 ? null : cookie;
        }
        var matcher = CSRF_FIELD.matcher(response.getContentAsString());
        if (matcher.find()) {
            lastCsrfToken = matcher.group(1);
        }
        return response;
    }

    public Cookie sessionCookie() {
        return session;
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    /** Oublie le cookie de session, comme un autre navigateur ou un autre appareil. */
    public Browser otherDevice() {
        return new Browser(mockMvc);
    }
}
