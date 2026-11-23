package com.marcantoinemeunier.hiversolidaire.monitoring;

import static org.springframework.web.servlet.function.RequestPredicates.method;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Page de santé publique ({@code /sante}) pour la sonde externe (UptimeRobot, cahier des charges §8). Le site est derrière
 * Cloudflare Access jusqu'à l'ouverture : une sonde sur {@code /} verrait la page d'Access et croirait le site en ligne.
 * Cette page seule a une règle « Bypass » chez Cloudflare (deploy/README.md).
 *
 * <p>Elle ne dit rien d'autre que son code : 200 si l'appli et la base répondent, 503 sinon, corps vide (ni version,
 * ni temps de réponse, ni erreur). L'Actuator reste sur le port de management, jamais routé.
 *
 * <p>Route fonctionnelle plutôt que {@code @Controller} : les {@code @ControllerAdvice} des pages (contact des
 * coordinateurs, lu en base) ne s'y appliquent pas, et la réponse ne dépend que de {@link PublicHealth}.
 */
@Configuration(proxyBeanMethods = false)
class PublicHealthRoute {

    static final String PATH = "/sante";

    @Bean
    RouterFunction<ServerResponse> publicHealthRouter(PublicHealth health) {
        return RouterFunctions.route()
                .route(path(PATH).and(method(HttpMethod.GET).or(method(HttpMethod.HEAD))), request -> ServerResponse
                        .status(health.up() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                        .cacheControl(CacheControl.noStore())
                        .build())
                .build();
    }
}
