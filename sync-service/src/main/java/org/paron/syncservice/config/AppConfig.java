package org.paron.syncservice.config;

/*defining rest tempalte to call out ledger and token service
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.restclient.RestTemplateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder){
        return builder
                .connectTimeout(Duration.ofSeconds(5))   // Removed "set" prefix
                .readTimeout(Duration.ofSeconds(10))    // Removed "set" prefix
                .build();

    }

    /*
     * Spring Boot 4 auto-configures Jackson 3 (tools.jackson.*), but
     * SignatureVerifier and Spring Kafka's Json(De)Serializer use Jackson 2
     * (com.fasterxml.jackson.databind), for which no ObjectMapper bean is
     * provided. Supply one so those components can be wired.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
