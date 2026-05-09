-- V13__m7_uploads_e2ee.sql
-- M7 E1: add E2EE columns and storage_class to uploads. Additive only — no existing
-- data is destroyed. All existing rows receive storage_class = 'legacy_plaintext'
-- from the DEFAULT; all new nullable columns default to NULL.

ALTER TABLE uploads
    -- Storage class: first-class property of every upload.
    -- 'encrypted'       = E2EE; server holds ciphertext only. The default for new uploads in M7.
    -- 'public'          = plaintext; admitted in schema but rejected at the API layer until a later milestone.
    -- 'legacy_plaintext'= pre-M7 uploads; transitional state only; cannot be created via API.
    ADD COLUMN storage_class                TEXT    NOT NULL DEFAULT 'legacy_plaintext'
        CHECK (storage_class IN ('encrypted', 'public', 'legacy_plaintext')),

    -- Content encryption envelope (NULL for non-encrypted classes).
    ADD COLUMN envelope_version             INTEGER NULL,
    ADD COLUMN wrapped_dek                  BYTEA   NULL,
    ADD COLUMN dek_format                   TEXT    NULL,

    -- Sensitive EXIF metadata encrypted as a single blob (NULL for non-encrypted classes).
    ADD COLUMN encrypted_metadata           BYTEA   NULL,
    ADD COLUMN encrypted_metadata_format    TEXT    NULL,

    -- Encrypted thumbnail (NULL for non-encrypted classes; legacy/public thumbnails use thumbnail_key).
    ADD COLUMN thumbnail_storage_key        TEXT    NULL,
    ADD COLUMN wrapped_thumbnail_dek        BYTEA   NULL,
    ADD COLUMN thumbnail_dek_format         TEXT    NULL;

-- Storage-class consistency constraint:
-- 'encrypted' rows must have wrapped_dek and dek_format present.
-- 'public' and 'legacy_plaintext' rows must have those fields absent.
ALTER TABLE uploads ADD CONSTRAINT uploads_storage_class_consistency
    CHECK (
        (storage_class = 'encrypted'
            AND wrapped_dek IS NOT NULL
            AND dek_format  IS NOT NULL)
        OR
        (storage_class IN ('public', 'legacy_plaintext')
            AND wrapped_dek IS NULL
            AND dek_format  IS NULL)
    );
