package com.marcantoinemeunier.hiversolidaire.auth;

import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import java.time.Clock;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.stereotype.Component;

/**
 * Branche le One-Time Token Login de Spring Security sur la chaîne publique (ADR-0003, ADR-0007) :
 * <ul>
 *   <li>{@code POST /login/ott} consomme le jeton (CSRF obligatoire), change l'identifiant de session ;</li>
 *   <li>la page {@code GET /login/ott} est la nôtre ({@link LoginController}), la page par défaut est coupée ;</li>
 *   <li>le point de génération de Spring ({@code /ott/generate}) est neutralisé : son résolveur ne renvoie
 *       jamais de demande, la requête retombe sur le refus par défaut. La génération passe par
 *       {@code POST /connexion} ;</li>
 *   <li>déconnexion par {@code POST /deconnexion}.</li>
 * </ul>
 */
@Component
public class MagicLinkLogin {

    private final HashedJdbcOneTimeTokenService tokens;
    private final OneTimeTokenAuthenticationProvider provider;
    private final VolunteerService volunteers;
    private final Clock clock;

    MagicLinkLogin(HashedJdbcOneTimeTokenService tokens, OneTimeTokenAuthenticationProvider provider,
            VolunteerService volunteers, Clock clock) {
        this.tokens = tokens;
        this.provider = provider;
        this.volunteers = volunteers;
        this.clock = clock;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .oneTimeTokenLogin(ott -> ott
                        .tokenService(tokens)
                        .authenticationProvider(provider)
                        .loginPage("/connexion")
                        .loginProcessingUrl("/login/ott")
                        .showDefaultSubmitPage(false)
                        .generateRequestResolver(request -> null)
                        .tokenGenerationSuccessHandler(new RedirectOneTimeTokenGenerationSuccessHandler("/connexion"))
                        .successHandler(new LoginSuccessHandler(volunteers, clock))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("/connexion?lien-invalide")))
                .logout(logout -> logout.logoutUrl("/deconnexion").logoutSuccessUrl("/"))
                .addFilterAfter(new SessionLifetimeFilter(clock), SecurityContextHolderFilter.class);
    }
}
