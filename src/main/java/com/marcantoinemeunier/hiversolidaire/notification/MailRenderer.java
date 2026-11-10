package com.marcantoinemeunier.hiversolidaire.notification;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Rendu des e-mails en texte brut ({@code templates/mail/*.txt}) et en HTML simple ({@code templates/mail/*.html},
 * {@code th:text} seulement, sans style ni script). Les textes viennent de {@code messages.properties}.
 * <p>
 * Le moteur est propre aux e-mails et n'est pas déclaré comme bean : un second {@code ISpringTemplateEngine} remplacerait
 * celui que Spring Boot configure pour les pages.
 */
@Component
class MailRenderer {

    record Rendered(String subject, String text, String html) {
    }

    private final SpringTemplateEngine engine = new SpringTemplateEngine();
    private final MessageSource messages;

    MailRenderer(MessageSource messages) {
        this.messages = messages;
        engine.setTemplateEngineMessageSource(messages);
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setTemplateMode(mode);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        resolver.setOrder(order);
        return resolver;
    }

    /**
     * @param template nom commun des deux gabarits, sans extension
     * @param subjectKey clé du sujet dans {@code messages.properties}
     */
    Rendered render(String template, String subjectKey, Object[] subjectArgs, Map<String, Object> variables) {
        var context = new Context(Locale.FRENCH, variables);
        return new Rendered(message(subjectKey, subjectArgs), engine.process(template + ".txt", context),
                engine.process(template + ".html", context));
    }

    String message(String key, Object... args) {
        return messages.getMessage(key, args, Locale.FRENCH);
    }
}
