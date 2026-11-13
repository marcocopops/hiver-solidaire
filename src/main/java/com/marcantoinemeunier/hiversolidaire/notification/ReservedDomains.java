package com.marcantoinemeunier.hiversolidaire.notification;

import java.util.List;
import java.util.Locale;

/**
 * Domaines réservés aux tests et à la documentation : {@code example.org}, {@code example.com}, {@code example.net} et
 * leurs sous-domaines (RFC 2606), les domaines de premier niveau {@code .test}, {@code .example}, {@code .invalid},
 * {@code .localhost} et {@code localhost} lui-même (RFC 2606, RFC 6761). Les données factices du dépôt y sont toutes ;
 * l'appli n'y envoie jamais rien en production ({@link RecipientGuard}). Java pur, sans Spring.
 */
public final class ReservedDomains {

    private static final List<String> SECOND_LEVEL = List.of("example.org", "example.com", "example.net");
    private static final List<String> TOP_LEVEL = List.of("test", "example", "invalid", "localhost");

    private ReservedDomains() {
    }

    /**
     * Vrai si l'adresse est dans un domaine réservé, sans tenir compte de la casse ni d'un point final. Une adresse sans
     * domaine est traitée comme réservée : il n'y a rien à lui envoyer.
     */
    public static boolean isReserved(String email) {
        if (email == null) {
            return true;
        }
        var at = email.lastIndexOf('@');
        if (at < 0) {
            return true;
        }
        var domain = email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (domain.isEmpty()) {
            return true;
        }
        if (domain.startsWith("[")) {
            // Littéral IP : pas un nom de domaine.
            return false;
        }
        for (var reserved : SECOND_LEVEL) {
            if (domain.equals(reserved) || domain.endsWith("." + reserved)) {
                return true;
            }
        }
        var lastDot = domain.lastIndexOf('.');
        var topLevel = lastDot < 0 ? domain : domain.substring(lastDot + 1);
        return TOP_LEVEL.contains(topLevel);
    }
}
