package org.paron.paypalfunding.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/funding/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedOriginPatterns("https://paron-public*.vercel.app", "https://paron-public.onrender.com")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
