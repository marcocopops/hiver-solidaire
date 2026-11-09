package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.UnknownEveningException;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.CancellationDecision;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * « Mes inscriptions » : les inscriptions à venir du bénévole connecté, et seulement les siennes (aucun identifiant
 * d'inscription dans l'URL), et la désinscription (§6, R6) : page de confirmation, puis POST protégé par CSRF.
 * L'inscription est désignée par sa soirée ({@code soiree}, date du soir J) et son choix ({@code choix}, type et plat),
 * uniques ensemble pour un bénévole grâce à R2 (un poste par bénévole et par soirée, ADR-0011) ; elle est cherchée
 * parmi celles du bénévole de la session. Les libellés donnent le jour de l'activité, comme le planning (ADR-0009), et
 * la date limite calculée de R6.
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

    /** Page de confirmation ; après la limite de R6, elle donne le contact des coordinateurs, sans formulaire. */
    @GetMapping("/mes-inscriptions/annuler")
    String confirmCancellation(@RequestParam(name = EVENING_PARAM) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            Principal principal, Model model) {
        var choice = parse(evening, choiceParam);
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        var date = choice.eveningDate();
        registrations.upcoming(volunteerId, date, choice.type(), choice.dishChoice())
                .orElseThrow(UnknownEveningException::new);
        model.addAttribute("date", date);
        model.addAttribute("activity", ActivityLabel.of(choice.type(), date));
        model.addAttribute("option", choice.choiceParam());
        model.addAttribute("cancellable", registrations.cancellable(date));
        model.addAttribute("deadline", deadline(date));
        model.addAttribute("pickupWarning",
                registrations.leavesPickupWithoutMeal(volunteerId, date, choice.type(), choice.dishChoice()));
        return "desinscription-confirmer";
    }

    @PostMapping("/mes-inscriptions/annuler")
    String cancel(@RequestParam(name = EVENING_PARAM) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            Principal principal, RedirectAttributes redirect) {
        var choice = parse(evening, choiceParam);
        var volunteerId = RegistrationController.volunteerId(principal).orElseThrow();
        var decision = registrations.cancel(volunteerId, choice.eveningDate(), choice.type(), choice.dishChoice());
        if (decision == CancellationDecision.NOT_REGISTERED) {
            // Même réponse qu'une soirée inconnue : rien sur les inscriptions des autres.
            throw new UnknownEveningException();
        }
        if (decision == CancellationDecision.TOO_LATE) {
            return "redirect:" + UriComponentsBuilder.fromPath("/mes-inscriptions/annuler")
                    .queryParam(EVENING_PARAM, choice.eveningDate())
                    .queryParam(RegistrationChoice.CHOICE_PARAM, choice.choiceParam())
                    .encode().build().toUriString();
        }
        redirect.addFlashAttribute("cancelled", ActivityLabel.of(choice.type(), choice.eveningDate()));
        return "redirect:/mes-inscriptions";
    }

    /** R6 : « jeu. 7 janv. à 23 h 59 », la veille de la soirée, petit-déjeuner compris. */
    private static String deadline(LocalDate eveningDate) {
        return DayLabelFormatter.deadline(Cancellation.deadline(eveningDate));
    }

    /** Soirée et choix relus, jamais recopiés ; un paramètre manquant ou inconnu répond comme une soirée inconnue. */
    private static RegistrationChoice parse(String evening, String choiceParam) {
        return RegistrationChoice.parseOnEvening(evening, choiceParam).orElseThrow(UnknownEveningException::new);
    }
}
