CREATE TABLE signing_keys (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    public_key              TEXT         NOT NULL,    -- PEM-encoded RSA public key
    private_key_encrypted   BYTEA        NOT NULL,    -- AES-GCM ciphertext of PEM-encoded private key
    alg                     VARCHAR(16)  NOT NULL DEFAULT 'RS256',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    rotated_at              TIMESTAMPTZ,              -- Null = currently active signer
    revoked_at              TIMESTAMPTZ               -- Null = still published in JWKS
);

-- Enforces exactly one active signing key at any time. The partial unique
-- index on the constant TRUE collapses to a single row when both rotated_at
-- and revoked_at are NULL.
CREATE UNIQUE INDEX uniq_signing_keys_active
    ON signing_keys ((TRUE))
    WHERE rotated_at IS NULL AND revoked_at IS NULL;
