package com.marcantoinemeunier.hiversolidaire.planning;

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

    @GetMapping("/planning")
    String planning(Model model) {
        model.addAttribute("planning", planning.load());
        return "planning";
    }
}
