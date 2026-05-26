package com.vendex.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Signing keys live on the auth side of the trust boundary. {@code
 * privateKeyEncrypted} stores the PEM-encoded RSA private key after
 * AES-256-GCM encryption with AAD bound to the row's UUID — see
 * {@link com.vendex.auth.crypto.Aead}.
 */
@Table("signing_keys")
public record SigningKey(
        @Id UUID id,
        String publicKey,
        byte[] privateKeyEncrypted,
        String alg,
        Instant createdAt,
        Instant rotatedAt,
        Instant revokedAt
) {
    public boolean isActive() {
        return rotatedAt == null && revokedAt == null;
    }

    public boolean isPublished() {
        return revokedAt == null;
    }
}
