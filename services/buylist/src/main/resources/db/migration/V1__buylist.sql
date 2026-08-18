CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE wanted_cards (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id         UUID           NOT NULL,
    card_id           UUID           NOT NULL,
    minimum_condition VARCHAR(3)     NOT NULL
        CHECK (minimum_condition IN ('NM', 'LP', 'MP', 'HP', 'DMG')),
    max_buy_price     NUMERIC(12, 2) NOT NULL CHECK (max_buy_price >= 0),
    quantity_wanted   INTEGER        NOT NULL CHECK (quantity_wanted > 0),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wanted_card_vendor_card UNIQUE (vendor_id, card_id)
);

-- A local, event-driven projection. Tombstones remain as active=false so a
-- delayed registration event cannot resurrect a newer unregistration.
CREATE TABLE event_vendor_roster (
    event_id    UUID        NOT NULL,
    vendor_id   UUID        NOT NULL,
    active      BOOLEAN     NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, vendor_id)
);

CREATE INDEX idx_wanted_cards_vendor_updated
    ON wanted_cards (vendor_id, updated_at DESC, id);
CREATE INDEX idx_wanted_cards_card_price
    ON wanted_cards (card_id, max_buy_price DESC, id);
CREATE INDEX idx_event_vendor_roster_active
    ON event_vendor_roster (event_id, vendor_id) WHERE active;
