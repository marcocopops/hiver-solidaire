package com.marcantoinemeunier.hiversolidaire.planning;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Planning public, en lecture seule, sans authentification (cahier des charges, §4). */
@Controller
class PlanningController {

    private final PublicPlanningService planning;

    PlanningController(PublicPlanningService planning) {
        this.planning = planning;
    }

    /** Le nom du principal, s'il y en a un, est l'identifiant technique du bénévole connecté. */
    @GetMapping("/planning")
    String planning(Principal principal, Model model) {
        var volunteerId = Optional.ofNullable(principal).map(p -> UUID.fromString(p.getName()));
        model.addAttribute("planning", planning.load(volunteerId));
        return "planning";
    }
}
