CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE event_schedules (
    event_id       UUID        PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE overlap_snapshots (
    overlap_id              UUID           PRIMARY KEY,
    event_id                UUID           NOT NULL,
    buyer_vendor_id         UUID           NOT NULL,
    seller_vendor_id        UUID           NOT NULL,
    card_id                 UUID           NOT NULL,
    inventory_priority      VARCHAR(16)    NOT NULL,
    score                   NUMERIC(5, 2)  NOT NULL,
    payload                 JSONB           NOT NULL,
    active                  BOOLEAN         NOT NULL,
    action_rank             SMALLINT        NOT NULL,
    occurred_at             TIMESTAMPTZ     NOT NULL,
    CONSTRAINT chk_notification_overlap_priority
        CHECK (inventory_priority IN ('normal', 'liquidate')),
    CONSTRAINT chk_notification_overlap_score CHECK (score >= 0 AND score <= 100),
    CONSTRAINT chk_notification_overlap_vendors
        CHECK (buyer_vendor_id <> seller_vendor_id)
);

CREATE TABLE saved_plan_activations (
    saved_overlap_id  UUID        PRIMARY KEY,
    overlap_id        UUID        NOT NULL,
    vendor_id         UUID        NOT NULL,
    event_id          UUID        NOT NULL,
    buyer_vendor_id   UUID        NOT NULL,
    seller_vendor_id  UUID        NOT NULL,
    card_id           UUID        NOT NULL,
    score             NUMERIC(5, 2) NOT NULL,
    saved_at          TIMESTAMPTZ NOT NULL,
    activated_at      TIMESTAMPTZ,
    CONSTRAINT uq_saved_plan_vendor_overlap UNIQUE (vendor_id, overlap_id),
    CONSTRAINT chk_saved_plan_participant
        CHECK (vendor_id = buyer_vendor_id OR vendor_id = seller_vendor_id)
);

CREATE TABLE notifications (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id               UUID        NOT NULL,
    event_id                UUID        NOT NULL,
    trigger_type            VARCHAR(32) NOT NULL,
    overlap_id              UUID        NOT NULL,
    card_id                 UUID        NOT NULL,
    counterparty_vendor_id  UUID        NOT NULL,
    payload                 JSONB       NOT NULL,
    active                  BOOLEAN     NOT NULL DEFAULT TRUE,
    is_read                 BOOLEAN     NOT NULL DEFAULT FALSE,
    source_occurred_at      TIMESTAMPTZ NOT NULL,
    available_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_overlap_trigger
        UNIQUE (vendor_id, overlap_id, trigger_type),
    CONSTRAINT chk_notification_trigger CHECK (trigger_type IN (
        'overlap_buylist', 'overlap_liquidate', 'saved_overlap_active'))
);

CREATE INDEX idx_notifications_vendor_feed
    ON notifications (vendor_id, available_at DESC, updated_at DESC, id)
    WHERE active;
CREATE INDEX idx_notifications_event
    ON notifications (event_id, vendor_id, trigger_type)
    WHERE active;

CREATE TABLE notification_preferences (
    user_id          UUID        PRIMARY KEY,
    in_app_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    triggers         JSONB       NOT NULL DEFAULT '{
        "overlap_buylist": true,
        "overlap_liquidate": true,
        "saved_overlap_active": true
    }'::jsonb,
    digest_mode      VARCHAR(16) NOT NULL DEFAULT 'real_time',
    event_mutes      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_notification_digest
        CHECK (digest_mode IN ('real_time', 'daily', 'event_only'))
);

CREATE TABLE overlap_interests (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    overlap_id               UUID          NOT NULL,
    interested_vendor_id     UUID          NOT NULL,
    counterparty_vendor_id   UUID          NOT NULL,
    event_id                 UUID          NOT NULL,
    score                    NUMERIC(5, 2) NOT NULL,
    status                   VARCHAR(16)   NOT NULL DEFAULT 'pending',
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_overlap_interest UNIQUE (overlap_id, interested_vendor_id),
    CONSTRAINT chk_overlap_interest_parties
        CHECK (interested_vendor_id <> counterparty_vendor_id),
    CONSTRAINT chk_overlap_interest_score CHECK (score >= 0 AND score <= 100),
    CONSTRAINT chk_overlap_interest_status
        CHECK (status IN ('pending', 'revealed', 'declined', 'expired'))
);

CREATE INDEX idx_overlap_interests_counterparty
    ON overlap_interests (counterparty_vendor_id, overlap_id, score DESC, created_at, id);
