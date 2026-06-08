package com.vendex.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code auth.*} keys in application.yaml. Records are immutable
 * so the runtime configuration is fixed at startup — anything that changes
 * dynamically should be in the DB, not here.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        Bcrypt bcrypt,
        Crypto crypto
) {
    public record Jwt(
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {}

    public record Bcrypt(int cost) {}

    /**
     * AES-256 key used to encrypt RSA signing-key private halves at rest.
     * Base64-encoded 32 bytes. Empty string in non-prod is permitted but
     * SigningKeyService will refuse to generate new keys without it.
     */
    public record Crypto(String encryptionKey) {}
}
