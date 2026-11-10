package com.marcantoinemeunier.hiversolidaire.notification;

import java.util.List;
import java.util.Objects;

/**
 * E-mail prêt à partir : texte brut et HTML simple. Contient des adresses et des données personnelles : jamais
 * journalisé ({@link #toString()} ne dit que le nombre de destinataires).
 *
 * @param to destinataires (un bénévole, ou les coordinateurs)
 * @param replyTo adresse « Répondre à » (contact des coordinateurs), ou {@code null}
 */
public record OutgoingMail(List<String> to, String replyTo, String subject, String text, String html) {

    public OutgoingMail {
        to = List.copyOf(to);
        if (to.isEmpty()) {
            throw new IllegalArgumentException("A mail needs a recipient");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(html, "html");
    }

    @Override
    public String toString() {
        return "OutgoingMail[" + to.size() + " recipient(s)]";
    }
}
