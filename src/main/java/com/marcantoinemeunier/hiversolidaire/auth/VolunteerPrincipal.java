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
 */
public record VolunteerPrincipal(UUID id) implements UserDetails {

    public static final String ROLE = "VOLUNTEER";

    public VolunteerPrincipal {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
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
