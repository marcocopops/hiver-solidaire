package com.marcantoinemeunier.hiversolidaire.config;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'; "
            + "form-action 'self'; base-uri 'self'; object-src 'none'";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), "
            + "usb=(), accelerometer=(), gyroscope=(), magnetometer=(), interest-cohort=()";

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
                        .anyRequest().denyAll());
        return baseline(http).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/error").permitAll()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .anyRequest().denyAll());
        return baseline(http).build();
    }

    private HttpSecurity baseline(HttpSecurity http) throws Exception {
        return http
                .csrf(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        // HSTS est posé par Cloudflare (cahier des charges, §8).
                        .httpStrictTransportSecurity(hsts -> hsts.disable()));
    }
}
