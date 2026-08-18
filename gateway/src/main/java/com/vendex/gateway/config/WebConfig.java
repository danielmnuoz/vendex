package com.vendex.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class WebConfig {

    @Bean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    WebMvcConfigurer corsConfigurer(GatewayProperties properties) {
        String[] origins = properties.cors().allowedOrigins().toArray(String[]::new);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type", "X-Request-ID")
                        .exposedHeaders("X-Request-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After")
                        .maxAge(3600);
            }
        };
    }
}
