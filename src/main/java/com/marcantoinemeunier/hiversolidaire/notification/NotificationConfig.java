package com.marcantoinemeunier.hiversolidaire.notification;

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
        if (!StringUtils.hasText(host) || mailSender.getIfAvailable() == null) {
            LOG.warn("No SMTP host configured: magic link login is unavailable");
            return new UnavailableMagicLinkSender();
        }
        return new SmtpMagicLinkSender(mailSender.getObject(), messages, from);
    }
}
