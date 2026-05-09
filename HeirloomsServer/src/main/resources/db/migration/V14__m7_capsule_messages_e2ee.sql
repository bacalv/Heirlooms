-- V14__m7_capsule_messages_e2ee.sql
-- M7 E1: add E2EE columns and storage_class to capsule_messages, mirroring the
-- uploads pattern. body becomes nullable to admit encrypted rows where the
-- plaintext body is replaced by an encrypted blob.

ALTER TABLE capsule_messages
    ALTER COLUMN body DROP NOT NULL;

ALTER TABLE capsule_messages
    ADD COLUMN storage_class    TEXT    NOT NULL DEFAULT 'legacy_plaintext'
        CHECK (storage_class IN ('encrypted', 'public', 'legacy_plaintext')),
    ADD COLUMN envelope_version INTEGER NULL,
    ADD COLUMN encrypted_body   BYTEA   NULL,
    ADD COLUMN body_format      TEXT    NULL,
    ADD COLUMN wrapped_dek      BYTEA   NULL,
    ADD COLUMN dek_format       TEXT    NULL;

-- For 'encrypted' rows: encrypted_body, wrapped_dek, dek_format must be present; body must be NULL.
-- For 'public'/'legacy_plaintext' rows: body must be present; encrypted fields must be NULL.
ALTER TABLE capsule_messages ADD CONSTRAINT capsule_messages_storage_class_consistency
    CHECK (
        (storage_class = 'encrypted'
            AND encrypted_body IS NOT NULL
            AND wrapped_dek    IS NOT NULL
            AND dek_format     IS NOT NULL
            AND body IS NULL)
        OR
        (storage_class IN ('public', 'legacy_plaintext')
            AND body           IS NOT NULL
            AND encrypted_body IS NULL
            AND wrapped_dek    IS NULL
            AND dek_format     IS NULL)
    );
