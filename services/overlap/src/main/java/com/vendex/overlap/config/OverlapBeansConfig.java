package com.vendex.overlap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class OverlapBeansConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
