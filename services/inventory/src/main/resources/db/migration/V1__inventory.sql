CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE inventory_items (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id       UUID          NOT NULL,
    card_id         UUID          NOT NULL,
    event_id        UUID,
    condition       VARCHAR(3)    NOT NULL CHECK (condition IN ('NM', 'LP', 'MP', 'HP', 'DMG')),
    grading_company VARCHAR(32),
    grade           NUMERIC(4, 1),
    quantity        INTEGER       NOT NULL CHECK (quantity > 0),
    asking_price    NUMERIC(12, 2) NOT NULL CHECK (asking_price >= 0),
    priority        VARCHAR(16)   NOT NULL CHECK (priority IN ('normal', 'liquidate')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_grading_pair CHECK (
        (grading_company IS NULL AND grade IS NULL)
        OR (grading_company IS NOT NULL AND grade IS NOT NULL)
    ),
    CONSTRAINT chk_inventory_grade CHECK (grade IS NULL OR (grade >= 1 AND grade <= 10))
);

CREATE INDEX idx_inventory_vendor_updated
    ON inventory_items (vendor_id, updated_at DESC, id);
CREATE INDEX idx_inventory_event_card
    ON inventory_items (event_id, card_id, asking_price, id);
CREATE INDEX idx_inventory_always_card
    ON inventory_items (card_id, asking_price, id) WHERE event_id IS NULL;
