ALTER TABLE uploads ADD COLUMN content_hash VARCHAR(64);

CREATE INDEX idx_uploads_content_hash ON uploads (content_hash) WHERE content_hash IS NOT NULL;

UPDATE uploads SET content_hash = REPLACE(gen_random_uuid()::TEXT || gen_random_uuid()::TEXT, '-', '') WHERE content_hash IS NULL;
