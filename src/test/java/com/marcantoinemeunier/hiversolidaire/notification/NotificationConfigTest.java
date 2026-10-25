package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Sans SMTP configuré (production avant S5), l'appli démarre et l'envoi se déclare indisponible. */
class NotificationConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfig.class)
            .withBean("messageSource", StaticMessageSource.class)
            .withPropertyValues("app.mail.from=hiver-solidaire@example.org");

    @Test
    void without_mail_host_the_sender_is_unavailable() {
        runner.withBean(JavaMailSenderImpl.class).run(context -> {
            var sender = context.getBean(MagicLinkSender.class);

            assertThat(sender.available()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> sender.send("benevole@example.org", URI.create("http://localhost/")));
        });
    }

    @Test
    void with_a_blank_mail_host_the_sender_is_unavailable() {
        runner.withBean(JavaMailSenderImpl.class).withPropertyValues("spring.mail.host=")
                .run(context -> assertThat(context.getBean(MagicLinkSender.class).available()).isFalse());
    }

    @Test
    void with_a_mail_host_the_sender_uses_smtp() {
        runner.withBean(JavaMailSenderImpl.class).withPropertyValues("spring.mail.host=localhost").run(context -> {
            var sender = context.getBean(MagicLinkSender.class);

            assertThat(sender).isInstanceOf(SmtpMagicLinkSender.class);
            assertThat(sender.available()).isTrue();
        });
    }
}
