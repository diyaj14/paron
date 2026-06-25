package org.paron.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/*
 * Application-wide bean definitions.
 *
 * A @Bean is an object that Spring manages for you.
 * Instead of calling "new RestTemplate()" yourself everywhere,
 * you define it once here and Spring injects it wherever it's needed.
 */

@Configuration
public class AppConfig{
    /*
     * RestTemplate for making HTTP calls to other services (ledger-service).
     *
     * Timeouts:
     *   connectTimeout — how long to wait when establishing a connection
     *   readTimeout    — how long to wait for a response after connecting
     *
     * Without timeouts, a slow ledger-service could make token-service
     * threads hang forever, eventually crashing the whole service.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))   // Removed "set" prefix
                .readTimeout(Duration.ofSeconds(10))    // Removed "set" prefix
                .build();
    }

}