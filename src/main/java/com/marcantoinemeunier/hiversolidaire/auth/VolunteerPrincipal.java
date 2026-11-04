package com.marcantoinemeunier.hiversolidaire.auth;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Bénévole connecté. Son nom pour Spring Security est son identifiant technique, jamais son e-mail : rien de
 * personnel dans la session stockée en base ({@code spring_session.principal_name}) ni dans les logs.
 *
 * <p>Un coordinateur (S4b, ADR-0010) est un bénévole qui a en plus le rôle {@link #COORDINATOR_ROLE}, décidé à la
 * connexion d'après {@code APP_COORDINATOR_EMAILS}. Le record est sérialisé dans la session JDBC : une session
 * ouverte avant l'ajout de {@code coordinator} se relit avec {@code coordinator = false}.
 *
 * @param coordinator l'adresse du bénévole figurait dans {@code APP_COORDINATOR_EMAILS} à sa connexion
 */
public record VolunteerPrincipal(UUID id, boolean coordinator) implements UserDetails {

    public static final String ROLE = "VOLUNTEER";
    public static final String COORDINATOR_ROLE = "COORDINATOR";

    public VolunteerPrincipal {
        Objects.requireNonNull(id, "id");
    }

    public VolunteerPrincipal(UUID id) {
        this(id, false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return coordinator
                ? List.of(new SimpleGrantedAuthority("ROLE_" + ROLE), new SimpleGrantedAuthority("ROLE_" + COORDINATOR_ROLE))
                : List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
    }

    /** Pas de mot de passe : la connexion se fait uniquement par lien magique (ADR-0003). */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }
}
