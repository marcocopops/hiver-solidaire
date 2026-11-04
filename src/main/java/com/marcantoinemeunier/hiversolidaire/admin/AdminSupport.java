package com.marcantoinemeunier.hiversolidaire.admin;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Outils communs aux contrôleurs du back-office. */
final class AdminSupport {

    private AdminSupport() {
    }

    /** Identifiant technique du coordinateur connecté (nom du principal, jamais son e-mail). */
    static UUID coordinatorId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new NotFoundException();
        }
    }

    static UUID id(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NotFoundException();
        }
    }

    /** Objet inconnu ou paramètre mal formé : 404, sans détail. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class NotFoundException extends RuntimeException {
    }
}
