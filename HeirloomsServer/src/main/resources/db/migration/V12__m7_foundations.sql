-- V12__m7_foundations.sql
-- M7 E1: rename captured_at -> taken_at, create key-management and orphan-tracking tables.

-- 1. Rename captured_at to taken_at on uploads
ALTER TABLE uploads RENAME COLUMN captured_at TO taken_at;

-- 2. Device key store: one row per registered device pubkey.
--    user_id is NULL in M7 (single-user sentinel, mirrors plots pattern); NOT NULL at M8.
CREATE TABLE wrapped_keys (
    id                  UUID PRIMARY KEY,
    user_id             UUID NULL,
    device_id           TEXT NOT NULL UNIQUE,
    device_label        TEXT NOT NULL,
    device_kind         TEXT NOT NULL CHECK (device_kind IN ('android', 'web', 'ios')),
    pubkey_format       TEXT NOT NULL,          -- e.g. 'p256-spki'
    pubkey              BYTEA NOT NULL,
    wrapped_master_key  BYTEA NOT NULL,
    wrap_format         TEXT NOT NULL,          -- e.g. 'p256-ecdh-hkdf-aes256gcm-v1'
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    retired_at          TIMESTAMPTZ NULL        -- NULL = active; non-NULL = soft-retired
);

CREATE INDEX idx_wrapped_keys_active ON wrapped_keys (last_used_at)
    WHERE retired_at IS NULL;

-- 3. Passphrase-wrapped master key backup (single row in M7; user_id becomes PK at M8).
CREATE TABLE recovery_passphrase (
    id                  INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    user_id             UUID NULL,
    wrapped_master_key  BYTEA NOT NULL,
    wrap_format         TEXT NOT NULL,          -- e.g. 'argon2id-aes256gcm-v1'
    argon2_params       JSONB NOT NULL,         -- {m: <kib>, t: <iters>, p: <lanes>}
    salt                BYTEA NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Orphaned-blob tracking: records GCS objects uploaded but not yet confirmed.
--    Rows are deleted on successful /confirm or /migrate calls.
--    A background job (E2) deletes rows older than 24h and their associated blobs.
CREATE TABLE pending_blobs (
    id          UUID PRIMARY KEY,
    storage_key TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_blobs_created_at ON pending_blobs (created_at);
