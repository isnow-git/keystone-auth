-- Users table: stores authenticated principals.
-- Passwords are persisted as Argon2id-encoded strings (see ADR-0002).
CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    roles           TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT users_roles_non_empty CHECK (cardinality(roles) > 0)
);

CREATE INDEX users_email_lower_idx ON users (LOWER(email));

COMMENT ON TABLE  users IS 'Authenticated principals.';
COMMENT ON COLUMN users.password_hash IS 'Argon2id-encoded password hash (never plaintext).';
COMMENT ON COLUMN users.roles IS 'Granted authority strings, e.g. {ROLE_USER, ROLE_ADMIN}.';
