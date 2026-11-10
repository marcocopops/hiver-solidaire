package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.auth.CoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import com.marcantoinemeunier.hiversolidaire.planning.CoordinatorPlanning;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Alerte immédiate aux coordinateurs (§6) : une soirée dont le dîner commence dans moins de 48 h a perdu un minimum.
 * L'e-mail donne ce qui manque au moment de l'envoi et le lien vers la fiche journée ; aucun nom de bénévole. Une
 * alerte déjà marquée traitée ne part plus.
 */
@Component
class AlertMail implements MailComposer {

    /** Postes de la soirée J : le dîner et la nuit sur la carte J, le petit-déjeuner sur la carte J+1 (ADR-0009). */
    private static final List<Post> EVENING_POSTS = List.of(Post.MAIN_DISH, Post.SIDES, Post.DINNER_PRESENCE,
            Post.NIGHT);

    private final MailDeliveries deliveries;
    private final CoordinatorEmails coordinators;
    private final CoordinatorPlanning planning;
    private final MailCommons commons;
    private final MailRenderer renderer;

    AlertMail(MailDeliveries deliveries, CoordinatorEmails coordinators, CoordinatorPlanning planning,
            MailCommons commons, MailRenderer renderer) {
        this.deliveries = deliveries;
        this.coordinators = coordinators;
        this.planning = planning;
        this.commons = commons;
        this.renderer = renderer;
    }

    @Override
    public Kind kind() {
        return Kind.ALERT;
    }

    @Override
    public Composed compose(MailDelivery delivery, Instant now) {
        var recipients = coordinators.all();
        if (recipients.isEmpty()) {
            return new Skipped(AbandonReason.NO_RECIPIENT);
        }
        var alert = deliveries.alert(delivery.alertId()).filter(row -> !row.handled());
        if (alert.isEmpty()) {
            return new Skipped(AbandonReason.NOTHING_TO_SEND);
        }
        var date = alert.get().eveningDate();
        var missing = new ArrayList<String>();
        planning.load(date, date.plusDays(1)).ifPresent(window -> {
            window.day(date).ifPresent(day -> EVENING_POSTS.forEach(post -> addMissing(missing, post, day.missing(post))));
            window.day(date.plusDays(1))
                    .ifPresent(day -> addMissing(missing, Post.BREAKFAST, day.missing(Post.BREAKFAST)));
        });
        var label = DayLabelFormatter.inline(date);
        var rendered = renderer.render("alerte", "mail.alert.subject", new Object[] {label}, Map.of(
                "evening", label,
                "missing", missing,
                "link", commons.link("/admin/journee?jour=" + date)));
        return new Ready(new OutgoingMail(recipients, null, rendered.subject(), rendered.text(), rendered.html()));
    }

    private void addMissing(List<String> missing, Post post, int count) {
        if (count > 0) {
            missing.add(renderer.message("mail.recap.missing", renderer.message("planning.post." + post.name()), count));
        }
    }
}
