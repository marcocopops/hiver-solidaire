package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import java.time.Instant;

/**
 * Contenu d'un type d'e-mail, calculé à l'envoi à partir de l'état courant (ADR-0012) : une inscription annulée
 * entre-temps n'apparaît pas, un compte supprimé ne reçoit rien. Appelé dans une transaction en lecture seule.
 */
interface MailComposer {

    Kind kind();

    Composed compose(MailDelivery delivery, Instant now);

    /** E-mail prêt, ou motif de ne pas l'envoyer. */
    sealed interface Composed permits Ready, Skipped {
    }

    record Ready(OutgoingMail mail) implements Composed {
    }

    record Skipped(AbandonReason reason) implements Composed {
    }
}
