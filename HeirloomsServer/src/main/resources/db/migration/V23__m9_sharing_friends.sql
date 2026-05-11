-- M9: account-level sharing keypairs, friendships, shared upload provenance

CREATE TABLE account_sharing_keys (
    user_id         UUID PRIMARY KEY REFERENCES users(id),
    pubkey          TEXT NOT NULL,
    wrapped_privkey TEXT NOT NULL,
    wrap_format     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE friendships (
    user_id_1  UUID NOT NULL REFERENCES users(id),
    user_id_2  UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id_1, user_id_2),
    CHECK (user_id_1 < user_id_2)
);

-- Backfill friendships from already-redeemed invites.
INSERT INTO friendships (user_id_1, user_id_2, created_at)
SELECT LEAST(created_by, used_by), GREATEST(created_by, used_by), used_at
FROM invites
WHERE used_by IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE uploads
    ADD COLUMN shared_from_upload_id UUID REFERENCES uploads(id) ON DELETE SET NULL,
    ADD COLUMN shared_from_user_id   UUID REFERENCES users(id)   ON DELETE SET NULL;
