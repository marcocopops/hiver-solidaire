package com.marcantoinemeunier.hiversolidaire.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Durée absolue de la session d'un bénévole : 30 jours après la connexion, même si elle sert tous les jours
 * (ADR-0007). Spring Session borne l'inactivité à 30 jours ; ce filtre borne la durée totale. Les coordinateurs
 * (8 h, S4) s'y brancheront avec leur propre durée.
 *
 * <p>Ce n'est pas un bean : il n'est ajouté qu'à la chaîne de sécurité, jamais au conteneur de servlets.
 */
final class SessionLifetimeFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_AT = SessionLifetimeFilter.class.getName() + ".AUTHENTICATED_AT";
    static final Duration VOLUNTEER_SESSION = Duration.ofDays(30);

    private final Clock clock;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    SessionLifetimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = contexts.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal) {
            var session = request.getSession(false);
            var authenticatedAt = session == null ? null : session.getAttribute(AUTHENTICATED_AT);
            if (!(authenticatedAt instanceof Instant at) || !clock.instant().isBefore(at.plus(VOLUNTEER_SESSION))) {
                if (session != null) {
                    session.invalidate();
                }
                contexts.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
