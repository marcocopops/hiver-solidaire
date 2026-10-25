package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.planning.EveningLabelFormatter;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * « Mes inscriptions », en lecture seule : les inscriptions à venir du bénévole connecté, et seulement les siennes
 * (aucun identifiant dans l'URL). La désinscription arrive en S4.
 */
@Controller
class MyRegistrationsController {

    private final RegistrationService registrations;

    MyRegistrationsController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    record Line(String label, String option) {
    }

    @GetMapping("/mes-inscriptions")
    String myRegistrations(Principal principal, Model model) {
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        List<Line> lines = registrations.upcoming(volunteerId).stream()
                .map(registration -> new Line(EveningLabelFormatter.format(registration.date()),
                        RegistrationChoice.choiceParam(registration.type(), registration.dishChoice())))
                .toList();
        model.addAttribute("registrations", lines);
        return "mes-inscriptions";
    }
}
