package com.marcantoinemeunier.hiversolidaire.auth;

import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Après la connexion : note l'heure de connexion (durée absolue de 30 jours), puis envoie vers le formulaire de
 * première connexion, vers la suite du choix fait sur le planning, ou vers « Mes inscriptions ». L'adresse de
 * redirection est toujours un chemin local reconstruit ici : aucune redirection ouverte.
 */
class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final VolunteerService volunteers;
    private final Clock clock;
    private final RedirectStrategy redirect = new DefaultRedirectStrategy();

    LoginSuccessHandler(VolunteerService volunteers, Clock clock) {
        this.volunteers = volunteers;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        request.getSession().setAttribute(SessionLifetimeFilter.AUTHENTICATED_AT, clock.instant());
        var principal = (VolunteerPrincipal) authentication.getPrincipal();
        var choice = RegistrationChoice.parse(request.getParameter(RegistrationChoice.DAY_PARAM),
                request.getParameter(RegistrationChoice.CHOICE_PARAM));
        String target;
        if (!volunteers.profileCompleted(principal.id())) {
            target = "/profil";
        } else if (choice.isPresent()) {
            target = "/inscription/confirmer";
        } else {
            target = "/mes-inscriptions";
        }
        var finalTarget = target;
        redirect.sendRedirect(request, response, choice.map(c -> c.appendTo(finalTarget)).orElse(target));
    }
}
