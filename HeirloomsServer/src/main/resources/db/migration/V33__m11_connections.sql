-- V33__m11_connections.sql
-- M11 Wave 0: connections identity layer, executor nominations, and capsule recipient linking.
-- Implements ARCH-004 §5 (DDL) and ARCH-010 §3.1 (sequencing).

-- Connections: identity layer for capsule recipients, executor nominees, and
-- Shamir shareholders. Unidirectional (owner → contact). Overlaps with but
-- does not replace the friendships table.

CREATE TABLE connections (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- If bound: linked to an existing Heirlooms account.
    contact_user_id UUID        NULL REFERENCES users(id) ON DELETE SET NULL,
    -- Display name shown in the UI (always present; for bound connections,
    -- kept in sync with the contact's display_name at login time by the client).
    display_name    TEXT        NOT NULL,
    -- Canonical identifier for deferred-pubkey placeholders.
    email           TEXT        NULL,
    -- Cached copy of the contact's P-256 sharing pubkey (base64url, uncompressed).
    -- Populated at bind time; updated if the contact rotates their sharing keypair.
    -- NULL for unbound placeholders.
    sharing_pubkey  TEXT        NULL,
    -- Roles this connection holds for the owner. Array of: 'recipient', 'executor', 'shareholder'.
    roles           TEXT[]      NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT connections_contact_or_email
        CHECK (contact_user_id IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT connections_pubkey_requires_contact
        CHECK (sharing_pubkey IS NULL OR contact_user_id IS NOT NULL),
    UNIQUE (owner_user_id, contact_user_id),  -- one connection per pair (bound case)
    UNIQUE (owner_user_id, email)             -- one connection per email (placeholder case)
);

CREATE INDEX idx_connections_owner ON connections(owner_user_id);
CREATE INDEX idx_connections_contact ON connections(contact_user_id) WHERE contact_user_id IS NOT NULL;

-- Backfill: every existing friend pair becomes a connection for each side.
-- display_name and sharing_pubkey backfilled from users and account_sharing_keys.
-- Uses ON CONFLICT DO NOTHING for idempotency (handles duplicate sharing-key rows per ARCH-010 §4.6).
INSERT INTO connections (id, owner_user_id, contact_user_id, display_name, sharing_pubkey, roles, created_at)
SELECT
    gen_random_uuid(),
    u1.id AS owner_user_id,
    u2.id AS contact_user_id,
    u2.display_name,
    ask.pubkey,
    ARRAY['recipient'],
    f.created_at
FROM friendships f
JOIN users u1 ON u1.id = f.user_id_1
JOIN users u2 ON u2.id = f.user_id_2
LEFT JOIN account_sharing_keys ask ON ask.user_id = u2.id
UNION ALL
SELECT
    gen_random_uuid(),
    u2.id AS owner_user_id,
    u1.id AS contact_user_id,
    u1.display_name,
    ask.pubkey,
    ARRAY['recipient'],
    f.created_at
FROM friendships f
JOIN users u1 ON u1.id = f.user_id_1
JOIN users u2 ON u2.id = f.user_id_2
LEFT JOIN account_sharing_keys ask ON ask.user_id = u1.id
ON CONFLICT DO NOTHING;

-- Executor nominations: offer-accept-revoke lifecycle for posthumous authority.
CREATE TABLE executor_nominations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_id   UUID        NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    status          TEXT        NOT NULL DEFAULT 'pending'
                                CHECK (status IN ('pending', 'accepted', 'declined', 'revoked')),
    offered_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at    TIMESTAMPTZ NULL,
    revoked_at      TIMESTAMPTZ NULL,
    -- Optional human-readable note from the owner to the executor.
    message         TEXT        NULL
);

CREATE INDEX idx_executor_nominations_owner ON executor_nominations(owner_user_id);
CREATE INDEX idx_executor_nominations_connection ON executor_nominations(connection_id);

-- Link capsule_recipients to connections (migration bridge).
-- NULL = legacy free-text row not yet resolved.
ALTER TABLE capsule_recipients
    ADD COLUMN connection_id UUID NULL REFERENCES connections(id) ON DELETE SET NULL;

CREATE INDEX idx_capsule_recipients_connection ON capsule_recipients(connection_id)
    WHERE connection_id IS NOT NULL;
