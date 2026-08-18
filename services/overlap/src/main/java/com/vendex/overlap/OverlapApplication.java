package com.vendex.overlap;

import com.vendex.overlap.config.OverlapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OverlapProperties.class)
public class OverlapApplication {
    public static void main(String[] args) {
        SpringApplication.run(OverlapApplication.class, args);
    }
}
