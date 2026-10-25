package com.marcantoinemeunier.hiversolidaire.auth;

import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationProvider;

@Configuration
@EnableScheduling
class AuthConfig {

    /**
     * Seul moyen de s'authentifier : le jeton consommé porte l'adresse e-mail ; le bénévole est retrouvé, ou créé
     * à sa première connexion, et le principal ne garde que son identifiant technique.
     *
     * <p>Déclaré comme bean pour que Spring Boot ne crée pas d'utilisateur en mémoire par défaut (avec un mot de
     * passe généré écrit dans les logs).
     */
    @Bean
    OneTimeTokenAuthenticationProvider oneTimeTokenAuthenticationProvider(HashedJdbcOneTimeTokenService tokens,
            VolunteerService volunteers) {
        return new OneTimeTokenAuthenticationProvider(tokens,
                email -> new VolunteerPrincipal(volunteers.findOrCreate(email)));
    }
}
