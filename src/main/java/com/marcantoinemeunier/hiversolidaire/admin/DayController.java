package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.benevole.Pseudo;
import com.marcantoinemeunier.hiversolidaire.benevole.ProfileForm;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.CoordinatorOutcome;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationContact;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Fiche journée du back-office (cahier des charges §6 « Fiche soirée », ADR-0009) : les deux soirées de la journée,
 * leurs inscrits avec leurs coordonnées, et les actions des coordinateurs. Chaque action a sa page de confirmation
 * (GET, sans effet), puis un POST protégé par CSRF, inscrit au journal d'audit.
 */
@Controller
class DayController {

    static final String DAY_PARAM = RegistrationChoice.DAY_PARAM;
    static final String REGISTRATION_PARAM = "inscription";
    static final String VOLUNTEER_PARAM = "benevole";

    private final CoordinatorPlanning planning;
    private final CoordinatorActions actions;
    private final RegistrationService registrations;
    private final VolunteerService volunteers;

    DayController(CoordinatorPlanning planning, CoordinatorActions actions, RegistrationService registrations,
            VolunteerService volunteers) {
        this.planning = planning;
        this.actions = actions;
        this.registrations = registrations;
        this.volunteers = volunteers;
    }

    /** Un poste de la soirée : inscrits qui le couvrent et places manquantes jusqu'au minimum (R5). */
    record PostStatus(String postKey, int occupied, int missing) {
    }

    /**
     * Une inscription et les coordonnées de son bénévole.
     *
     * @param afterDeadline la limite de R6 est passée : le bénévole ne peut plus se désinscrire seul
     */
    record Line(String registrationId, String optionKey, ActivityLabel activity, String volunteerId, String pseudo,
            String lastName, String firstName, String phone, String email, boolean changeable, boolean afterDeadline) {
    }

    /**
     * Une des deux soirées de la journée, limitée aux postes affichés ce jour-là.
     *
     * @param pickupAlert R4 : un repas préparé chez soi n'a pas de récupération
     * @param pickupWithoutMeal R4 : une récupération n'a plus de repas (préparation chez soi annulée ou déplacée)
     * @param doublePresences pseudos des bénévoles qui comptent pour deux présences au dîner (deux « préparer et
     *     partager ») : les coordinateurs vérifient qu'ils viennent à deux
     */
    record Section(String titleKey, String eveningLabel, List<PostStatus> posts, List<Line> lines, boolean pickupAlert,
            boolean pickupWithoutMeal, List<String> doublePresences) {
    }

    @GetMapping("/admin/journee")
    String day(@RequestParam(name = DAY_PARAM) String dayParam, Model model) {
        var date = AdminSupport.date(dayParam);
        var window = planning.load(date.minusDays(1), date.plusDays(1)).orElseThrow(AdminSupport.NotFoundException::new);
        var day = window.day(date).orElseThrow(AdminSupport.NotFoundException::new);
        var sections = new ArrayList<Section>();
        day.eveningOf(Post.BREAKFAST).ifPresent(evening -> sections.add(section("admin.day.breakfast", day, evening,
                List.of(Post.BREAKFAST), window.contacts().getOrDefault(evening.date(), List.of()),
                contact -> contact.type() == RegistrationType.BREAKFAST)));
        day.eveningOf(Post.NIGHT).ifPresent(evening -> sections.add(section("admin.day.evening", day, evening,
                List.of(Post.MAIN_DISH, Post.SIDES, Post.DINNER_PRESENCE, Post.NIGHT),
                window.contacts().getOrDefault(evening.date(), List.of()),
                contact -> contact.type() != RegistrationType.BREAKFAST)));
        model.addAttribute("date", date.toString());
        model.addAttribute("label", DayLabelFormatter.format(date));
        model.addAttribute("statusKey", day.status().name().toLowerCase(java.util.Locale.ROOT));
        model.addAttribute("sections", sections);
        model.addAttribute("previous", window.day(date.minusDays(1)).map(d -> d.date().toString()).orElse(null));
        model.addAttribute("next", window.day(date.plusDays(1)).map(d -> d.date().toString()).orElse(null));
        return "admin/journee";
    }

    private Section section(String titleKey, Day day, Evening evening, List<Post> posts,
            List<RegistrationContact> contacts, Predicate<RegistrationContact> shown) {
        var statuses = posts.stream()
                .map(post -> new PostStatus("planning.post." + post.name(), evening.occupied(post), day.missing(post)))
                .toList();
        var shownContacts = contacts.stream().filter(shown).toList();
        var lines = shownContacts.stream().map(this::line).toList();
        var dinner = posts.contains(Post.MAIN_DISH);
        return new Section(titleKey, DayLabelFormatter.inline(evening.date()), statuses, lines,
                dinner && evening.needsPickupAlert(), dinner && evening.pickupsWithoutMeal() > 0,
                dinner ? doublePresences(shownContacts) : List.of());
    }

