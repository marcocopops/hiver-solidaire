package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.UnknownEveningException;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
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
 * soirée est désignée par sa date ({@code soiree}, unique par bénévole grâce à R2) ; l'inscription est cherchée parmi
 * celles du bénévole de la session. Les libellés donnent le jour de l'activité, comme le planning (ADR-0009), et la
 * date limite calculée de R6.
 */
@Controller
class MyRegistrationsController {

    private final RegistrationService registrations;

    MyRegistrationsController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    /** Paramètre de la soirée à annuler : la date du soir J, pas celle de la carte du planning. */
    static final String EVENING_PARAM = "soiree";

    record Line(LocalDate date, ActivityLabel activity, String option, boolean cancellable, String deadline) {
    }

    @GetMapping("/mes-inscriptions")
    String myRegistrations(Principal principal, Model model) {
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        List<Line> lines = registrations.upcoming(volunteerId).stream()
                .map(registration -> new Line(registration.date(),
                        ActivityLabel.of(registration.type(), registration.date()),
                        RegistrationChoice.choiceParam(registration.type(), registration.dishChoice()),
                        registrations.cancellable(registration.date()), deadline(registration.date())))
                .toList();
        model.addAttribute("registrations", lines);
        return "mes-inscriptions";
    }

    /** Page de confirmation ; après la limite de R6, elle donne le téléphone des coordinateurs, sans formulaire. */
    @GetMapping("/mes-inscriptions/annuler")
    String confirmCancellation(@RequestParam(name = EVENING_PARAM) String evening,
            Principal principal, Model model) {
        var date = parseDate(evening);
        var registration = registrations.upcoming(RegistrationController.volunteerId(principal).orElseThrow(), date)
                .orElseThrow(UnknownEveningException::new);
        model.addAttribute("date", date);
        model.addAttribute("activity", ActivityLabel.of(registration.type(), date));
        model.addAttribute("option", RegistrationChoice.choiceParam(registration.type(), registration.dishChoice()));
        model.addAttribute("cancellable", registrations.cancellable(date));
        model.addAttribute("deadline", deadline(date));
        return "desinscription-confirmer";
    }

    @PostMapping("/mes-inscriptions/annuler")
    String cancel(@RequestParam(name = EVENING_PARAM) String evening, Principal principal,
            RedirectAttributes redirect) {
        var date = parseDate(evening);
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        // Lue avant l'annulation, pour le message : seulement l'inscription du bénévole de la session.
        var registration = registrations.upcoming(volunteerId, date);
        var decision = registrations.cancel(volunteerId, date);
        if (decision == CancellationDecision.NOT_REGISTERED) {
            // Même réponse qu'une soirée inconnue : rien sur les inscriptions des autres.
            throw new UnknownEveningException();
        }
        if (decision == CancellationDecision.TOO_LATE) {
            return "redirect:/mes-inscriptions/annuler?soiree=" + date;
        }
        redirect.addFlashAttribute("cancelled", ActivityLabel.of(registration.orElseThrow().type(), date));
        return "redirect:/mes-inscriptions";
    }

    /** R6 : « jeu. 7 janv. à 23 h 59 », la veille de la soirée, petit-déjeuner compris. */
    private static String deadline(LocalDate eveningDate) {
        return DayLabelFormatter.deadline(Cancellation.deadline(eveningDate));
    }

    private static LocalDate parseDate(String evening) {
        try {
            return LocalDate.parse(evening);
        } catch (DateTimeParseException e) {
            throw new UnknownEveningException();
        }
    }
}
