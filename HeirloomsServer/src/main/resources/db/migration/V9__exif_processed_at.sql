-- V9__exif_processed_at.sql
-- Tracks whether background EXIF extraction has been attempted on a row.
-- NULL = pending. Non-NULL = attempted (regardless of whether useful data was found).

ALTER TABLE uploads ADD COLUMN exif_processed_at TIMESTAMPTZ;

-- Partial index keeps the startup-recovery query cheap as the table grows.
CREATE INDEX idx_uploads_exif_pending ON uploads(uploaded_at)
    WHERE exif_processed_at IS NULL;

-- All rows that existed before D2 were extracted inline at upload time; mark them done.
UPDATE uploads SET exif_processed_at = uploaded_at WHERE exif_processed_at IS NULL;
