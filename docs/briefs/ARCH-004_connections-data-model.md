# ARCH-004 — Connections Data Model

*Authored: 2026-05-15. Status: approved for M11 implementation.*

This brief designs the identity layer that underlies capsule recipients, executor
nominations, and Shamir Secret Sharing shareholders in Milestone 11. The
connections model is a single place that holds all "people this user cares about
cryptographically" — it is not a social graph, a contacts app, or an address book.

---

## 1. Is a "connection" always a Heirlooms user?

No. A connection can be either:

**a) A bound connection** — the remote party has a live Heirlooms account and a
registered P-256 sharing pubkey (via `account_sharing_keys`). Cryptographic
operations (DEK wrapping, Shamir share delivery) can happen immediately at sealing
time without further ceremony. Most connections entered after M9 will be bound, since
the friend-invite flow automatically provisions both the account and the keypair.

**b) A deferred-pubkey placeholder** — the remote party has not yet created an
account (or has but is not yet a friend). The connection record stores an email
address as the canonical identifier. At sealing time, the capsule's wrapped keys
cannot yet be computed for this party. The server records the email; the actual
DEK wrap is deferred until:
- The placeholder upgrades to a bound connection (the person signs up and the
  author's device notices the pubkey is now available), or
- The capsule is delivered (M12), at which point the delivery system sends an
  invitation email and the recipient completes the wrap on first login — a
  "first-open" key handshake.

This matches the M5 design intent ("Recipients are free-text in v1, becoming
connections at Milestone 7") and the M12 plan ("resolution UI for free-text
recipients"). Deferred-pubkey connections are not a workaround — they are the
correct model for pre-mortem capsules addressed to people who have not yet
joined the platform.

**Design rule:** A capsule sealed with at least one deferred-pubkey recipient MUST
also have at least one other unlock path (tlock gate, or ≥1 executor Shamir share
with a bound connection) to avoid creating an irrecoverable capsule. The server
enforces this at sealing time.

---

## 2. New table vs. extending `friendships`?

### Why not extend `friendships`

`friendships` (V23) is a symmetric, unidirectional join table: two bound users,
ordered by UUID, with a single `created_at`. It has no role concept, no
unidirectionality (Alice's friend list and Bob's friend list are the same row), and
no support for placeholders (both columns are `UUID NOT NULL REFERENCES users(id)`).

Extending it to carry roles (`executor`, `shamir_shareholder`) and optional pubkeys
for placeholders would require structural surgery that breaks the M9 invite
redemption flow and the plot invitation flow that depend on it today.

### Decision: new `connections` table

A new `connections` table is unidirectional (owner → contact), supports both bound
and placeholder entries, and carries role labels without disrupting the existing
`friendships` table. Friendship and connection are overlapping but distinct concepts:

- **Friendship** — mutual, symmetric, established at invite redemption. Controls
  who can invite whom to shared plots and who appears in the sharing recipient picker.
- **Connection** — owned by one user, may be unilateral, carries semantic roles.
  Controls who appears in the capsule recipient picker, the executor nominee picker,
  and the Shamir shareholder picker.

On M11 launch, every friend is automatically backfilled as a connection. The two
tables remain separate and each retains its own semantics.

### Role labels

| Role | Description |
|---|---|
| `recipient` | A person the owner intends to address capsules to |
| `executor` | A person the owner nominates to act on their behalf posthumously (countersigns death verification, holds a Shamir share) |
| `shareholder` | A person holding a Shamir share of the owner's master key (vault recovery) or capsule key |

A single connection can hold multiple roles (a sibling might be both a recipient and
an executor). Roles are stored as a TEXT array on the connection row; individual
active nominations are in separate tables (see §4, §schema below).

---

## 3. Migration path for existing `capsule_recipients` rows

`capsule_recipients` stores free-text `recipient` strings — usually a display name
or email, entered by the user. There is no UUID or pubkey. The migration strategy:

**Step 1 (M11 migration, server-side):** Add a `connection_id UUID NULL` FK column
to `capsule_recipients` pointing to the new `connections` table. Leave it NULL for
all existing rows. The old `recipient TEXT` column stays — it is still authoritative
for display when `connection_id IS NULL`.

**Step 2 (M11 client, soft prompt):** When the owner opens a capsule with unresolved
free-text recipients, the UI shows a soft banner: "These recipients haven't been
linked to Heirlooms accounts yet. Link them so the capsule can be sealed securely."
Tapping links the free-text to a connection (search existing connections, or enter an
email to create a placeholder). Linking sets `connection_id` on the row and
optionally clears the display `recipient` field in favour of `connections.display_name`.

**Step 3 (M12, resolution UI):** Full resolution surface — search by name or email,
batch-link multiple free-text recipients, and prompt for missing pubkeys when the
placeholder's user has not yet signed up.

**Breaking change guard:** No existing behaviour changes at M11. Capsules with all-
NULL `connection_id` rows continue to work exactly as before — they are just not
eligible for M11's cryptographic sealing paths.

---

## 4. Executor nomination lifecycle

An executor nomination is an offer-accept-revoke lifecycle, distinct from simply
labelling a connection as `executor` in the role list.

States:

```
pending  →  accepted  →  (active until revoked)
         ↘  declined
```

The offer is extended unilaterally by the owner (no prior consent required to offer).
The executor must actively accept — passive "I didn't decline" is not sufficient for
a role that involves posthumous legal and cryptographic responsibility.

**Offer:** Owner creates a `executor_nominations` row with `status = 'pending'`. A
push notification or email is sent to the executor inviting them to accept.

**Accept:** Executor responds via the app; `status` moves to `'accepted'`.
The owner's device then wraps one Shamir share to the executor's sharing pubkey and
stores it in `executor_shares`. This decouples key material from the nomination
record itself.

**Decline:** Executor declines; `status` = `'declined'`. The owner is notified. No
share material was ever produced or distributed, so no cryptographic cleanup is needed.

**Revoke:** Owner revokes an accepted nomination; `status` = `'revoked'`. Any
Shamir shares distributed to this executor are rotated (the capsule or vault key
re-split; new shares distributed to remaining executors). Key rotation on revocation
is non-trivial and is tracked as a separate M11 task.

---

## 5. Schema

```sql
-- V31__connections.sql

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
-- display_name and email backfilled from the users table.
-- sharing_pubkey backfilled from account_sharing_keys.
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
```

---

## 6. M11 vs. M12 scope boundary

### Resolved at M11

- `connections` table and backfill migration (V31).
- Soft-link UI: open a capsule → link a free-text recipient to a connection.
- Executor nomination lifecycle (offer, accept/decline, revoke).
- Sharing-pubkey cache on connection rows, populated at friend-bind time.
- `capsule_recipients.connection_id` FK column (nullable; not enforced for
  legacy rows).
- Server validation at sealing: all recipients must either have a bound
  connection with a sharing pubkey, or the capsule must have a tlock gate
  or ≥1 executor share as a fallback.

### Deferred to M12

- **Resolution UI** for capsules with unresolved free-text recipients. Full
  batch-link surface, fuzzy name search, email lookup, mass-resolve workflow.
- **Enforced FK constraint** on `capsule_recipients.connection_id` (can be
  added as a migration once all legacy rows have been resolved, which requires
  either migration automation or user action — M12 decides the strategy).
- **Push/email prompts** to placeholder recipients (they get an invitation
  email at M12 delivery time, not at M11 sealing time).
- **Multi-executor Shamir rotation UI** when an executor is revoked.
- **Connection management UI**: edit display names, merge duplicate entries,
  remove placeholders that were never claimed.

---

## Cross-references

- `ARCH-003_m11-capsule-crypto-brief.md` — uses this connections model for
  recipient pubkey wrapping and Shamir share distribution.
- `docs/briefs/ARCH-005_envelope-format-amendment.md` — algorithm IDs used
  for wrapped capsule keys and Shamir shares.
- `docs/ROADMAP.md` M11 section — "The connections data model lands here."
