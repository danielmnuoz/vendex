package com.vendex.cardcatalog;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CardCatalogProperties.class)
public class CardCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardCatalogApplication.class, args);
    }
}
