CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL CHECK (role IN ('VENDOR', 'ATTENDEE', 'ORGANIZER')),
    shop_name       VARCHAR(255),
    city            VARCHAR(128),
    state           VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Case-insensitive uniqueness on email (lower(email) is the canonical lookup form).
CREATE UNIQUE INDEX uniq_users_email_lower ON users (LOWER(email));
