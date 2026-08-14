CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID         NOT NULL,
    name         VARCHAR(200) NOT NULL,
    city         VARCHAR(120) NOT NULL,
    state        VARCHAR(80)  NOT NULL,
    venue        VARCHAR(200),
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_dates CHECK (end_date >= start_date)
);

CREATE TABLE event_registrations (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL,
    role          VARCHAR(16) NOT NULL CHECK (role IN ('vendor', 'attendee')),
    booth         VARCHAR(40),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_event_registration UNIQUE (event_id, user_id),
    CONSTRAINT chk_booth_role CHECK (role = 'vendor' OR booth IS NULL)
);

CREATE INDEX idx_events_dates ON events (start_date, end_date);
CREATE INDEX idx_event_registrations_event_role
    ON event_registrations (event_id, role, registered_at);
