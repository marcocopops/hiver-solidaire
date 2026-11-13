package com.marcantoinemeunier.hiversolidaire.privacy;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Notice d'information (cahier des charges §7), publique. Aucune donnée réelle dans le dépôt : le responsable du
 * traitement est l'organisateur saisi pour la saison, et le contact celui des coordinateurs (exposé par
 * {@code CoordinatorsContactAdvice}). Vides, la notice le dit sobrement et le tableau de bord le signale. Les durées
 * sont celles que la purge applique ({@link RetentionPolicy}).
 */
@Controller
class PrivacyNoticeController {

    static final String PATH = "/donnees-personnelles";

    /** Durée de conservation des sauvegardes, à reprendre de S6 ; d'ici là, la borne annoncée. */
    static final int BACKUP_DAYS = 30;

    private final SeasonSettingsService seasons;

    PrivacyNoticeController(SeasonSettingsService seasons) {
        this.seasons = seasons;
    }

    @GetMapping(PATH)
    String notice(Model model) {
        model.addAttribute("organizer", seasons.current().map(SeasonSettings::organizer)
                .filter(organizer -> !organizer.isBlank()).orElse(null));
        model.addAttribute("monthsAfterSeason", RetentionPolicy.AFTER_SEASON_WITHOUT_CONSENT.getMonths());
        model.addAttribute("yearsWithoutParticipation", RetentionPolicy.WITHOUT_PARTICIPATION_WITH_CONSENT.getYears());
        model.addAttribute("incompleteAccountDays", RetentionPolicy.INCOMPLETE_ACCOUNT.toDays());
        model.addAttribute("technicalMonths", RetentionPolicy.TECHNICAL_DATA.getMonths());
        model.addAttribute("backupDays", BACKUP_DAYS);
        return "donnees-personnelles";
    }
}
