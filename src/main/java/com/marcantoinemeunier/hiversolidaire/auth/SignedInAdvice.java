package com.marcantoinemeunier.hiversolidaire.auth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Expose aux pages deux booléens, « bénévole connecté » et « coordinateur », pour la navigation. */
@ControllerAdvice
class SignedInAdvice {

    @ModelAttribute("signedIn")
    boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal;
    }

    /** Pour afficher le lien du back-office ; l'accès reste vérifié par {@code SecurityConfig} sur chaque route. */
    @ModelAttribute("signedInCoordinator")
    boolean coordinator(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal principal
                && principal.coordinator();
    }
}
