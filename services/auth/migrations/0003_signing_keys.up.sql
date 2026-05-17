-- One row per RSA keypair. At any time exactly one row has rotated_at IS NULL —
-- that's the active signing key. Rotated keys remain published in JWKS until
-- revoked_at is set (which only happens after every JWT they signed has expired,
-- i.e., after the full 7-day refresh-token window).
CREATE TABLE signing_keys (
    id                       UUID PRIMARY KEY,
    public_key               TEXT        NOT NULL,
    private_key_encrypted    TEXT        NOT NULL,
    alg                      VARCHAR(16) NOT NULL DEFAULT 'RS256',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at               TIMESTAMPTZ,
    revoked_at               TIMESTAMPTZ
);

-- Enforce: at most one active (non-rotated, non-revoked) key.
CREATE UNIQUE INDEX signing_keys_one_active_idx
    ON signing_keys ((TRUE))
    WHERE rotated_at IS NULL AND revoked_at IS NULL;
