-- V34__m11_capsule_crypto.sql
-- M11 Wave 0: capsule cryptographic columns, per-recipient key table, executor shares.
-- Implements ARCH-003 §2 (DDL) and ARCH-010 §3.1 (sequencing).
-- DEPENDS ON V33: capsule_recipient_keys references connections(id);
--                 executor_shares references executor_nominations(id).

-- Per-capsule DEK, generated at creation time and re-wrapped at sealing.
-- Stored as a capsule-ecdh-aes256gcm-v1 asymmetric envelope (primary recipient).
-- NULL until the capsule is sealed.
ALTER TABLE capsules
    ADD COLUMN wrapped_capsule_key  BYTEA    NULL,
    ADD COLUMN capsule_key_format   TEXT     NULL
        CHECK (capsule_key_format IN ('capsule-ecdh-aes256gcm-v1', 'tlock-bls12381-v1'));

-- tlock fields. NULL on all non-tlock capsules.
-- tlock_round and tlock_chain_id identify which drand round gates the key.
-- tlock_wrapped_key is the IBE ciphertext encrypting DEK_client (blinding scheme).
-- tlock_dek_tlock = DEK XOR DEK_client; stored at sealing, served via /tlock-key.
-- tlock_key_digest = SHA-256(DEK_tlock); stored for tamper detection at delivery time.
-- SECURITY: tlock_dek_tlock MUST NEVER appear in application logs, access logs,
-- or request traces. See ARCH-003 §6.5 and ARCH-006 §6.2 for the full prohibition.
ALTER TABLE capsules
    ADD COLUMN tlock_round          BIGINT   NULL,
    ADD COLUMN tlock_chain_id       TEXT     NULL,
    ADD COLUMN tlock_wrapped_key    BYTEA    NULL,
    ADD COLUMN tlock_dek_tlock      BYTEA    NULL,
    ADD COLUMN tlock_key_digest     BYTEA    NULL;

-- Shamir fields. NULL on non-Shamir capsules.
-- These columns describe the split configuration; actual shares live in executor_shares.
ALTER TABLE capsules
    ADD COLUMN shamir_threshold     SMALLINT NULL CHECK (shamir_threshold >= 1),
    ADD COLUMN shamir_total_shares  SMALLINT NULL CHECK (shamir_total_shares >= 1),
    ADD CONSTRAINT shamir_threshold_lte_total
        CHECK (shamir_threshold IS NULL OR shamir_threshold <= shamir_total_shares);

-- sealed_at: timestamp when the capsule was sealed via /seal.
-- NULL until sealed. Referenced by ARCH-010 §5 /seal response spec.
ALTER TABLE capsules
    ADD COLUMN sealed_at TIMESTAMPTZ NULL;

-- One wrapped DEK per recipient (primary recipient uses capsules.wrapped_capsule_key;
-- additional recipients use this table).
-- For tlock capsules, wrapped_blinding_mask holds ECDH-wrap(DEK_client) for the
-- Android/web blinded delivery path. See ARCH-003 §9 for the full blinding scheme.
CREATE TABLE capsule_recipient_keys (
    capsule_id              UUID        NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    connection_id           UUID        NOT NULL REFERENCES connections(id),
    wrapped_capsule_key     BYTEA       NOT NULL,
    capsule_key_format      TEXT        NOT NULL DEFAULT 'capsule-ecdh-aes256gcm-v1',
    -- wrapped_blinding_mask: ECDH-wrap(DEK_client) — non-NULL only on tlock capsules.
    -- Used by Android/web to recover DEK_client and complete blinded decryption.
    -- NULL on non-tlock capsules; iOS never reads this field.
    wrapped_blinding_mask   BYTEA       NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (capsule_id, connection_id)
);

-- Shamir shares distributed to executors.
-- Each row is a shamir-share-v1 envelope wrapped to the executor's sharing pubkey.
-- NULL capsule_id means a master-key share (vault recovery), not a capsule-key share.
CREATE TABLE executor_shares (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    capsule_id      UUID        NULL REFERENCES capsules(id) ON DELETE CASCADE,
    nomination_id   UUID        NOT NULL REFERENCES executor_nominations(id),
    share_index     SMALLINT    NOT NULL,
    wrapped_share   BYTEA       NOT NULL,
    share_format    TEXT        NOT NULL DEFAULT 'shamir-share-v1',
    distributed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executor_shares_capsule ON executor_shares(capsule_id)
    WHERE capsule_id IS NOT NULL;
CREATE INDEX idx_executor_shares_nomination ON executor_shares(nomination_id);
