-- V8__compost.sql
-- Soft-delete mechanism for uploads.
-- Items with non-null composted_at are in the compost heap and excluded
-- from default upload listings. After 90 days, lazy cleanup hard-deletes
-- both the DB row and the corresponding GCS object.

ALTER TABLE uploads ADD COLUMN composted_at TIMESTAMP WITH TIME ZONE;

-- Partial index for the heap query: "show me composted items".
CREATE INDEX uploads_composted_at_idx ON uploads (composted_at)
    WHERE composted_at IS NOT NULL;
