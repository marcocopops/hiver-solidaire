package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationContact;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Exports (cahier des charges §6, §8 « Plan B ») sur une période choisie (7 prochains jours par défaut, 31 jours au
 * plus) : tableau à l'écran, mis à jour par htmx dès qu'une date change, page imprimable que le navigateur enregistre
 * en PDF (aucune bibliothèque PDF), et CSV des inscriptions. Le tableau à l'écran est une consultation, comme la fiche
 * d'un bénévole : il n'est pas audité. La page imprimable et le CSV extraient des coordonnées : ce sont des POST
 * protégés par CSRF et inscrits au journal d'audit, dont les réponses ne sont pas mises en cache ({@code no-store}).
 * Le tableau de l'écran et celui de la page imprimable viennent du même fragment ({@code admin/fragments :: contacts}).
 */
@Controller
class ExportController {

    private final CoordinatorPlanning planning;
    private final CoordinatorActions actions;
    private final MessageSource messages;

    ExportController(CoordinatorPlanning planning, CoordinatorActions actions, MessageSource messages) {
        this.planning = planning;
        this.actions = actions;
        this.messages = messages;
    }

    /** Page des exports ; sans JavaScript, le bouton « Afficher » y renvoie les dates en GET. */
    @GetMapping("/admin/exports")
    String exports(@RequestParam(name = "du", required = false) String fromParam,
            @RequestParam(name = "au", required = false) String toParam, Model model) {
        show(fromParam, toParam, model);
        return "admin/exports";
    }

    /** Fragment htmx : le tableau de la période et, hors de sa place ({@code hx-swap-oob}), les boutons d'export. */
    @GetMapping("/admin/exports/tableau")
    String table(@RequestParam(name = "du", required = false) String fromParam,
            @RequestParam(name = "au", required = false) String toParam, Model model) {
        show(fromParam, toParam, model);
        return "admin/fragments :: exportPartial";
    }

