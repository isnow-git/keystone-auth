-- Refresh tokens table.
--
-- Reuse detection: every token belongs to a `family_id`. Rotation issues a new
-- token in the same family and marks the previous one `used = TRUE`. If a token
-- already marked `used` is presented again, the entire family is revoked (sign
-- of theft — see ADR-0005).
--
-- Storage: only SHA-256 hashes are persisted; the plaintext value lives in the
-- client cookie. A compromised database does not leak usable refresh tokens.
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    family_id    UUID         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_expiry_future CHECK (expires_at > created_at)
);

CREATE INDEX refresh_tokens_family_idx  ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx    ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at) WHERE revoked = FALSE;

COMMENT ON TABLE  refresh_tokens IS 'Refresh token chain. Rotation + reuse detection per ADR-0005.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex of the refresh token. Plaintext never persisted.';
COMMENT ON COLUMN refresh_tokens.family_id  IS 'All tokens in a rotation chain share this id. Reuse revokes the family.';
