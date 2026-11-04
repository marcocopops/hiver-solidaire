package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.admin.AuditLog.Action;
import com.marcantoinemeunier.hiversolidaire.admin.AuditLog.Target;
import com.marcantoinemeunier.hiversolidaire.benevole.VolunteerService;
import com.marcantoinemeunier.hiversolidaire.notification.CoordinatorAlerts;
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

    CoordinatorActions(AuditLog audit, CoordinatorAlerts alerts, VolunteerService volunteers) {
        this.audit = audit;
        this.alerts = alerts;
        this.volunteers = volunteers;
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
