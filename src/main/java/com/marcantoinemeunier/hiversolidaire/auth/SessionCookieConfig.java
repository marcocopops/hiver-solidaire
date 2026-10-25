package com.marcantoinemeunier.hiversolidaire.auth;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie de session (cahier des charges §8, ADR-0007), déclaré ici plutôt que par {@code server.servlet.session
 * .cookie.*} : Spring Boot n'applique ces propriétés à Spring Session qu'avec un serveur embarqué, et les tests
 * verraient un autre cookie que la production.
 */
@Configuration
class SessionCookieConfig {

    static final String NAME = "__Host-SESSION";
    static final Duration MAX_AGE = SessionLifetimeFilter.VOLUNTEER_SESSION;

    /**
     * {@code __Host-} impose Secure, {@code Path=/} et l'absence de {@code Domain} : le cookie ne quitte pas le
     * sous-domaine (ADR-0001). Secure est forcé y compris en local : les navigateurs acceptent les cookies Secure
     * sur {@code http://localhost}.
     */
    @Bean
    DefaultCookieSerializer cookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(NAME);
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge((int) MAX_AGE.toSeconds());
        return serializer;
    }
}