    /** Bénévoles qui tiennent plusieurs présences au dîner de la soirée (décision 2 du lot 3), par pseudo. */
    private static List<String> doublePresences(List<RegistrationContact> contacts) {
        return contacts.stream()
                .filter(contact -> contact.type().coveredPosts(contact.dishChoice()).contains(Post.DINNER_PRESENCE))
                .collect(Collectors.groupingBy(RegistrationContact::volunteerId, LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .filter(same -> same.size() > 1 && same.getFirst().pseudo() != null)
                .map(same -> same.getFirst().pseudo())
                .toList();
    }

    private Line line(RegistrationContact contact) {
        var now = planning.today();
        return new Line(contact.registrationId().toString(),
                "registration.option." + RegistrationChoice.choiceParam(contact.type(), contact.dishChoice()),
                ActivityLabel.of(contact.type(), contact.eveningDate()), contact.volunteerId().toString(),
                contact.pseudo(), contact.lastName(), contact.firstName(), contact.phone(), contact.email(),
                !contact.eveningDate().isBefore(now), !registrations.cancellable(contact.eveningDate()));
    }

    // --- Annuler une inscription (même après la limite de R6) ---

    @GetMapping("/admin/inscriptions/annuler")
    String confirmCancel(@RequestParam(name = REGISTRATION_PARAM) String registrationParam, Model model) {
        var contact = activeRegistration(registrationParam);
        model.addAttribute("line", line(contact));
        model.addAttribute("day", contact.activityDate().toString());
        return "admin/inscription-annuler";
    }

    @PostMapping("/admin/inscriptions/annuler")
    String cancel(@RequestParam(name = REGISTRATION_PARAM) String registrationParam, Principal principal,
            RedirectAttributes redirect) {
        var contact = activeRegistration(registrationParam);
        var change = actions.cancel(AdminSupport.coordinatorId(principal), contact.registrationId());
        if (change.outcome() == CoordinatorOutcome.NOT_FOUND) {
            throw new AdminSupport.NotFoundException();
        }
        redirect.addFlashAttribute("done", change.outcome() == CoordinatorOutcome.DONE
                ? "admin.done.cancelled" : "admin.refused.past");
        return "redirect:/admin/journee?jour=" + contact.activityDate();
    }

    // --- Déplacer une inscription vers une autre soirée ---

    @GetMapping("/admin/inscriptions/deplacer")
    String move(@RequestParam(name = REGISTRATION_PARAM) String registrationParam,
            @RequestParam(name = DAY_PARAM, required = false) String target,
            @RequestParam(name = "refus", required = false) String refusal, Model model) {
        var contact = activeRegistration(registrationParam);
        model.addAttribute("line", line(contact));
        model.addAttribute("day", contact.activityDate().toString());
        model.addAttribute("refusal", knownRefusal(refusal));
        if (target == null || target.isBlank()) {
            return "admin/inscription-deplacer";
        }
        var targetDay = AdminSupport.date(target);
        model.addAttribute("target", targetDay.toString());
        model.addAttribute("targetActivity", ActivityLabel.of(contact.type(), contact.type().eveningDate(targetDay)));
        return "admin/inscription-deplacer-confirmer";
    }

    @PostMapping("/admin/inscriptions/deplacer")
    String doMove(@RequestParam(name = REGISTRATION_PARAM) String registrationParam,
            @RequestParam(name = DAY_PARAM) String target, Principal principal, RedirectAttributes redirect) {
        var contact = activeRegistration(registrationParam);
        var targetDay = AdminSupport.date(target);
        var change = actions.move(AdminSupport.coordinatorId(principal), contact.registrationId(),
                contact.type().eveningDate(targetDay));
        return switch (change.outcome()) {
            case DONE -> {
                redirect.addFlashAttribute("done", "admin.done.moved");
                yield "redirect:/admin/journee?jour=" + targetDay;
            }
            case NOT_FOUND -> throw new AdminSupport.NotFoundException();
            case REFUSED -> "redirect:/admin/inscriptions/deplacer?inscription=" + contact.registrationId() + "&jour="
                    + targetDay + "&refus=" + change.refusal().name();
            case SAME_EVENING, PAST -> "redirect:/admin/inscriptions/deplacer?inscription=" + contact.registrationId()
                    + "&refus=" + change.outcome().name();
        };
    }

    // --- Inscrire un bénévole existant ---

    /**
     * Trois étapes en GET, sans effet : choisir le bénévole, puis l'inscription parmi celles encore possibles pour
     * lui (R1 à R4, R7, R2), puis confirmer. Seul le POST inscrit.
     */
    @GetMapping("/admin/inscriptions/nouvelle")
    String newRegistration(@RequestParam(name = DAY_PARAM) String dayParam,
            @RequestParam(name = VOLUNTEER_PARAM, required = false) String volunteerParam,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            @RequestParam(name = "refus", required = false) String refusal, Model model) {
        var date = AdminSupport.date(dayParam);
        model.addAttribute("date", date.toString());
        model.addAttribute("label", DayLabelFormatter.format(date));
        model.addAttribute("refusal", knownRefusal(refusal));
        if (volunteerParam == null || volunteerParam.isBlank()) {
            model.addAttribute("volunteers", volunteers.activeWithProfile());
            return "admin/inscription-nouvelle";
        }
        var volunteer = volunteers.contact(AdminSupport.id(volunteerParam))
                .orElseThrow(AdminSupport.NotFoundException::new);
        model.addAttribute("volunteer", volunteer);
        var choice = choiceParam == null ? Optional.<RegistrationChoice>empty()
                : RegistrationChoice.parse(date.toString(), choiceParam);
        if (choice.isEmpty()) {
            var available = registrations.choice(date, Optional.of(volunteer.id()))
                    .orElseThrow(AdminSupport.NotFoundException::new);
            model.addAttribute("options", available.available());
            model.addAttribute("blocking", available.blocking() == null ? null : available.blocking().name());
            return "admin/inscription-nouvelle-choix";
        }
        model.addAttribute("choice", choice.get());
        model.addAttribute("activity", ActivityLabel.of(choice.get().type(), choice.get().eveningDate()));
        return "admin/inscription-nouvelle-confirmer";
    }

    @PostMapping("/admin/inscriptions/nouvelle")
    String register(@RequestParam(name = DAY_PARAM) String dayParam,
            @RequestParam(name = VOLUNTEER_PARAM) String volunteerParam,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM) String choiceParam, Principal principal,
            RedirectAttributes redirect) {
        var volunteerId = AdminSupport.id(volunteerParam);
        var choice = RegistrationChoice.parse(dayParam, choiceParam).orElseThrow(AdminSupport.NotFoundException::new);
        var registered = actions.register(AdminSupport.coordinatorId(principal), volunteerId, choice);
        if (!registered.decision().accepted()) {
            return "redirect:/admin/inscriptions/nouvelle?jour=" + choice.day() + "&benevole=" + volunteerId
                    + "&refus=" + registered.decision().name();
        }
        redirect.addFlashAttribute("done", "admin.done.registered");
        return "redirect:/admin/journee?jour=" + choice.day();
    }

    // --- Nouveau bénévole sans e-mail ---

    @GetMapping("/admin/benevoles/nouveau")
    String newVolunteer(@RequestParam(name = DAY_PARAM, required = false) String dayParam, Model model) {
        model.addAttribute("profileForm", new ProfileForm());
        model.addAttribute("date", dayParam == null ? null : AdminSupport.date(dayParam).toString());
        return "admin/benevole-nouveau";
    }

    @PostMapping("/admin/benevoles/nouveau")
    String createVolunteer(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult binding,
            @RequestParam(name = DAY_PARAM, required = false) String dayParam, Principal principal, Model model) {
        var date = dayParam == null || dayParam.isBlank() ? null : AdminSupport.date(dayParam);
        model.addAttribute("date", date == null ? null : date.toString());
        if (form.getPseudo() == null || form.getPseudo().isBlank()) {
            // Même règle qu'au premier profil : un pseudo public n'est jamais publié sans avoir été vu.
            if (!binding.hasFieldErrors("lastName") && !binding.hasFieldErrors("firstName")) {
                form.setPseudo(volunteers.freePseudo(Pseudo.suggest(form.getFirstName(), form.getLastName())));
                model.addAttribute("pseudoSuggested", true);
            }
            return "admin/benevole-nouveau";
        }
        var pseudo = Pseudo.normalize(form.getPseudo());
        if (pseudo.isEmpty()) {
            binding.rejectValue("pseudo", "profile.error.pseudo");
        }
        if (binding.hasErrors()) {
            return "admin/benevole-nouveau";
        }
        try {
            var id = actions.createVolunteer(AdminSupport.coordinatorId(principal), form, pseudo.get());
            return date == null ? "redirect:/admin/benevoles/" + id
                    : "redirect:/admin/inscriptions/nouvelle?jour=" + date + "&benevole=" + id;
        } catch (VolunteerService.PseudoTakenException e) {
            var variant = volunteers.freePseudo(pseudo.get());
            form.setPseudo(variant);
            model.addAttribute("pseudoTaken", variant);
            return "admin/benevole-nouveau";
        }
    }

    private RegistrationContact activeRegistration(String registrationParam) {
        return planning.registration(AdminSupport.id(registrationParam))
                .orElseThrow(AdminSupport.NotFoundException::new);
    }

    /** Motif affiché seulement s'il est connu : la page ne recopie jamais un paramètre libre. */
    private static String knownRefusal(String refusal) {
        if (refusal == null) {
            return null;
        }
        var known = new ArrayList<String>();
        Arrays.stream(RegistrationDecision.values()).filter(d -> !d.accepted()).map(Enum::name).forEach(known::add);
        known.add(CoordinatorOutcome.SAME_EVENING.name());
        known.add(CoordinatorOutcome.PAST.name());
        return known.contains(refusal) ? refusal : null;
    }

    /** Date limite de R6, pour la page de confirmation. */
    static String deadline(LocalDate eveningDate) {
        return DayLabelFormatter.deadline(Cancellation.deadline(eveningDate));
    }
}
