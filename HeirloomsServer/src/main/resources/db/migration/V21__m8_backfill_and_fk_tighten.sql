-- V21__m8_backfill_and_fk_tighten.sql
-- M8 E1: seed the founding user, backfill all existing rows, tighten NULLs to FKs.

-- 1. Insert the founding user.
--    auth_verifier and auth_salt are NULL; the owner sets their passphrase on first
--    M8 Android launch via POST /api/auth/setup-existing.
INSERT INTO users (id, username, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'bret', 'Bret');

-- 2. Add user_id to uploads.
ALTER TABLE uploads ADD COLUMN user_id UUID NULL;
UPDATE uploads SET user_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE uploads
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_uploads_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_uploads_user ON uploads (user_id);

-- 3. Add user_id FK to capsules.
ALTER TABLE capsules ADD COLUMN user_id UUID NULL;
UPDATE capsules SET user_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE capsules
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_capsules_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_capsules_user ON capsules (user_id);

-- 4. Tighten plots.owner_user_id (NULL sentinel from V10).
UPDATE plots SET owner_user_id = '00000000-0000-0000-0000-000000000001'
    WHERE owner_user_id IS NULL;
ALTER TABLE plots
    ALTER COLUMN owner_user_id SET NOT NULL,
    ADD CONSTRAINT fk_plots_owner FOREIGN KEY (owner_user_id) REFERENCES users(id);

-- 5. Tighten wrapped_keys.user_id (NULL sentinel from V12).
UPDATE wrapped_keys SET user_id = '00000000-0000-0000-0000-000000000001'
    WHERE user_id IS NULL;
ALTER TABLE wrapped_keys
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_wrapped_keys_user FOREIGN KEY (user_id) REFERENCES users(id);

-- 6. Fix recovery_passphrase: drop single-row sentinel, make user_id the PK.
UPDATE recovery_passphrase
    SET user_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE recovery_passphrase DROP COLUMN id;
ALTER TABLE recovery_passphrase
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT pk_recovery_passphrase PRIMARY KEY (user_id),
    ADD CONSTRAINT fk_recovery_passphrase_user
        FOREIGN KEY (user_id) REFERENCES users(id);

-- 7. Add user_id and pairing columns to pending_device_links.
ALTER TABLE pending_device_links ADD COLUMN user_id UUID NULL
    REFERENCES users(id);
UPDATE pending_device_links
    SET user_id = '00000000-0000-0000-0000-000000000001'
    WHERE user_id IS NULL;

ALTER TABLE pending_device_links ADD COLUMN web_session_id TEXT NULL;
ALTER TABLE pending_device_links ADD COLUMN raw_session_token TEXT NULL;
ALTER TABLE pending_device_links ADD COLUMN session_expires_at TIMESTAMPTZ NULL;
