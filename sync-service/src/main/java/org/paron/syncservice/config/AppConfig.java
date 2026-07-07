package org.paron.syncservice.config;

/*defining rest tempalte to call out ledger and token service
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

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
}
