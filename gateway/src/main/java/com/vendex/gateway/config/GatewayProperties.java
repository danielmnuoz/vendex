package com.vendex.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("gateway")
public record GatewayProperties(
        @NotNull Duration grpcDeadline,
        @Valid @NotNull Services services,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cors cors) {

    public record Services(
            boolean plaintext,
            @NotBlank String authTarget,
            @NotBlank String cardCatalogTarget) {}

    public record Jwt(
            @NotBlank String issuer,
            @NotNull Duration cacheTtl,
            @NotNull Duration staleTtl,
            @NotNull Duration clockSkew) {}

    public record RateLimit(
            @Positive int edgeRequests,
            @Positive int publicRequests,
            @Positive int authenticatedRequests,
            @NotNull Duration window) {}

    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {}
}
