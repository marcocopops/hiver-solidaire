package com.marcantoinemeunier.hiversolidaire.auth;

import com.marcantoinemeunier.hiversolidaire.benevole.EmailAddress;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.notification.MagicLinkSender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Demande du lien magique et page « Me connecter » (cahier des charges §6 étapes 2-3, ADR-0003).
 *
 * <p>La consommation du jeton reste au filtre de Spring Security ({@code POST /login/ott}). La génération passe
 * par ce contrôleur plutôt que par {@code /ott/generate} : validation de l'adresse, limites de débit avant la
 * création du jeton, message d'indisponibilité sans SMTP.
 */
@Controller
class LoginController {

    /** Durée de validité du lien (§6, §8). */
    static final Duration LINK_VALIDITY = Duration.ofMinutes(15);

    private final OneTimeTokenService tokens;
    private final MagicLinkSender sender;
    private final MagicLinkRateLimits limits;
    private final String baseUrl;

    LoginController(OneTimeTokenService tokens, MagicLinkSender sender, MagicLinkRateLimits limits,
            @Value("${app.base-url}") String baseUrl) {
        this.tokens = tokens;
        this.sender = sender;
        this.limits = limits;
        this.baseUrl = baseUrl;
    }

    @GetMapping("/connexion")
    String loginForm(@RequestParam(name = RegistrationChoice.EVENING_PARAM, required = false) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choice,
            HttpServletRequest request, Model model) {
        model.addAttribute("invalidLink", request.getParameterMap().containsKey("lien-invalide"));
        return form(model, RegistrationChoice.parse(evening, choice).orElse(null), "");
    }

    /**
     * Même réponse, que l'adresse soit connue ou non, et que la limite par adresse soit atteinte ou non : aucune
     * recherche du bénévole ici, l'envoi part en arrière-plan (menace M3).
     */
    @PostMapping("/connexion")
    String requestLink(@RequestParam(name = "email", defaultValue = "") String email,
            @RequestParam(name = RegistrationChoice.EVENING_PARAM, required = false) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choiceParam,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        var choice = RegistrationChoice.parse(evening, choiceParam).orElse(null);
        if (!sender.available()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return form(model, choice, email);
        }
        var normalized = EmailAddress.normalize(email);
        if (normalized.isEmpty()) {
            model.addAttribute("invalidEmail", true);
            return form(model, choice, email);
        }
        if (!limits.allowIp(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return "connexion-trop-de-demandes";
        }
        if (limits.allowEmail(normalized.get())) {
            var token = tokens.generate(new GenerateOneTimeTokenRequest(normalized.get(), LINK_VALIDITY));
            sender.send(normalized.get(), link(token.getTokenValue(), choice));
        }
        return "redirect:/connexion/envoye";
    }

    @GetMapping("/connexion/envoye")
    String linkSent() {
        return "connexion-envoye";
    }

    /**
     * Page « Me connecter » ouverte par le lien. Elle ne consomme pas le jeton : les filtres des messageries qui
     * ouvrent les liens ne connectent personne (menace M2). Seul le POST de cette page, protégé par CSRF, connecte.
     */
    @GetMapping("/login/ott")
    String confirmLogin(@RequestParam(name = "token", required = false) String token,
            @RequestParam(name = RegistrationChoice.EVENING_PARAM, required = false) String evening,
            @RequestParam(name = RegistrationChoice.CHOICE_PARAM, required = false) String choice, Model model) {
        if (token == null || token.isBlank()) {
            return "redirect:/connexion";
        }
        model.addAttribute("token", token);
        model.addAttribute("choice", RegistrationChoice.parse(evening, choice).orElse(null));
        return "connexion-confirmer";
    }

    private String form(Model model, RegistrationChoice choice, String email) {
        model.addAttribute("choice", choice);
        model.addAttribute("email", email);
        model.addAttribute("unavailable", !sender.available());
        return "connexion";
    }

    /** Lien construit sur l'origine configurée, jamais sur l'en-tête Host de la requête. */
    private URI link(String token, RegistrationChoice choice) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl).path("/login/ott").queryParam("token", token);
        if (choice != null) {
            choice.appendTo(uri);
        }
        return uri.encode().build().toUri();
    }
}
