CREATE TABLE catalog_sync_state (
    source              VARCHAR(64) PRIMARY KEY,
    lease_owner         UUID,
    lease_expires_at    TIMESTAMPTZ,
    last_started_at     TIMESTAMPTZ,
    last_completed_at   TIMESTAMPTZ,
    last_failed_at      TIMESTAMPTZ,
    last_card_count     INTEGER,
    last_error          TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT catalog_sync_lease_pair CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
    )
);

INSERT INTO catalog_sync_state (source) VALUES ('tcgdex');
