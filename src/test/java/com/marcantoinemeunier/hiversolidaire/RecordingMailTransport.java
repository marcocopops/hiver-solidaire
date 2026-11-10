package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.notification.MailTransport;
import com.marcantoinemeunier.hiversolidaire.notification.OutgoingMail;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mail.MailSendException;

/**
 * Double de test des e-mails automatiques : garde les messages au lieu de les envoyer (Mailpit ne sert qu'aux essais
 * manuels). Peut simuler des échecs (Brevo indisponible, quota atteint), avec un message qui contient l'adresse, comme
 * un vrai serveur SMTP : il ne doit jamais arriver dans les logs.
 */
public final class RecordingMailTransport implements MailTransport {

    private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresToSimulate = new AtomicInteger();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void send(OutgoingMail mail) {
        if (failuresToSimulate.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new MailSendException("Daily quota exceeded for " + String.join(", ", mail.to()));
        }
        sent.add(mail);
    }

    /** Les {@code count} prochains envois échouent. */
    public void failNext(int count) {
        failuresToSimulate.set(count);
    }

    public List<OutgoingMail> sent() {
        return List.copyOf(sent);
    }

    public List<OutgoingMail> sentTo(String email) {
        return sent.stream().filter(mail -> mail.to().contains(email)).toList();
    }

    /** À appeler au début de chaque test : le contexte, et donc ce double, est partagé entre les classes de test. */
    public void clear() {
        sent.clear();
        failuresToSimulate.set(0);
    }
}
