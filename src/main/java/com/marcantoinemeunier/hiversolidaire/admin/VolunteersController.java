package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.benevole.IdentityForm;
import com.marcantoinemeunier.hiversolidaire.benevole.Pseudo;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerContact;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.CoordinatorPlanning;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bénévoles (cahier des charges §6) : liste avec recherche, nouveaux bénévoles signalés, fiche avec ses inscriptions,
 * correction du nom, du prénom et du pseudo, suppression sur demande (ADR-0008).
 */
@Controller
class VolunteersController {

    private final VolunteerService volunteers;
    private final CoordinatorPlanning planning;
    private final CoordinatorActions actions;

    VolunteersController(VolunteerService volunteers, CoordinatorPlanning planning, CoordinatorActions actions) {
        this.volunteers = volunteers;
        this.planning = planning;
        this.actions = actions;
    }

    /** Une inscription de la fiche : jour de l'activité, ce que fait le bénévole, passée ou à venir. */
    record RegistrationLine(String day, ActivityLabel activity, String optionKey, boolean past) {
    }

    @GetMapping("/admin/benevoles")
    String list(@RequestParam(name = "q", required = false) String query, Model model) {
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("volunteers", volunteers.search(query));
        return "admin/benevoles";
    }

    @GetMapping("/admin/benevoles/{id}")
    String sheet(@PathVariable("id") String idParam, Model model) {
        var volunteer = active(idParam);
        if (!model.containsAttribute("identityForm")) {
            model.addAttribute("identityForm", IdentityForm.of(volunteer));
        }
        return sheet(volunteer, model);
    }

    private String sheet(VolunteerContact volunteer, Model model) {
        var today = planning.today();
        model.addAttribute("volunteer", volunteer);
        model.addAttribute("profileCompleted", volunteers.profileCompleted(volunteer.id()));
        model.addAttribute("registrations", planning.registrationsOf(volunteer.id()).stream()
                .map(contact -> new RegistrationLine(contact.activityDate().toString(),
                        ActivityLabel.of(contact.type(), contact.eveningDate()),
                        "registration.option." + RegistrationChoice.choiceParam(contact.type(), contact.dishChoice()),
                        contact.eveningDate().isBefore(today)))
                .toList());
        return "admin/benevole";
    }

    @PostMapping("/admin/benevoles/{id}/identite")
    String correctIdentity(@PathVariable("id") String idParam, @Valid @ModelAttribute("identityForm") IdentityForm form,
            BindingResult binding, Principal principal, Model model, RedirectAttributes redirect) {
        var volunteer = active(idParam);
        var pseudo = Pseudo.normalize(form.getPseudo());
        if (pseudo.isEmpty()) {
            binding.rejectValue("pseudo", "profile.error.pseudo");
        }
        if (binding.hasErrors()) {
            return sheet(volunteer, model);
        }
        try {
            actions.correctIdentity(AdminSupport.coordinatorId(principal), volunteer.id(), form, pseudo.get());
        } catch (VolunteerService.PseudoTakenException e) {
            var variant = volunteers.freePseudo(pseudo.get());
            form.setPseudo(variant);
            model.addAttribute("pseudoTaken", variant);
            return sheet(volunteer, model);
        }
        redirect.addFlashAttribute("done", "admin.done.identityCorrected");
        return "redirect:/admin/benevoles/" + volunteer.id();
    }

    @GetMapping("/admin/benevoles/{id}/supprimer")
    String confirmDeletion(@PathVariable("id") String idParam, Principal principal, Model model) {
        var volunteer = active(idParam);
        model.addAttribute("volunteer", volunteer);
        model.addAttribute("self", volunteer.id().equals(AdminSupport.coordinatorId(principal)));
        model.addAttribute("upcomingCount", planning.registrationsOf(volunteer.id()).stream()
                .filter(contact -> !contact.eveningDate().isBefore(planning.today())).count());
        return "admin/benevole-supprimer";
    }

    @PostMapping("/admin/benevoles/{id}/supprimer")
    String delete(@PathVariable("id") String idParam, Principal principal, RedirectAttributes redirect) {
        var volunteer = active(idParam);
        actions.deleteVolunteer(AdminSupport.coordinatorId(principal), volunteer.id());
        redirect.addFlashAttribute("done", "admin.done.volunteerDeleted");
        return "redirect:/admin/benevoles";
    }

    private VolunteerContact active(String idParam) {
        UUID id = AdminSupport.id(idParam);
        return volunteers.contact(id).orElseThrow(AdminSupport.NotFoundException::new);
    }
}
