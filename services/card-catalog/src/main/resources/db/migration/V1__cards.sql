-- pg_trgm powers the fuzzy SearchCards path (similarity + GIN index on name).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE cards (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id     VARCHAR(64)  NOT NULL UNIQUE,   -- TCGdex card ID (e.g. "sv03-001")
    name            VARCHAR(255) NOT NULL,
    set_id          VARCHAR(64)  NOT NULL,          -- TCGdex set ID (e.g. "sv03")
    set_name        VARCHAR(255) NOT NULL,
    set_series      VARCHAR(128),                   -- Nullable: TCGdex publishes inconsistently
    rarity          VARCHAR(64),                    -- Nullable
    image_url       TEXT,                           -- Small/low-res
    image_url_large TEXT,                           -- High-res
    release_date    DATE,                           -- Nullable
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- GIN trigram index on name powers SearchCards fuzzy matching.
CREATE INDEX idx_cards_name_trgm ON cards USING GIN (name gin_trgm_ops);

-- Fast lookups for set filters and ListSets aggregation.
CREATE INDEX idx_cards_set_id ON cards (set_id);
