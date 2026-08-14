package com.vendex.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        String cardCatalogTarget,
        Csv csv,
        Matching matching
) {
    public record Csv(int maxRows, int maxBytes) {}
    public record Matching(double autoMatchThreshold, double ambiguityGap) {}
}
