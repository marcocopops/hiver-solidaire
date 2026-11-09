package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.config.CoordinatorsContact;
import com.marcantoinemeunier.hiversolidaire.notification.CoordinatorAlerts;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Accueil du back-office : le tableau de bord (cahier des charges §6). Les journées à compléter des 14 prochains jours
 * en tête, puis les alertes enregistrées non traitées, les préparations chez soi sans récupération et les
 * récupérations sans repas (R4), et les nouveaux bénévoles à signaler (§2). En tête, un avertissement si la saison n'a pas de contact des coordinateurs
 * (R6). Réservé au rôle coordinateur ({@code SecurityConfig}).
 */
@Controller
class DashboardController {

    /** Fenêtre des journées à compléter, aujourd'hui compris. */
    static final int DAYS_AHEAD = 14;

    private final CoordinatorPlanning planning;
    private final CoordinatorAlerts alerts;
    private final VolunteerService volunteers;
    private final CoordinatorActions actions;
    private final SeasonSettingsService seasons;

    DashboardController(CoordinatorPlanning planning, CoordinatorAlerts alerts, VolunteerService volunteers,
            CoordinatorActions actions, SeasonSettingsService seasons) {
        this.planning = planning;
        this.alerts = alerts;
        this.volunteers = volunteers;
        this.actions = actions;
        this.seasons = seasons;
    }

    record Missing(String postKey, int count) {
    }

    record IncompleteDay(String date, String label, List<Missing> missing) {
    }

    /** @param evening « lun. 4 janv. » : le soir J de la soirée concernée */
    record AlertLine(String id, String kindKey, String evening, String eveningDate) {
    }

    /**
     * @param count repas préparés chez soi sans récupération
     * @param withoutMeal récupérations sans repas à récupérer
     */
    record PickupLine(String eveningDate, String label, int count, int withoutMeal) {
    }

    @GetMapping("/admin")
    String dashboard(Model model) {
        var today = planning.today();
        model.addAttribute("incompleteDays", planning.incompleteDays(today, today.plusDays(DAYS_AHEAD - 1)).stream()
                .map(day -> new IncompleteDay(day.date().toString(), DayLabelFormatter.format(day.date()),
                        day.posts().stream().filter(post -> day.missing(post) > 0)
                                .map(post -> new Missing("planning.post." + post.name(), day.missing(post)))
                                .toList()))
                .toList());
        model.addAttribute("alerts", alerts.unhandled().stream()
                .map(alert -> new AlertLine(alert.id().toString(), "admin.alert.kind." + alert.kind().name(),
                        DayLabelFormatter.inline(alert.eveningDate()), alert.eveningDate().toString()))
                .toList());
        model.addAttribute("pickups", planning.eveningsWithPickupToOrganize().stream()
                .map(evening -> new PickupLine(evening.date().toString(), DayLabelFormatter.format(evening.date()),
                        evening.unmatchedHomePreparations(), evening.pickupsWithoutMeal()))
                .toList());
        model.addAttribute("newVolunteers", volunteers.toReview());
        model.addAttribute("daysAhead", DAYS_AHEAD);
        model.addAttribute("contactMissing", seasons.current().map(SeasonSettings::coordinatorsContact)
                .flatMap(CoordinatorsContact::readable).isEmpty());
        return "admin/tableau-de-bord";
    }

    @PostMapping("/admin/alertes/traiter")
    String handleAlert(@RequestParam(name = "alerte") String alert, Principal principal, RedirectAttributes redirect) {
        actions.handleAlert(AdminSupport.coordinatorId(principal), AdminSupport.id(alert));
        redirect.addFlashAttribute("done", "admin.done.alertHandled");
        return "redirect:/admin";
    }

    @PostMapping("/admin/benevoles/vu")
    String markReviewed(@RequestParam(name = "benevole") String volunteer,
            @RequestParam(name = "retour", required = false) String back, Principal principal,
            RedirectAttributes redirect) {
        var id = AdminSupport.id(volunteer);
        actions.markReviewed(AdminSupport.coordinatorId(principal), id);
        redirect.addFlashAttribute("done", "admin.done.volunteerReviewed");
        // Retour vers la fiche ou vers le tableau de bord, jamais vers une adresse reçue telle quelle.
        return "fiche".equals(back) ? "redirect:/admin/benevoles/" + id : "redirect:/admin";
    }
}
