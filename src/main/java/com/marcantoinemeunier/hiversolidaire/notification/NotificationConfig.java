package com.marcantoinemeunier.hiversolidaire.notification;

import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Configuration
class NotificationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationConfig.class);

    /** SMTP si {@code MAIL_HOST} est renseigné, sinon envoi indisponible (l'appli démarre quand même). */
    @Bean
    MagicLinkSender magicLinkSender(@Value("${spring.mail.host:}") String host,
            ObjectProvider<JavaMailSender> mailSender, MessageSource messages,
            @Value("${app.mail.from}") String from) {
        if (!smtpConfigured(host, mailSender)) {
            LOG.warn("No SMTP host configured: magic link login is unavailable");
            return new UnavailableMagicLinkSender();
        }
        return new SmtpMagicLinkSender(mailSender.getObject(), messages, sender(requiredFrom(from), messages));
    }

    /** Même canal pour les e-mails automatiques (ADR-0012) : sans SMTP, ils attendent puis expirent. */
    @Bean
    MailTransport mailTransport(@Value("${spring.mail.host:}") String host, ObjectProvider<JavaMailSender> mailSender,
            MessageSource messages, @Value("${app.mail.from}") String from) {
        if (!smtpConfigured(host, mailSender)) {
            LOG.warn("No SMTP host configured: automatic e-mails are not sent");
            return new UnavailableMailTransport();
        }
        return new SmtpMailTransport(mailSender.getObject(), requiredFrom(from), senderName(messages));
    }

    /**
     * Avec un serveur SMTP, l'expéditeur est obligatoire : l'appli ne démarre pas plutôt que d'envoyer depuis une
     * adresse vide.
     */
    private static String requiredFrom(String from) {
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("MAIL_FROM is required when MAIL_HOST is set");
        }
        return from.strip();
    }

    private static boolean smtpConfigured(String host, ObjectProvider<JavaMailSender> mailSender) {
        return StringUtils.hasText(host) && mailSender.getIfAvailable() != null;
    }

    static String senderName(MessageSource messages) {
        return messages.getMessage("mail.sender.name", null, "Hiver Solidaire", Locale.FRENCH);
    }

    /** « Hiver Solidaire &lt;adresse&gt; », encodé si besoin. */
    private static String sender(String from, MessageSource messages) {
        try {
            return new InternetAddress(from, senderName(messages), StandardCharsets.UTF_8.name()).toString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
