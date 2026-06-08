package com.vendex.cardcatalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code card-catalog.*} keys in {@code application.yaml}.
 * Records are immutable so runtime configuration is fixed at startup.
 */
@ConfigurationProperties(prefix = "card-catalog")
public record CardCatalogProperties(
        Tcgdex tcgdex,
        Cache cache,
        Search search,
        Seed seed
) {
    public record Tcgdex(String baseUrl) {}

    public record Cache(boolean enabled, Duration ttl) {}

    public record Search(int defaultPageSize, int maxPageSize) {}

    public record Seed(int maxSets, boolean dryRun, boolean enrichSeries) {}
}
