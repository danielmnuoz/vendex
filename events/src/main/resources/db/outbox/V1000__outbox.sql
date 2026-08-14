-- Transactional outbox table, shared by every producing service.
--
-- This migration ships inside the `events` module jar. A producing service
-- picks it up by adding `classpath:db/outbox` to its Flyway locations:
--
--   spring.flyway.locations: classpath:db/migration,classpath:db/outbox
--
-- Version 1000 is namespaced high so it never collides with a service's own
-- V1, V2, ... migrations (Flyway requires globally-unique versions across all
-- locations). Keep service migrations below 1000.

CREATE TABLE IF NOT EXISTS outbox (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64)  NOT NULL,           -- e.g. "inventory_item" (audit/debug)
    aggregate_id   VARCHAR(64)  NOT NULL,           -- the entity's id (audit/debug)
    topic          VARCHAR(128) NOT NULL,           -- Kafka topic
    partition_key  VARCHAR(128),                    -- Kafka message key (nullable)
    payload        JSONB        NOT NULL,           -- JSON event body, published verbatim
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ                       -- NULL until the relay ships it
);

-- The relay only ever scans unpublished rows; a partial index keeps that poll
-- cheap as the table accumulates published history.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox (created_at)
    WHERE published_at IS NULL;
