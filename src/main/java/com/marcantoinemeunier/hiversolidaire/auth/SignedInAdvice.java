package com.marcantoinemeunier.hiversolidaire.auth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Expose aux pages un simple booléen « bénévole connecté », pour la navigation. */
@ControllerAdvice
class SignedInAdvice {

    @ModelAttribute("signedIn")
    boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal;
    }
}
