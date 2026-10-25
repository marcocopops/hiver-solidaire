package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.auth.MagicLinkLogin;
import com.marcantoinemeunier.hiversolidaire.auth.VolunteerPrincipal;
import java.util.Arrays;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'; "
            + "form-action 'self'; base-uri 'self'; object-src 'none'";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), "
            + "usb=(), accelerometer=(), gyroscope=(), magnetometer=(), interest-cohort=()";

    /** Pages du bénévole connecté : sans connexion, on l'envoie vers {@code /connexion}. */
    private static final String[] VOLUNTEER_PAGES = {
        "/profil", "/inscription/confirmer", "/inscription/confirmee", "/mes-inscriptions"
    };

    /**
     * Port de management (8081) : seul {@code health} est servi, pour le healthcheck Docker.
     * Ce port n'est jamais routé par le tunnel.
     */
    @Bean
    @Order(1)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .anyRequest().denyAll())
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        return baseline(http).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, MagicLinkLogin magicLinkLogin) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/error").permitAll()
                // Planning public et choix d'une soirée : lecture seule, sans donnée personnelle.
                .requestMatchers(HttpMethod.GET, "/planning", "/inscription", "/notice").permitAll()
                // Demande du lien, page « Me connecter » et consommation du jeton (POST protégé par CSRF).
                .requestMatchers(HttpMethod.GET, "/connexion", "/connexion/envoye", "/login/ott").permitAll()
                .requestMatchers(HttpMethod.POST, "/connexion", "/login/ott").permitAll()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers(HttpMethod.GET, VOLUNTEER_PAGES).hasRole(VolunteerPrincipal.ROLE)
                .requestMatchers(HttpMethod.POST, "/profil", "/inscription", "/deconnexion")
                        .hasRole(VolunteerPrincipal.ROLE)
                .anyRequest().denyAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint()));
        magicLinkLogin.apply(http);
        return baseline(http).build();
    }

    /** Pages du bénévole : redirection vers la connexion. Tout le reste : 403, sans rien révéler. */
    private static AuthenticationEntryPoint entryPoint() {
        var volunteerPages = new OrRequestMatcher(Arrays.stream(VOLUNTEER_PAGES)
                .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path))
                .toList());
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/connexion"), volunteerPages)
                .defaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN))
                .build();
    }

    private HttpSecurity baseline(HttpSecurity http) throws Exception {
        return http
                .csrf(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        // HSTS est posé par Cloudflare (cahier des charges, §8).
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                        // Le planning affiche les pseudos des bénévoles : aucune page n'a à figurer dans les moteurs
                        // de recherche (cahier des charges §4, modèle de menaces).
                        .addHeaderWriter(new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow")));
    }
}
