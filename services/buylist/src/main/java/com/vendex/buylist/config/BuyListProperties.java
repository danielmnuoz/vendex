package com.vendex.buylist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buylist")
public record BuyListProperties(String cardCatalogTarget) {}
