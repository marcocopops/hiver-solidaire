package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.auth.CoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import com.marcantoinemeunier.hiversolidaire.planning.CoordinatorPlanning;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationContact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Récap quotidien des coordinateurs, à 20 h (§6) : les journées des 7 jours suivants (ce qui manque par poste, les
 * inscrits avec leur nom, leur pseudo et leur téléphone), les alertes non traitées, les repas à organiser (R4) et les
 * nouveaux bénévoles. Il sert de plan B (§8) : le texte brut se lit sans le site. Il ne contient aucune adresse e-mail
 * de bénévole ; un bénévole sans adresse est signalé « à prévenir par téléphone », car il ne reçoit pas de rappel.
 */
@Component
class DailyRecapMail implements MailComposer {

    /** Journées couvertes, à partir du lendemain du récap. */
    static final int DAYS = 7;

    /**
     * @param option ce que la personne fait ce jour-là
     * @param person « Anne Martin (AnneM), +33600000001 »
     * @param noEmail « sans e-mail : à prévenir par téléphone », ou {@code null}
     */
    record PersonLine(String option, String person, String noEmail) {
    }

    /**
     * @param missing « Nuit : 1 à pourvoir », par poste
     * @param doubles bénévoles qui comptent pour deux présences au dîner
     */
    record DayBlock(String label, String status, List<String> missing, List<PersonLine> people, List<String> doubles) {
    }

    private final CoordinatorEmails coordinators;
    private final CoordinatorPlanning planning;
    private final CoordinatorAlerts alerts;
    private final VolunteerService volunteers;
    private final MailCommons commons;
    private final MailRenderer renderer;

    DailyRecapMail(CoordinatorEmails coordinators, CoordinatorPlanning planning, CoordinatorAlerts alerts,
            VolunteerService volunteers, MailCommons commons, MailRenderer renderer) {
        this.coordinators = coordinators;
        this.planning = planning;
        this.alerts = alerts;
        this.volunteers = volunteers;
        this.commons = commons;
        this.renderer = renderer;
    }

    @Override
    public Kind kind() {
        return Kind.DAILY_RECAP;
    }

    @Override
    public Composed compose(MailDelivery delivery, Instant now) {
        var recipients = coordinators.all();
        if (recipients.isEmpty()) {
            return new Skipped(AbandonReason.NO_RECIPIENT);
        }
        var from = delivery.referenceDate().plusDays(1);
        var to = delivery.referenceDate().plusDays(DAYS);
        var days = planning.load(from, to).map(window -> window.days().stream()
                .map(day -> block(day, window.contacts())).toList()).orElse(List.of());
        var model = new LinkedHashMap<String, Object>();
        model.put("from", DayLabelFormatter.inline(from));
        model.put("to", DayLabelFormatter.inline(to));
        model.put("days", days);
        model.put("alerts", alerts.unhandled().stream()
                .map(alert -> renderer.message("admin.alert.kind." + alert.kind().name(),
                        DayLabelFormatter.inline(alert.eveningDate())))
                .toList());
        model.put("pickups", planning.eveningsWithPickupToOrganize().stream()
                .flatMap(evening -> Stream.of(
                        evening.unmatchedHomePreparations() > 0 ? renderer.message("mail.recap.pickup",
                                DayLabelFormatter.format(evening.date()), renderer.message(
                                        "admin.dashboard.pickups.count", evening.unmatchedHomePreparations())) : null,
                        evening.pickupsWithoutMeal() > 0 ? renderer.message("mail.recap.pickup",
                                DayLabelFormatter.format(evening.date()), renderer.message(
                                        "admin.dashboard.pickups.withoutMeal", evening.pickupsWithoutMeal())) : null))
                .filter(line -> line != null)
                .toList());
        model.put("newVolunteers", volunteers.toReview().stream()
                .map(volunteer -> person(volunteer.firstName(), volunteer.lastName(), volunteer.pseudo(),
                        volunteer.phone()))
                .toList());
        model.put("admin", commons.link("/admin"));
        var rendered = renderer.render("recap", "mail.recap.subject",
                new Object[] {DayLabelFormatter.inline(from), DayLabelFormatter.inline(to)}, model);
        return new Ready(new OutgoingMail(recipients, null, rendered.subject(), rendered.text(), rendered.html()));
    }

    /** Journée D : le petit-déjeuner de la soirée D-1, puis le dîner et la nuit de la soirée D (ADR-0009). */
    private DayBlock block(Day day, Map<LocalDate, List<RegistrationContact>> contacts) {
        var breakfast = contacts.getOrDefault(day.date().minusDays(1), List.of()).stream()
                .filter(contact -> contact.type() == RegistrationType.BREAKFAST);
        var evening = contacts.getOrDefault(day.date(), List.of()).stream()
                .filter(contact -> contact.type() != RegistrationType.BREAKFAST)
                .toList();
        var people = Stream.concat(breakfast, evening.stream())
                .sorted(Comparator.comparingInt((RegistrationContact contact) -> Day.POSTS.indexOf(
                        Registration.of(contact.type(), contact.dishChoice()).ownPost())))
                .map(this::line)
                .toList();
        var missing = day.posts().stream().filter(post -> day.missing(post) > 0)
                .map(post -> renderer.message("mail.recap.missing", renderer.message("planning.post." + post.name()),
                        day.missing(post)))
                .toList();
        return new DayBlock(DayLabelFormatter.format(day.date()),
                renderer.message("mail.recap.status." + day.status().name()), missing, people, doubles(evening));
    }

    private PersonLine line(RegistrationContact contact) {
        return new PersonLine(
                renderer.message("registration.option." + RegistrationChoice.choiceParam(contact.type(),
                        contact.dishChoice())),
                person(contact.firstName(), contact.lastName(), contact.pseudo(), contact.phone()),
                contact.email() == null ? renderer.message("mail.recap.noEmail") : null);
    }

    private static String person(String firstName, String lastName, String pseudo, String phone) {
        return firstName + " " + lastName + " (" + pseudo + "), " + phone;
    }

    /** Bénévoles qui tiennent plusieurs présences au dîner (lot 3) : à vérifier qu'ils viennent à deux. */
    private List<String> doubles(List<RegistrationContact> evening) {
        return evening.stream()
                .filter(contact -> contact.type().coveredPosts(contact.dishChoice()).contains(Post.DINNER_PRESENCE))
                .collect(Collectors.groupingBy(RegistrationContact::volunteerId, LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .filter(same -> same.size() > 1)
                .map(same -> renderer.message("admin.day.doublePresence", same.getFirst().pseudo()))
                .toList();
    }
}
