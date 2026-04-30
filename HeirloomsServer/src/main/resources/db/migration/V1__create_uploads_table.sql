CREATE TABLE uploads (
    id           UUID PRIMARY KEY,
    storage_key  VARCHAR(512)  NOT NULL,
    mime_type    VARCHAR(128)  NOT NULL,
    file_size    BIGINT        NOT NULL,
    uploaded_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uploads_uploaded_at ON uploads (uploaded_at DESC);
