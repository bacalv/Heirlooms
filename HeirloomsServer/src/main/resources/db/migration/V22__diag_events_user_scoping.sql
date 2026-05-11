-- Add user_id to diagnostic_events for per-user scoping.
-- Pre-M8 rows stay NULL (no owner). New rows get user_id from the auth context.
ALTER TABLE diagnostic_events
    ADD COLUMN user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL;
