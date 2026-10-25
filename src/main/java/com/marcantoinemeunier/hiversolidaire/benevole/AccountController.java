package com.marcantoinemeunier.hiversolidaire.benevole;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * « Mon compte » (cahier des charges §6) : le bénévole voit ses données, modifie son téléphone et son pseudo, donne
 * ou retire son accord de recontact, supprime son compte. L'e-mail n'est pas modifiable dans cette version. Le
 * bénévole est celui de la session, jamais un paramètre : il ne lit et n'écrit que son propre compte.
 */
@Controller
class AccountController {

    private final VolunteerService volunteers;
    private final AccountDeletionService deletion;
    private final RegistrationService registrations;

    AccountController(VolunteerService volunteers, AccountDeletionService deletion,
            RegistrationService registrations) {
        this.volunteers = volunteers;
        this.deletion = deletion;
        this.registrations = registrations;
    }

    @GetMapping("/mon-compte")
    String account(Principal principal, Model model) {
        return page(ProfileController.volunteerId(principal), model);
    }

    @PostMapping("/mon-compte/telephone")
    String changePhone(Principal principal, @Valid @ModelAttribute("phoneForm") PhoneForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        var id = ProfileController.volunteerId(principal);
        if (binding.hasErrors()) {
            return page(id, model);
        }
        volunteers.changePhone(id, form);
        redirect.addFlashAttribute("saved", true);
        return "redirect:/mon-compte";
    }

    @PostMapping("/mon-compte/pseudo")
    String changePseudo(Principal principal, @ModelAttribute("pseudoForm") PseudoForm form, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        var id = ProfileController.volunteerId(principal);
        var pseudo = Pseudo.normalize(form.getPseudo());
        if (pseudo.isEmpty()) {
            binding.rejectValue("pseudo", "profile.error.pseudo");
            return page(id, model);
        }
        try {
            volunteers.changePseudo(id, pseudo.get());
        } catch (VolunteerService.PseudoTakenException e) {
            var variant = volunteers.freePseudo(pseudo.get());
            form.setPseudo(variant);
            model.addAttribute("pseudoTaken", variant);
            return page(id, model);
        }
        redirect.addFlashAttribute("saved", true);
        return "redirect:/mon-compte";
    }

    /** Case décochée : le navigateur n'envoie rien, l'accord est retiré. */
    @PostMapping("/mon-compte/recontact")
    String changeRecontact(Principal principal,
            @RequestParam(name = "recontactNextSeason", defaultValue = "false") boolean recontactNextSeason,
            RedirectAttributes redirect) {
        volunteers.changeRecontact(ProfileController.volunteerId(principal), recontactNextSeason);
        redirect.addFlashAttribute("saved", true);
        return "redirect:/mon-compte";
    }

    /** Page de confirmation explicite : ce qui est effacé, ce qui reste sans nom, les inscriptions annulées. */
    @GetMapping("/mon-compte/supprimer")
    String confirmDeletion(Principal principal, Model model) {
        var upcoming = registrations.upcoming(ProfileController.volunteerId(principal));
        model.addAttribute("upcomingCount", upcoming.size());
        model.addAttribute("pastDeadline", upcoming.stream()
                .anyMatch(registration -> !registrations.cancellable(registration.date())));
        return "compte-supprimer";
    }

    @PostMapping("/mon-compte/supprimer")
    String delete(Principal principal, Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        deletion.delete(ProfileController.volunteerId(principal));
        // Invalide la session de cette requête : sinon Spring Session la réécrirait à la fin de la requête.
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/compte-supprime";
    }

    @GetMapping("/compte-supprime")
    String deleted() {
        return "compte-supprime";
    }

    private String page(UUID id, Model model) {
        var account = volunteers.account(id);
        model.addAttribute("account", account);
        if (!model.containsAttribute("phoneForm")) {
            model.addAttribute("phoneForm", new PhoneForm());
        }
        if (!model.containsAttribute("pseudoForm")) {
            var pseudoForm = new PseudoForm();
            pseudoForm.setPseudo(account.pseudo());
            model.addAttribute("pseudoForm", pseudoForm);
        }
        return "mon-compte";
    }
}
