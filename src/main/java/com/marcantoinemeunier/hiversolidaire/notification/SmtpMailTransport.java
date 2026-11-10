package com.marcantoinemeunier.hiversolidaire.notification;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Envoi par SMTP : un message en deux versions, texte brut et HTML simple, de « Hiver Solidaire » ({@code MAIL_FROM}).
 * Aucun en-tête de suivi : les liens sont ceux du message, tels quels.
 */
class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;
    private final String senderName;

    SmtpMailTransport(JavaMailSender mailSender, String from, String senderName) {
        this.mailSender = mailSender;
        this.from = from;
        this.senderName = senderName;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void send(OutgoingMail mail) {
        var message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, senderName);
            helper.setTo(mail.to().toArray(String[]::new));
            if (mail.replyTo() != null) {
                helper.setReplyTo(mail.replyTo());
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Pas de cause : son message peut contenir une adresse.
            throw new MailPreparationException("Mail could not be prepared (" + e.getClass().getSimpleName() + ")");
        }
        mailSender.send(message);
    }
}
