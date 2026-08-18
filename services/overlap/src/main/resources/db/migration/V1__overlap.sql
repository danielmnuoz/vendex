CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE overlap_opportunities (
    id                    UUID           PRIMARY KEY,
    event_id              UUID           NOT NULL,
    buyer_vendor_id       UUID           NOT NULL,
    seller_vendor_id      UUID           NOT NULL,
    card_id               UUID           NOT NULL,
    inventory_item_id     UUID           NOT NULL,
    wanted_card_id        UUID           NOT NULL,
    seller_condition      VARCHAR(3)     NOT NULL
        CHECK (seller_condition IN ('NM', 'LP', 'MP', 'HP', 'DMG')),
    minimum_condition     VARCHAR(3)     NOT NULL
        CHECK (minimum_condition IN ('NM', 'LP', 'MP', 'HP', 'DMG')),
    available_quantity    INTEGER        NOT NULL CHECK (available_quantity > 0),
    quantity_wanted       INTEGER        NOT NULL CHECK (quantity_wanted > 0),
    asking_price          NUMERIC(12, 2) NOT NULL CHECK (asking_price >= 0),
    max_buy_price         NUMERIC(12, 2) NOT NULL CHECK (max_buy_price >= 0),
    inventory_priority    VARCHAR(16)    NOT NULL
        CHECK (inventory_priority IN ('normal', 'liquidate')),
    score                 NUMERIC(5, 2)  NOT NULL CHECK (score >= 0 AND score <= 100),
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_overlap_pair_card UNIQUE
        (event_id, buyer_vendor_id, seller_vendor_id, card_id),
    CONSTRAINT chk_distinct_overlap_vendors CHECK (buyer_vendor_id <> seller_vendor_id)
);

CREATE INDEX idx_overlaps_vendor_event_active
    ON overlap_opportunities (event_id, buyer_vendor_id, seller_vendor_id, score DESC, id)
    WHERE active;
CREATE INDEX idx_overlaps_pair_active
    ON overlap_opportunities (event_id, seller_vendor_id, buyer_vendor_id, card_id)
    WHERE active;

CREATE TABLE saved_overlaps (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id   UUID        NOT NULL,
    overlap_id  UUID        NOT NULL REFERENCES overlap_opportunities(id),
    event_id    UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_saved_overlap_vendor UNIQUE (vendor_id, overlap_id)
);

CREATE INDEX idx_saved_overlaps_vendor_event
    ON saved_overlaps (vendor_id, event_id, created_at DESC, id);
