CREATE TABLE cards (
    id              UUID PRIMARY KEY,
    external_id     VARCHAR(64)  NOT NULL UNIQUE,  -- TCGdex card ID, e.g. "sv03-001"
    name            VARCHAR(255) NOT NULL,
    set_id          VARCHAR(64)  NOT NULL,         -- TCGdex set ID, e.g. "sv03"
    set_name        VARCHAR(255) NOT NULL,
    set_series      VARCHAR(255),                  -- Nullable until backfilled
    rarity          VARCHAR(64),                   -- Nullable until backfilled
    image_url       VARCHAR(512),
    image_url_large VARCHAR(512),
    release_date    DATE,                          -- Nullable; TCGdex publishes at series granularity
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX cards_set_id_idx ON cards (set_id);
CREATE INDEX cards_name_idx ON cards (name);

-- Trigram index supports fuzzy LIKE/ILIKE search and Phase 2 CSV fuzzy-matching.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX cards_name_trgm_idx ON cards USING gin (name gin_trgm_ops);
