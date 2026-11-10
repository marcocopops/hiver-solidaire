package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Sans SMTP configuré, l'appli démarre et l'envoi se déclare indisponible : lien de connexion et e-mails automatiques.
 * La tâche planifiée des e-mails tourne par défaut ; le déclencheur de développement n'existe que si on le demande.
 */
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

    @Test
    void without_mail_host_automatic_mails_wait() {
        runner.withBean(JavaMailSenderImpl.class).run(context -> {
            var transport = context.getBean(MailTransport.class);

            assertThat(transport.available()).isFalse();
            assertThatIllegalStateException().isThrownBy(() -> transport.send(new OutgoingMail(
                    List.of("benevole@example.org"), null, "Sujet", "Texte", "<p>Texte</p>")));
        });
    }

    @Test
    void with_a_mail_host_automatic_mails_use_smtp() {
        runner.withBean(JavaMailSenderImpl.class).withPropertyValues("spring.mail.host=localhost")
                .run(context -> assertThat(context.getBean(MailTransport.class))
                        .isInstanceOf(SmtpMailTransport.class));
    }

    @Test
    void an_outgoing_mail_never_prints_its_addresses() {
        var mail = new OutgoingMail(List.of("benevole@example.org"), "coordination@example.org", "Sujet", "Texte",
                "<p>Texte</p>");

        assertThat(mail.toString()).doesNotContain("@").doesNotContain("Texte");
    }

    private final ApplicationContextRunner scheduling = new ApplicationContextRunner()
            .withUserConfiguration(MailSchedulingConfig.class, MailDevelopmentRun.class)
            .withBean(MailJobs.class, () -> mock(MailJobs.class));

    @Test
    void the_mail_jobs_are_scheduled_by_default() {
        scheduling.run(context -> assertThat(context).hasSingleBean(MailSchedulingConfig.MailTicker.class));
    }

    @Test
    void the_mail_jobs_can_be_left_unscheduled() {
        scheduling.withPropertyValues("app.mail.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MailSchedulingConfig.MailTicker.class));
    }

    @Test
    void the_development_trigger_is_absent_by_default() {
        scheduling.run(context -> assertThat(context).doesNotHaveBean(MailDevelopmentRun.class));
        scheduling.withPropertyValues("app.mail.dev-run-now=false")
                .run(context -> assertThat(context).doesNotHaveBean(MailDevelopmentRun.class));
        scheduling.withPropertyValues("app.mail.dev-run-now=true")
                .run(context -> assertThat(context).hasSingleBean(MailDevelopmentRun.class));
    }
}
