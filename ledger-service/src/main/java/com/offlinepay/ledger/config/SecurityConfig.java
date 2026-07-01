package com.offlinepay.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Configures which API endpoints require authentication.
 *
 * Same reasoning as token-service's SecurityConfig: the API Gateway
 * (not yet built) will eventually handle user-level authentication
 * before any request reaches this service. Internal services
 * (token-service, sync-service) call ledger-service directly over
 * HTTP, so for now we keep endpoints open but stateless.
 *
 * IMPORTANT — this is the weakest point in the system right now:
 * /reserve, /release, and /settle move real money and have ZERO
 * caller verification. Once the gateway exists, this should be
 * tightened to only accept requests carrying a valid internal
 * service token (or be placed behind a private network / VPC
 * so it isn't reachable from the public internet at all).
 *
 * Endpoint access rules:
 *   /actuator/health           → public (Railway health checks need this)
 *   /api/v1/ledger/**          → open for now (internal services only, by convention)
 *   /api/v1/ledger/accounts/** → DEV/TEST ONLY — must be removed or locked
 *                                 down before any real deployment, since it
 *                                 lets anyone create an account with any balance
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless — no sessions, no cookies. Every request stands alone.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Health endpoint must be public for Railway deployment checks
                .requestMatchers("/actuator/**").permitAll()

                // Ledger API — open for now, trusted via internal network
                // convention rather than a real auth check. Tighten this
                // once the API Gateway and service-to-service auth exist.
                .requestMatchers("/api/v1/ledger/**").permitAll()

                // Catch-all: anything not listed above requires authentication
                .anyRequest().authenticated()
            )

            // No HTTP Basic popup — this is a REST API, not a browser app
            .httpBasic(AbstractHttpConfigurer::disable)

            // No form login — not relevant for a backend service
            .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
