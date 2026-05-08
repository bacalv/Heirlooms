-- V11__view_tracking.sql
-- Track when an upload has been opened in detail view.
-- Used by the "Just arrived" plot predicate: untagged, unviewed, not-in-capsule items.

ALTER TABLE uploads
  ADD COLUMN last_viewed_at TIMESTAMPTZ NULL;

-- Partial index for Just arrived queries: covers the three filterable columns.
-- The not-in-capsule condition is a subquery and cannot be indexed here.
CREATE INDEX idx_uploads_just_arrived ON uploads(uploaded_at DESC)
  WHERE last_viewed_at IS NULL
    AND tags = '{}'
    AND composted_at IS NULL;
