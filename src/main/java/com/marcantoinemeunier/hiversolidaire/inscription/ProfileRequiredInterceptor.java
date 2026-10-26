package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Avant toute inscription, le bénévole remplit le formulaire de première connexion (§6, étape 4) : sinon il y est
 * renvoyé, avec son choix. Enregistré sur {@code /inscription/**}, {@code /mes-inscriptions/**} et « Mon compte »
 * (sauf la suppression, voir {@code WebConfig}) ; le choix d'un type ({@code GET /inscription}), public, n'est pas
 * concerné.
 */
@Component
public class ProfileRequiredInterceptor implements HandlerInterceptor {

    private final VolunteerService volunteers;

    ProfileRequiredInterceptor(VolunteerService volunteers) {
        this.volunteers = volunteers;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        var publicChoice = "GET".equals(request.getMethod()) && "/inscription".equals(request.getRequestURI());
        var principal = request.getUserPrincipal();
        if (publicChoice || principal == null) {
            return true;
        }
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        if (volunteers.profileCompleted(volunteerId)) {
            return true;
        }
        var target = RegistrationChoice.parse(request.getParameter(RegistrationChoice.DAY_PARAM),
                request.getParameter(RegistrationChoice.CHOICE_PARAM))
                .map(choice -> choice.appendTo("/profil")).orElse("/profil");
        response.sendRedirect(request.getContextPath() + target);
        return false;
    }
}
