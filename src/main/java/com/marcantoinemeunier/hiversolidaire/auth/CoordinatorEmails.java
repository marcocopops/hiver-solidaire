package com.marcantoinemeunier.hiversolidaire.auth;

import com.marcantoinemeunier.hiversolidaire.benevole.EmailAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adresses des coordinateurs ({@code APP_COORDINATOR_EMAILS}, liste séparée par des virgules, ADR-0010). Une adresse
 * de la liste reçoit le rôle coordinateur à la connexion par lien magique, en plus du rôle bénévole. Aucune adresse
 * réelle dans le dépôt ; les adresses ne sont jamais journalisées.
 */
@Component
public class CoordinatorEmails {

    // Non final : les tests la remplacent par une liste modifiable.

    private final Set<String> emails;

    public CoordinatorEmails(@Value("${app.coordinators.emails:}") String configured) {
        this.emails = parse(configured);
    }

    /**
     * Adresses normalisées comme à la connexion ({@link EmailAddress#normalize(String)}).
     *
     * @throws IllegalStateException si une entrée n'est pas une adresse : l'appli ne démarre pas (sans répéter
     *     l'entrée, qui pourrait être une adresse mal saisie)
     */
    static Set<String> parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configured.split(","))
                .filter(entry -> !entry.isBlank())
                .map(entry -> EmailAddress.normalize(entry)
                        .orElseThrow(() -> new IllegalStateException("app.coordinators.emails contains an invalid address")))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Destinataires des e-mails aux coordinateurs (récap quotidien, alertes immédiates), dans l'ordre alphabétique.
     * Vide : aucun e-mail aux coordinateurs.
     */
    public List<String> all() {
        return emails.stream().sorted().toList();
    }

    /** @param normalizedEmail adresse du lien magique consommé, déjà normalisée */
    public boolean contains(String normalizedEmail) {
        return emails.contains(normalizedEmail);
    }
}
