-- SEC-015: add require_biometric flag to user accounts
-- Default FALSE = opt-in, no forced migration for existing users.
ALTER TABLE users ADD COLUMN IF NOT EXISTS require_biometric BOOLEAN NOT NULL DEFAULT FALSE;
