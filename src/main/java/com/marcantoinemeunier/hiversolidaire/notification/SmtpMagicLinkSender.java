package com.marcantoinemeunier.hiversolidaire.notification;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Envoi du lien par SMTP (Mailpit en développement). L'envoi part sur deux fils dédiés avec une file bornée :
 * la requête n'attend jamais le serveur SMTP, et une rafale au-delà de la file est abandonnée (journalisée sans
 * adresse) plutôt que de bloquer l'appli. Les limites par IP et par adresse rendent ce cas exceptionnel.
 */
class SmtpMagicLinkSender implements MagicLinkSender, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpMagicLinkSender.class);

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final String from;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200), Thread.ofVirtual().name("magic-link-mail-", 0).factory(),
            (task, pool) -> LOG.warn("Magic link e-mail dropped: send queue is full"));

    SmtpMagicLinkSender(JavaMailSender mailSender, MessageSource messages, String from) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.from = from;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void send(String email, URI link) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(messages.getMessage("mail.magicLink.subject", null, Locale.FRENCH));
        message.setText(messages.getMessage("mail.magicLink.body", new Object[] {link.toString()}, Locale.FRENCH));
        executor.execute(() -> {
            try {
                mailSender.send(message);
            } catch (RuntimeException e) {
                // Seule la classe de l'exception : son message peut contenir l'adresse du destinataire. Rien ne
                // remonte au gestionnaire par défaut du fil, qui imprimerait ce message.
                LOG.warn("Magic link e-mail could not be sent ({})", e.getClass().getSimpleName());
            }
        });
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
