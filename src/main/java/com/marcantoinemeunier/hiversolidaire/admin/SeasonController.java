package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.config.CoordinatorsPhone;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonForm;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService.EveningsWithRegistrationsException;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService.SampleSeasonInUseException;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService.SeasonAlreadyActiveException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Saison (cahier des charges §6 « Saison », §11 question 2) : réglages de la saison courante, création d'une saison qui
 * remplace la saison d'exemple. Une seule saison active à la fois ; une soirée qui a des inscriptions actives n'est
 * jamais supprimée (ADR-0006).
 */
@Controller
class SeasonController {

    private final SeasonSettingsService seasons;
    private final CoordinatorActions actions;

    SeasonController(SeasonSettingsService seasons, CoordinatorActions actions) {
        this.seasons = seasons;
        this.actions = actions;
    }

    @GetMapping("/admin/saison")
    String season(Model model) {
        var current = seasons.current();
        if (current.isEmpty() || current.get().sample()) {
            return "redirect:/admin/saison/nouvelle";
        }
        if (!model.containsAttribute("seasonForm")) {
            model.addAttribute("seasonForm", SeasonForm.of(current.get()));
        }
        return page(model, "admin/saison");
    }

    @PostMapping("/admin/saison")
    String update(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult binding, Principal principal,
            Model model, RedirectAttributes redirect) {
        var current = seasons.current().filter(season -> !season.sample())
                .orElseThrow(AdminSupport.NotFoundException::new);
        var phone = phone(form, binding);
        if (binding.hasErrors()) {
            return page(model, "admin/saison");
        }
        try {
            actions.updateSeason(AdminSupport.coordinatorId(principal), current.id(), form, phone.orElse(null));
        } catch (EveningsWithRegistrationsException e) {
            model.addAttribute("blockingDates", e.dates().stream().map(DayLabelFormatter::inline)
                    .collect(Collectors.joining(", ")));
            return page(model, "admin/saison");
        }
        redirect.addFlashAttribute("done", "admin.done.seasonUpdated");
        return "redirect:/admin/saison";
    }

    @GetMapping("/admin/saison/nouvelle")
    String newSeason(Model model) {
        if (!model.containsAttribute("seasonForm")) {
            model.addAttribute("seasonForm", SeasonForm.blank());
        }
        return creationPage(model);
    }

    @PostMapping("/admin/saison/nouvelle")
    String create(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult binding, Principal principal,
            Model model, RedirectAttributes redirect) {
        var phone = phone(form, binding);
        if (binding.hasErrors()) {
            return creationPage(model);
        }
        try {
            actions.createSeason(AdminSupport.coordinatorId(principal), form, phone.orElse(null));
        } catch (SeasonAlreadyActiveException | SampleSeasonInUseException e) {
            return creationPage(model);
        }
        redirect.addFlashAttribute("done", "admin.done.seasonCreated");
        return "redirect:/admin/saison";
    }

    private String creationPage(Model model) {
        var current = seasons.current();
        model.addAttribute("creationAllowed", seasons.creationAllowed());
        model.addAttribute("sample", current.filter(season -> season.sample()).orElse(null));
        model.addAttribute("sampleRegistrations",
                current.filter(season -> season.sample()).map(season -> seasons.activeRegistrations(season.id()))
                        .orElse(0));
        return page(model, "admin/saison-nouvelle");
    }

    private String page(Model model, String view) {
        model.addAttribute("postKeys", Day.POSTS.stream().map(post -> "planning.post." + post.name()).toList());
        return view;
    }

    /** Téléphone saisi : vide, ou un numéro lisible ({@link CoordinatorsPhone}), sinon erreur de champ. */
    private static Optional<String> phone(SeasonForm form, BindingResult binding) {
        try {
            return CoordinatorsPhone.parse(form.getCoordinatorsPhone()).map(CoordinatorsPhone::display);
        } catch (IllegalStateException e) {
            binding.rejectValue("coordinatorsPhone", "admin.season.error.phone");
            return Optional.empty();
        }
    }
}
