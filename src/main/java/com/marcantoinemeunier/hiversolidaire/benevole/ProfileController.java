package com.marcantoinemeunier.hiversolidaire.benevole;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Formulaire de première connexion (cahier des charges §6, étape 4). Une fois rempli, il n'est plus modifiable
 * ici : la modification du téléphone arrive avec « Mon compte » (S4). Le bénévole ne lit et n'écrit que ses
 * propres données : son identifiant vient de la session, jamais de la requête.
 */
@Controller
class ProfileController {

    private final VolunteerService volunteers;

    ProfileController(VolunteerService volunteers) {
        this.volunteers = volunteers;
    }

    @GetMapping("/profil")
    String form(Principal principal,
            @RequestParam(name = RegistrationChoice.EVENING_PARAM, required = false) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            Model model) {
        var choice = RegistrationChoice.parse(evening, choiceParam).orElse(null);
        if (volunteers.profileCompleted(volunteerId(principal))) {
            return "redirect:" + next(choice);
        }
        model.addAttribute("profileForm", new ProfileForm());
        model.addAttribute("choice", choice);
        return "profil";
    }

    @PostMapping("/profil")
    String submit(Principal principal, @Valid @ModelAttribute("profileForm") ProfileForm form,
            BindingResult binding,
            @RequestParam(name = RegistrationChoice.EVENING_PARAM, required = false) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            Model model) {
        var choice = RegistrationChoice.parse(evening, choiceParam).orElse(null);
        var id = volunteerId(principal);
        if (volunteers.profileCompleted(id)) {
            return "redirect:" + next(choice);
        }
        if (binding.hasErrors()) {
            model.addAttribute("choice", choice);
            return "profil";
        }
        volunteers.completeProfile(id, form);
        return "redirect:" + next(choice);
    }

    @GetMapping("/notice")
    String notice() {
        return "notice";
    }

    /** Suite du parcours : confirmation du choix fait sur le planning, sinon « Mes inscriptions ». */
    private static String next(RegistrationChoice choice) {
        return choice == null ? "/mes-inscriptions" : choice.appendTo("/inscription/confirmer");
    }

    /** Le nom du principal est l'identifiant technique du bénévole (jamais son e-mail). */
    static UUID volunteerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
