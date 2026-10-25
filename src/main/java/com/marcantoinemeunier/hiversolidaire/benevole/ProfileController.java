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
 * ici : téléphone et pseudo se modifient dans « Mon compte » ({@link AccountController}). Le bénévole ne lit et
 * n'écrit que ses propres données : son identifiant vient de la session, jamais de la requête.
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
        model.addAttribute("choice", choice);
        if (form.getPseudo() == null || form.getPseudo().isBlank()) {
            // Sans JavaScript, le pseudo arrive vide : on propose « prénom + initiale », libre, à confirmer. Une
            // donnée publique n'est jamais publiée sans que le bénévole l'ait vue.
            if (!binding.hasFieldErrors("lastName") && !binding.hasFieldErrors("firstName")) {
                form.setPseudo(volunteers.freePseudo(Pseudo.suggest(form.getFirstName(), form.getLastName())));
                model.addAttribute("pseudoSuggested", true);
            }
            return "profil";
        }
        var pseudo = Pseudo.normalize(form.getPseudo());
        if (pseudo.isEmpty()) {
            binding.rejectValue("pseudo", "profile.error.pseudo");
        }
        if (binding.hasErrors()) {
            return "profil";
        }
        try {
            volunteers.completeProfile(id, form, pseudo.get());
        } catch (VolunteerService.PseudoTakenException e) {
            var variant = volunteers.freePseudo(pseudo.get());
            form.setPseudo(variant);
            model.addAttribute("pseudoTaken", variant);
            return "profil";
        }
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
