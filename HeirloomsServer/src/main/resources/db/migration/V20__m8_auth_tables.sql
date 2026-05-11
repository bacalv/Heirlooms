-- V20__m8_auth_tables.sql
-- M8 E1: users, sessions, and invites tables for per-user auth.

CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username        TEXT        NOT NULL UNIQUE,
    display_name    TEXT        NOT NULL,
    auth_verifier   BYTEA       NULL,             -- SHA256(auth_key); NULL until passphrase set
    auth_salt       BYTEA       NULL,             -- 16 bytes; NULL until passphrase set
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id),
    token_hash      BYTEA       NOT NULL UNIQUE,  -- SHA256(bearer token); raw token never stored
    device_kind     TEXT        NOT NULL CHECK (device_kind IN ('android', 'web', 'ios')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_sessions_token   ON user_sessions (token_hash);
CREATE INDEX idx_user_sessions_expiry  ON user_sessions (expires_at);
CREATE INDEX idx_user_sessions_user    ON user_sessions (user_id);

CREATE TABLE invites (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token           TEXT        NOT NULL UNIQUE,
    created_by      UUID        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ NULL,
    used_by         UUID        NULL REFERENCES users(id)
);

CREATE INDEX idx_invites_token ON invites (token) WHERE used_at IS NULL;
