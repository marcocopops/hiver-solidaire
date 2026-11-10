package com.marcantoinemeunier.hiversolidaire.planning;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page d'accueil (lot 4) : présentation des trois façons de participer. Les dates, l'organisateur et le contact des
 * coordinateurs (exposé par {@code CoordinatorsContactAdvice}) viennent des réglages de la saison, jamais du dépôt.
 */
@Controller
class HomeController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    private final SeasonSettingsService seasons;

    HomeController(SeasonSettingsService seasons) {
        this.seasons = seasons;
    }

    /**
     * Saison présentée sur l'accueil.
     *
     * @param firstEvening première soirée, « lundi 4 janvier 2027 »
     * @param lastMorning matin qui suit la dernière soirée
     * @param winter année ou années de l'hiver, « 2027 » ou « 2026-2027 »
     * @param organizer organisateur saisi pour la saison, ou {@code null}
     */
    record HomeSeason(boolean sample, String firstEvening, String lastMorning, String winter, String organizer) {

        static HomeSeason of(SeasonSettings settings) {
            var lastMorning = settings.lastEvening().plusDays(1);
            var firstYear = settings.firstEvening().getYear();
            var winter = firstYear == lastMorning.getYear()
                    ? String.valueOf(firstYear)
                    : firstYear + "-" + lastMorning.getYear();
            return new HomeSeason(settings.sample(), DATE.format(settings.firstEvening()), DATE.format(lastMorning),
                    winter, settings.organizer());
        }
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("season", seasons.current().map(HomeSeason::of).orElse(null));
        return "index";
    }
}
