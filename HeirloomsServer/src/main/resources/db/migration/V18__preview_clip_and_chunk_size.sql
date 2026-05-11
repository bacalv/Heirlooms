ALTER TABLE uploads
    ADD COLUMN preview_storage_key TEXT,
    ADD COLUMN wrapped_preview_dek  BYTEA,
    ADD COLUMN preview_dek_format   TEXT,
    ADD COLUMN plain_chunk_size     INTEGER;
