package com.vendex.cardcatalog;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CardCatalogProperties.class)
public class CardCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardCatalogApplication.class, args);
    }
}