    /**
     * CSV des inscriptions actives dont le jour d'activité tombe dans la période (bornes comprises), ou, si la période
     * est refusée, la page des exports avec le message (400, sans audit) : d'où le type de retour {@code Object}, que
     * Spring MVC traite selon la valeur rendue ({@link ResponseEntity} ou {@link ModelAndView}).
     */
    @PostMapping("/admin/exports/inscriptions.csv")
    Object csv(@RequestParam(name = "du", required = false) String fromParam,
            @RequestParam(name = "au", required = false) String toParam, Principal principal, Model model) {
        ExportPeriod period;
        try {
            period = ExportPeriod.parse(fromParam, toParam);
        } catch (ExportPeriod.InvalidPeriodException e) {
            return refused(fromParam, toParam, e, model);
        }
        var from = period.from();
        var to = period.to();
        var contacts = actions.exportRegistrations(AdminSupport.coordinatorId(principal), from, to);
        var body = new StringBuilder(Csv.BOM);
        body.append(Csv.row(List.of(message("admin.csv.day"), message("admin.csv.activity"),
                message("admin.csv.registration"), message("profile.lastName"), message("profile.firstName"),
                message("profile.pseudo"), message("profile.phone"), message("account.email"))));
        contacts.stream()
                .filter(contact -> !contact.activityDate().isBefore(from) && !contact.activityDate().isAfter(to))
                .sorted(Comparator.comparing(RegistrationContact::activityDate)
                        .thenComparing(contact -> order(contact)))
                .forEach(contact -> body.append(Csv.row(List.of(contact.activityDate().toString(),
                        message(activityKey(contact)),
                        message("registration.option." + RegistrationChoice.choiceParam(contact.type(),
                                contact.dishChoice())),
                        nullToEmpty(contact.lastName()), nullToEmpty(contact.firstName()),
                        nullToEmpty(contact.pseudo()), nullToEmpty(contact.phone()), nullToEmpty(contact.email())))));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("inscriptions-" + from + "-" + to + ".csv").build().toString())
                .body(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Un inscrit dans le tableau ; {@code email} vaut {@code null} pour un bénévole inscrit par téléphone. */
    record Person(String pseudo, String name, String phone, String email) {
    }

    record PrintPost(String postKey, List<Person> people, int missing) {
    }

    record PrintDay(String label, List<PrintPost> posts) {
    }

    @PostMapping("/admin/exports/impression")
    ModelAndView printable(@RequestParam(name = "du", required = false) String fromParam,
            @RequestParam(name = "au", required = false) String toParam, Principal principal, Model model) {
        ExportPeriod period;
        try {
            period = ExportPeriod.parse(fromParam, toParam);
        } catch (ExportPeriod.InvalidPeriodException e) {
            return refused(fromParam, toParam, e, model);
        }
        var window = actions.printablePlanning(AdminSupport.coordinatorId(principal), period.from(), period.to());
        model.addAttribute("days", days(window));
        model.addAttribute("from", DayLabelFormatter.inline(period.from()));
        model.addAttribute("to", DayLabelFormatter.inline(period.to()));
        return new ModelAndView("admin/impression");
    }

    /** Tableau de la période saisie, ou message d'erreur et boutons d'export désactivés. */
    private void show(String fromParam, String toParam, Model model) {
        try {
            var period = ExportPeriod.orDefault(fromParam, toParam, planning.today());
            model.addAttribute("from", period.from().toString());
            model.addAttribute("to", period.to().toString());
            model.addAttribute("period", period);
            model.addAttribute("days", days(planning.load(period.from(), period.to())));
        } catch (ExportPeriod.InvalidPeriodException e) {
            show(fromParam, toParam, e, model);
        }
    }

    private static void show(String fromParam, String toParam, ExportPeriod.InvalidPeriodException e, Model model) {
        // Les champs gardent la saisie : th:value l'échappe, et un champ date ignore une valeur mal formée.
        model.addAttribute("from", fromParam);
        model.addAttribute("to", toParam);
        model.addAttribute("error", e.messageKey());
        model.addAttribute("maxDays", ExportPeriod.MAX_DAYS);
        model.addAttribute("days", List.of());
    }

    /** Impression ou CSV demandés pour une période refusée : la page des exports avec le message, sans audit. */
    private static ModelAndView refused(String fromParam, String toParam, ExportPeriod.InvalidPeriodException e,
            Model model) {
        show(fromParam, toParam, e, model);
        return new ModelAndView("admin/exports", HttpStatus.BAD_REQUEST);
    }

    private static List<PrintDay> days(Optional<CoordinatorPlanning.Window> window) {
        var days = new ArrayList<PrintDay>();
        window.ifPresent(w -> w.days().forEach(day -> days.add(new PrintDay(DayLabelFormatter.format(day.date()),
                day.posts().stream().map(post -> new PrintPost("planning.post." + post.name(),
                        w.contactsOn(day.eveningOf(post).orElseThrow().date(), post).stream()
                                .map(contact -> new Person(contact.pseudo(),
                                        contact.lastName() + " " + contact.firstName(), contact.phone(),
                                        contact.email()))
                                .toList(),
                        day.missing(post))).toList()))));
        return days;
    }

    private static int order(RegistrationContact contact) {
        return switch (contact.type()) {
            case BREAKFAST -> 0;
            case PREPARE_AND_SHARE, PREPARE_AT_HOME -> 1;
            case PICK_UP_AND_SHARE -> 2;
            case NIGHT_AND_DINNER -> 3;
        };
    }

    private static String activityKey(RegistrationContact contact) {
        return switch (contact.type()) {
            case BREAKFAST -> "admin.csv.activity.BREAKFAST";
            case NIGHT_AND_DINNER -> "admin.csv.activity.NIGHT";
            default -> contact.type().coveredPosts(contact.dishChoice()).contains(Post.DINNER_PRESENCE)
                    ? "admin.csv.activity.DINNER" : "admin.csv.activity.DINNER_AT_HOME";
        };
    }

    private String message(String key) {
        return messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
