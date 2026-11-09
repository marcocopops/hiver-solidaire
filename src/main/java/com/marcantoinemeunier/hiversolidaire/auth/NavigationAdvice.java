package com.marcantoinemeunier.hiversolidaire.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose aux pages ce qu'il faut pour l'en-tête et son menu : bénévole connecté, coordinateur, page du back-office et
 * entrée du menu de la page en cours. Affichage seulement : l'accès reste vérifié par {@code SecurityConfig}.
 */
@ControllerAdvice
class NavigationAdvice {

    /** Entrées du menu qu'une page peut marquer comme en cours ({@code aria-current="page"}). */
    enum MenuEntry {
        PLANNING, SIGN_IN, MY_REGISTRATIONS, MY_ACCOUNT, DASHBOARD, VOLUNTEERS, SEASON, EXPORTS
    }

    @ModelAttribute("signedIn")
    boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal;
    }

    /** Pour afficher les entrées du back-office. */
    @ModelAttribute("signedInCoordinator")
    boolean coordinator(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof VolunteerPrincipal principal
                && principal.coordinator();
    }

    /**
     * Page du back-office : en-tête prune et mention « Accès coordinateur ». Une page d'erreur (refus d'accès compris)
     * est servie sous {@code /error} et garde l'en-tête ordinaire.
     */
    @ModelAttribute("adminPage")
    boolean adminPage(HttpServletRequest request) {
        return isAdmin(path(request));
    }

    /** Nom de l'entrée en cours ({@link MenuEntry}), ou {@code null} si la page n'en a pas. */
    @ModelAttribute("menuCurrent")
    String menuCurrent(HttpServletRequest request) {
        var entry = current(path(request));
        return entry == null ? null : entry.name();
    }

    static MenuEntry current(String path) {
        if (path.equals("/planning")) {
            return MenuEntry.PLANNING;
        }
        if (path.equals("/connexion") || path.startsWith("/connexion/")) {
            return MenuEntry.SIGN_IN;
        }
        if (under(path, "/mes-inscriptions")) {
            return MenuEntry.MY_REGISTRATIONS;
        }
        if (under(path, "/mon-compte")) {
            return MenuEntry.MY_ACCOUNT;
        }
        if (under(path, "/admin/benevoles")) {
            return MenuEntry.VOLUNTEERS;
        }
        if (under(path, "/admin/saison")) {
            return MenuEntry.SEASON;
        }
        if (under(path, "/admin/exports")) {
            return MenuEntry.EXPORTS;
        }
        // Autres pages du back-office (journée, inscriptions, alertes) : le tableau de bord.
        return isAdmin(path) ? MenuEntry.DASHBOARD : null;
    }

    private static boolean isAdmin(String path) {
        return under(path, "/admin");
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
