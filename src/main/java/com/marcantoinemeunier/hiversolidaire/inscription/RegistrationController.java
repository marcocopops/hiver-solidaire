package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.UnknownEveningException;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Inscription depuis une journée du planning (cahier des charges §6, ADR-0009) : choix d'un type encore disponible,
 * confirmation par un POST protégé par CSRF, page de confirmation. Le bénévole est celui de la session, jamais un
 * paramètre. L'inscription est enregistrée sur sa soirée : la veille pour un petit-déjeuner, le jour même sinon.
 */
@Controller
class RegistrationController {

    private final RegistrationService registrations;

    RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    /** Choix d'un type, ouvert à tous : un visiteur poursuit vers la demande de lien, un bénévole vers la confirmation. */
    @GetMapping("/inscription")
    String choose(@RequestParam(name = RegistrationChoice.DAY_PARAM) String day,
            @RequestParam(name = "refus", required = false) String refusal, Principal principal, Model model) {
        var date = parseDate(day);
        var choice = registrations.choice(date, volunteerId(principal)).orElseThrow(UnknownEveningException::new);
        model.addAttribute("date", date);
        model.addAttribute("label", DayLabelFormatter.format(date));
        model.addAttribute("options", choice.available());
        model.addAttribute("blocking", choice.blocking() == null ? null : choice.blocking().name());
        model.addAttribute("refusal", knownRefusal(refusal));
        model.addAttribute("next", principal == null ? "/connexion" : "/inscription/confirmer");
        return "inscription-choix";
    }

    @GetMapping("/inscription/confirmer")
    String confirm(@RequestParam(name = RegistrationChoice.DAY_PARAM) String day,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM) String choiceParam, Model model) {
        var choice = RegistrationChoice.parse(day, choiceParam).orElseThrow(UnknownEveningException::new);
        model.addAttribute("choice", choice);
        model.addAttribute("activity", ActivityLabel.of(choice.type(), choice.eveningDate()));
        return "inscription-confirmer";
    }

    @PostMapping("/inscription")
    String register(@RequestParam(name = RegistrationChoice.DAY_PARAM) String day,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM) String choiceParam, Principal principal,
            RedirectAttributes redirect) {
        var choice = RegistrationChoice.parse(day, choiceParam).orElseThrow(UnknownEveningException::new);
        var decision = registrations.register(volunteerId(principal).orElseThrow(), choice);
        if (!decision.accepted()) {
            return "redirect:/inscription?jour=" + choice.day() + "&refus=" + decision.name();
        }
        redirect.addFlashAttribute("activity", ActivityLabel.of(choice.type(), choice.eveningDate()));
        redirect.addFlashAttribute("option", choice.choiceParam());
        return "redirect:/inscription/confirmee";
    }

    /** Page de confirmation à l'écran (l'e-mail récapitulatif arrive en S5). */
    @GetMapping("/inscription/confirmee")
    String confirmed(Model model) {
        if (!model.containsAttribute("activity")) {
            return "redirect:/mes-inscriptions";
        }
        return "inscription-confirmee";
    }

    private static LocalDate parseDate(String day) {
        try {
            return LocalDate.parse(day);
        } catch (DateTimeParseException e) {
            throw new UnknownEveningException();
        }
    }

    /** Motif de refus affiché seulement s'il est connu : la page ne recopie jamais un paramètre libre. */
    private static String knownRefusal(String refusal) {
        return Arrays.stream(RegistrationDecision.values()).map(Enum::name)
                .filter(name -> !name.equals(RegistrationDecision.ACCEPTED.name()) && name.equals(refusal))
                .findFirst().orElse(null);
    }

    /** Le nom du principal est l'identifiant technique du bénévole (jamais son e-mail). */
    static Optional<UUID> volunteerId(Principal principal) {
        return Optional.ofNullable(principal).map(p -> UUID.fromString(p.getName()));
    }
}
