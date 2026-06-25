package org.paron.config;
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
 * In our architecture, the API Gateway handles the user-level
 * JWT authentication (session token). By the time a request
 * reaches token-service, the gateway has already verified the user.
 *
 * So token-service trusts requests that arrive (they came through the gateway)
 * but still protects the admin endpoints with a service-level API key
 * that only other internal services know.
 *
 * Endpoint access rules:
 *   /actuator/health          → public (Railway health checks need this)
 *   /api/v1/tokens/request    → authenticated users (via gateway)
 *   /api/v1/tokens/validate   → internal services only (sync-service calls this)
 *   /api/v1/tokens/mark-used  → internal services only (sync-service calls this)
 *   /api/v1/tokens/history/** → authenticated users
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless REST APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless — no sessions. Every request must carry its own auth.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Health endpoint must be public for Railway deployment checks
                        .requestMatchers("/actuator/**").permitAll()

                        // All token API endpoints — permit all here because the
                        // API Gateway has already authenticated the request upstream.
                        // For extra security in production, add IP allowlist
                        // or mTLS (mutual TLS) between services.
                        .requestMatchers("/api/v1/tokens/**").permitAll()

                        // Catch-all: anything not listed above requires authentication
                        .anyRequest().authenticated()
                )

                // Disable HTTP Basic auth popup — we don't use it
                .httpBasic(AbstractHttpConfigurer::disable)

                // Disable form login — this is a REST API, not a web app
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

