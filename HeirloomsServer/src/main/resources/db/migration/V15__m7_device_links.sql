-- V15__m7_device_links.sql
-- M7 E2: device-link state machine table for the trusted-device key-wrap handshake.

CREATE TABLE pending_device_links (
    id                  UUID PRIMARY KEY,
    one_time_code       TEXT NOT NULL UNIQUE,        -- shown on trusted device, typed on new device
    expires_at          TIMESTAMPTZ NOT NULL,         -- link expires after 15 minutes
    state               TEXT NOT NULL DEFAULT 'initiated'
                            CHECK (state IN ('initiated', 'device_registered', 'wrap_complete')),
    -- Filled in when new device submits its pubkey (step 2 of link flow):
    new_device_id       TEXT NULL,
    new_device_label    TEXT NULL,
    new_device_kind     TEXT NULL CHECK (new_device_kind IN ('android', 'web', 'ios')),
    new_pubkey_format   TEXT NULL,
    new_pubkey          BYTEA NULL,
    -- Filled in when trusted device posts the wrapped master key (step 4):
    wrapped_master_key  BYTEA NULL,
    wrap_format         TEXT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_device_links_code ON pending_device_links (one_time_code)
    WHERE state = 'initiated';
