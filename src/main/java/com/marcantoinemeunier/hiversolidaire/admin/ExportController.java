package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationContact;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Exports (cahier des charges §6, §8 « Plan B ») : CSV des inscriptions d'une période, et page imprimable du planning
 * des 7 prochains jours, que le navigateur enregistre en PDF (aucune bibliothèque PDF). Les deux sont des POST
 * protégés par CSRF et inscrits au journal d'audit : ils extraient des coordonnées. Les réponses ne sont pas mises en
 * cache ({@code no-store}).
 */
@Controller
class ExportController {

    /** Période maximale d'un export CSV. */
    static final int MAX_DAYS = 200;
    /** Page imprimable : aujourd'hui et les 6 jours suivants. */
    static final int PRINT_DAYS = 7;

    private final CoordinatorPlanning planning;
    private final CoordinatorActions actions;
    private final MessageSource messages;

    ExportController(CoordinatorPlanning planning, CoordinatorActions actions, MessageSource messages) {
        this.planning = planning;
        this.actions = actions;
        this.messages = messages;
    }

    @GetMapping("/admin/exports")
    String exports(Model model) {
        var today = planning.today();
        model.addAttribute("from", today.toString());
        model.addAttribute("to", today.plusDays(PRINT_DAYS - 1).toString());
        model.addAttribute("maxDays", MAX_DAYS);
        return "admin/exports";
    }

    /** CSV des inscriptions actives dont le jour d'activité tombe dans la période (bornes comprises). */
    @PostMapping("/admin/exports/inscriptions.csv")
    ResponseEntity<byte[]> csv(@RequestParam(name = "du") String fromParam, @RequestParam(name = "au") String toParam,
            Principal principal) {
        var from = AdminSupport.date(fromParam);
        var to = AdminSupport.date(toParam);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            return ResponseEntity.badRequest().build();
        }
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

    /** Un inscrit sur la page imprimable. */
    record Person(String pseudo, String name, String phone) {
    }

    record PrintPost(String postKey, List<Person> people, int missing) {
    }

    record PrintDay(String label, List<PrintPost> posts) {
    }

    @PostMapping("/admin/exports/impression")
    String printable(Principal principal, Model model) {
        var today = planning.today();
        var to = today.plusDays(PRINT_DAYS - 1);
        var window = actions.printablePlanning(AdminSupport.coordinatorId(principal), today, to);
        var days = new ArrayList<PrintDay>();
        window.ifPresent(w -> w.days().forEach(day -> days.add(new PrintDay(DayLabelFormatter.format(day.date()),
                day.posts().stream().map(post -> new PrintPost("planning.post." + post.name(),
                        w.contactsOn(day.eveningOf(post).orElseThrow().date(), post).stream()
                                .map(contact -> new Person(contact.pseudo(),
                                        contact.lastName() + " " + contact.firstName(), contact.phone()))
                                .toList(),
                        day.missing(post))).toList()))));
        model.addAttribute("days", days);
        model.addAttribute("from", DayLabelFormatter.inline(today));
        model.addAttribute("to", DayLabelFormatter.inline(to));
        return "admin/impression";
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
