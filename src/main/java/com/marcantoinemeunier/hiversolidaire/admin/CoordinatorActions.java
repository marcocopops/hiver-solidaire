package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.admin.AuditLog.Action;
import com.marcantoinemeunier.hiversolidaire.admin.AuditLog.Target;
import com.marcantoinemeunier.hiversolidaire.benevole.ProfileForm;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.CoordinatorChange;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.CoordinatorOutcome;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService.Registered;
import com.marcantoinemeunier.hiversolidaire.notification.CoordinatorAlerts;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actions des coordinateurs : chacune dans une transaction qui écrit aussi sa ligne du journal d'audit
 * ({@link AuditLog}). Une action refusée ou sans effet n'écrit rien.
 */
@Service
class CoordinatorActions {

    private final AuditLog audit;
    private final CoordinatorAlerts alerts;
    private final VolunteerService volunteers;
    private final RegistrationService registrations;

    CoordinatorActions(AuditLog audit, CoordinatorAlerts alerts, VolunteerService volunteers,
            RegistrationService registrations) {
        this.audit = audit;
        this.alerts = alerts;
        this.volunteers = volunteers;
        this.registrations = registrations;
    }

    /** Annulation d'une inscription, même après la limite de R6. */
    @Transactional
    CoordinatorChange cancel(UUID coordinatorId, UUID registrationId) {
        var change = registrations.cancelByCoordinator(registrationId);
        if (change.outcome() == CoordinatorOutcome.DONE) {
            audit.record(coordinatorId, Action.REGISTRATION_CANCELLED,
                    Target.registration(registrationId, change.volunteerId(), change.eveningId()));
        }
        return change;
    }

    /** Déplacement vers une autre soirée (R1 à R4 et R7 sur la soirée cible). */
    @Transactional
    CoordinatorChange move(UUID coordinatorId, UUID registrationId, LocalDate targetEveningDate) {
        var change = registrations.moveByCoordinator(registrationId, targetEveningDate);
        if (change.outcome() == CoordinatorOutcome.DONE) {
            audit.record(coordinatorId, Action.REGISTRATION_MOVED, Target.move(registrationId, change.volunteerId(),
                    change.eveningId(), change.targetEveningId()));
        }
        return change;
    }

    /** Inscription d'un bénévole existant par un coordinateur (par téléphone), mêmes règles qu'en ligne. */
    @Transactional
    Registered register(UUID coordinatorId, UUID volunteerId, RegistrationChoice choice) {
        if (volunteers.contact(volunteerId).isEmpty() || !volunteers.profileCompleted(volunteerId)) {
            throw new AdminSupport.NotFoundException();
        }
        var registered = registrations.registerFor(volunteerId, choice);
        if (registered.decision().accepted()) {
            audit.record(coordinatorId, Action.REGISTRATION_CREATED,
                    Target.registration(registered.registrationId(), volunteerId, registered.eveningId()));
        }
        return registered;
    }

    /**
     * Nouveau bénévole sans e-mail, inscrit par téléphone.
     *
     * @throws VolunteerService.PseudoTakenException si le pseudo est déjà pris
     */
    @Transactional
    UUID createVolunteer(UUID coordinatorId, ProfileForm form, String pseudo) {
        var id = volunteers.createWithoutEmail(form, pseudo);
        audit.record(coordinatorId, Action.VOLUNTEER_CREATED, Target.volunteer(id));
        return id;
    }

    @Transactional
    void handleAlert(UUID coordinatorId, UUID alertId) {
        if (alerts.markHandled(alertId, coordinatorId)) {
            audit.record(coordinatorId, Action.ALERT_HANDLED, Target.alert(alertId));
        }
    }

    @Transactional
    void markReviewed(UUID coordinatorId, UUID volunteerId) {
        if (!volunteers.markReviewed(volunteerId)) {
            throw new AdminSupport.NotFoundException();
        }
        audit.record(coordinatorId, Action.VOLUNTEER_REVIEWED, Target.volunteer(volunteerId));
    }
}
