package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.UnknownEveningException;
import com.marcantoinemeunier.hiversolidaire.planning.EveningLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.CancellationDecision;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * « Mes inscriptions » : les inscriptions à venir du bénévole connecté, et seulement les siennes (aucun identifiant
 * d'inscription dans l'URL), et la désinscription (§6, R6) : page de confirmation, puis POST protégé par CSRF. La
 * soirée est désignée par sa date ; l'inscription est cherchée parmi celles du bénévole de la session.
 */
@Controller
class MyRegistrationsController {

    private final RegistrationService registrations;

    MyRegistrationsController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    record Line(LocalDate date, String label, String option, boolean cancellable) {
    }

    @GetMapping("/mes-inscriptions")
    String myRegistrations(Principal principal, Model model) {
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        List<Line> lines = registrations.upcoming(volunteerId).stream()
                .map(registration -> new Line(registration.date(), EveningLabelFormatter.format(registration.date()),
                        RegistrationChoice.choiceParam(registration.type(), registration.dishChoice()),
                        registrations.cancellable(registration.date())))
                .toList();
        model.addAttribute("registrations", lines);
        return "mes-inscriptions";
    }

    /** Page de confirmation ; après la limite de R6, elle donne le téléphone des coordinateurs, sans formulaire. */
    @GetMapping("/mes-inscriptions/annuler")
    String confirmCancellation(@RequestParam(name = RegistrationChoice.EVENING_PARAM) String evening,
            Principal principal, Model model) {
        var date = parseDate(evening);
        var registration = registrations.upcoming(RegistrationController.volunteerId(principal).orElseThrow(), date)
                .orElseThrow(UnknownEveningException::new);
        model.addAttribute("date", date);
        model.addAttribute("label", EveningLabelFormatter.format(date));
        model.addAttribute("option", RegistrationChoice.choiceParam(registration.type(), registration.dishChoice()));
        model.addAttribute("cancellable", registrations.cancellable(date));
        return "desinscription-confirmer";
    }

    @PostMapping("/mes-inscriptions/annuler")
    String cancel(@RequestParam(name = RegistrationChoice.EVENING_PARAM) String evening, Principal principal,
            RedirectAttributes redirect) {
        var date = parseDate(evening);
        var decision = registrations.cancel(RegistrationController.volunteerId(principal).orElseThrow(), date);
        if (decision == CancellationDecision.NOT_REGISTERED) {
            // Même réponse qu'une soirée inconnue : rien sur les inscriptions des autres.
            throw new UnknownEveningException();
        }
        if (decision == CancellationDecision.TOO_LATE) {
            return "redirect:/mes-inscriptions/annuler?soiree=" + date;
        }
        redirect.addFlashAttribute("cancelledLabel", EveningLabelFormatter.format(date));
        return "redirect:/mes-inscriptions";
    }

    private static LocalDate parseDate(String evening) {
        try {
            return LocalDate.parse(evening);
        } catch (DateTimeParseException e) {
            throw new UnknownEveningException();
        }
    }
}
