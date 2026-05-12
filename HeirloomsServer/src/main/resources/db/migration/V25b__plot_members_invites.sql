-- M10 E3: Shared plot membership, wrapped plot keys, and invite tokens.

-- Members of shared/public plots (including the owner with role = 'owner')
CREATE TABLE plot_members (
    plot_id          UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL REFERENCES users(id),
    role             TEXT        NOT NULL CHECK (role IN ('owner', 'member')),
    wrapped_plot_key BYTEA       NULL,    -- plot_key wrapped to this member's sharing pubkey
    plot_key_format  TEXT        NULL,    -- 'p256-ecdh-hkdf-aes256gcm-v1'
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plot_id, user_id)
);

CREATE INDEX idx_plot_members_user ON plot_members(user_id);

-- Invite tokens for plot membership (fallback for non-friends)
CREATE TABLE plot_invites (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plot_id             UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    created_by          UUID        NOT NULL REFERENCES users(id),
    token               TEXT        NOT NULL UNIQUE,
    -- two-step async join: recipient pubkey stored at redeem time; confirmed by inviter
    recipient_user_id   UUID        NULL REFERENCES users(id),
    recipient_pubkey    TEXT        NULL,
    used_by             UUID        NULL REFERENCES users(id),
    used_at             TIMESTAMPTZ NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plot_invites_token ON plot_invites(token) WHERE used_at IS NULL;
