package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.notification.MagicLinkSender;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.util.UriComponentsBuilder;

/** Double de test de l'envoi : garde les liens au lieu de les envoyer (Mailpit ne sert qu'aux essais manuels). */
public final class RecordingMagicLinkSender implements MagicLinkSender {

    public record SentLink(String email, URI link) {

        public String token() {
            return UriComponentsBuilder.fromUri(link).build().getQueryParams().getFirst("token");
        }
    }

    private final List<SentLink> sent = new CopyOnWriteArrayList<>();
    private volatile boolean available = true;

    @Override
    public boolean available() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public void send(String email, URI link) {
        sent.add(new SentLink(email, link));
    }

    public List<SentLink> sentTo(String email) {
        return sent.stream().filter(link -> link.email().equals(email)).toList();
    }

    public Optional<SentLink> lastSentTo(String email) {
        var links = sentTo(email);
        return links.isEmpty() ? Optional.empty() : Optional.of(links.getLast());
    }
}
