ALTER TABLE uploads
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_uploads_tags ON uploads USING GIN (tags);
