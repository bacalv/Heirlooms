# Shared Plot Membership — UX Overhaul

Refinement of M10's shared plot feature. Adds invitation acceptance, member-chosen
names, plot lifecycle (open/closed), ownership transfer, soft-delete with restore
window, and a dedicated Shared Plots screen on both platforms.

---

## Motivation

The current M10 implementation auto-joins a recipient the moment the inviter
confirms: the plot appears immediately in their garden under the owner's name.
This is the wrong model — recipients have no say in the name, no moment to
consent, and no way to cleanly leave and come back. This brief replaces that
flow with an invitation-acceptance model and adds the lifecycle controls needed
to make shared plots a durable, low-friction feature.

---

## Design decisions

| Decision | Choice |
|---|---|
| Post-confirmation state | Inviter confirmation → `status = invited`; plot does NOT appear in garden yet |
| Acceptance | Recipient accepts in Shared Plots screen; provides their own `local_name` |
| Name storage | Per-member `local_name` on `plot_members`; plot's own `name` belongs to owner |
| Garden display | Joined shared plots show member's `local_name` + owner attribution subtitle |
| Leave | Status → `left`; plot stays in Shared Plots screen for re-join |
| Re-join name | Silently restore previous `local_name` unless conflict; prompt if conflict |
| Tombstone | Last member leaves → plot soft-deleted (`tombstoned_at`); 90-day restore window |
| Tombstone restore | Only the member who triggered the tombstone can restore it |
| Plot lifecycle | Owner can open/close/reopen the plot; closed = no new items, members can still view |
| Ownership transfer | Owner must transfer to another member before leaving (unless last member) |
| Original creator | `plots.created_by` set at creation; never updated through ownership transfers |
| Decline | No explicit decline — invitations sit until accepted; leaving after join is equivalent |
| Nav placement | Dedicated top-level "Shared" screen (Android tab + web nav item) |

---

## Shared Plots screen

New top-level destination on both platforms. Sections:

1. **Invitations** — plots the user has been invited to but not yet accepted.
   Shows plot name (owner's name), owner attribution, "Join" button.
2. **Joined** — shared plots the user is an active member of. Tapping opens the
   plot. Long-press (Android) / overflow menu (web) offers Leave and (for owner)
   Open/Close, Transfer Ownership.
3. **Left** — plots the user has previously left. Shows greyed state with a
   "Re-join" button.
4. **Recently removed** — tombstoned plots triggered by this user (i.e. they were
   the last member to leave). Shows "Restore" button within the 90-day window.
   Disappears after hard-delete.

---

## Invitation and join flow (revised)

Current flow (M10):
> Recipient stores sharing pubkey → Inviter confirms → plot appears in recipient's garden

New flow:
> Recipient stores sharing pubkey → Inviter confirms → `plot_members` row created with
> `status = invited`, `wrapped_plot_key` already stored → Recipient sees invitation in
> Shared Plots screen → Recipient taps Join, enters name → `status = joined`,
> `local_name` saved → Plot appears in garden

The crypto work (wrapping the plot key to the recipient's sharing pubkey) happens at
inviter-confirmation time, as today. No additional crypto is needed at recipient accept
time — the wrapped key is already in the `plot_members` row.

---

## Leave and re-join

**Leave:** Sets `plot_members.status = left`, `left_at = NOW()`. Plot vanishes from
garden. Invitation record persists; plot appears in "Left" section of Shared Plots screen.

**Tombstone:** After the last member leaves, `plots.tombstoned_at = NOW()`. The plot
enters a soft-deleted state. The member who triggered the tombstone sees it in
"Recently removed" with a Restore button.

**Restore:** Sets `tombstoned_at = NULL` and rejoins the restorer as a member (using
their previous `local_name`, or prompting if conflict). The 90-day cleanup job
hard-deletes tombstoned plots past their window.

**Re-join (non-tombstone):** Any member with `status = left` can re-join from the
Left section. `local_name` is restored silently if no conflict; otherwise a name
prompt appears pre-filled with the previous name.

---

## Plot lifecycle — open / closed

Owner-only control. Toggled from the plot's settings/overflow menu.

| State | New items | Members view existing items |
|---|---|---|
| `open` | Yes | Yes |
| `closed` | No — server rejects `POST /api/plots/:id/items` | Yes |

Re-opening is always permitted by the owner. Closed is not a deletion step — it
is a collaborative moderation tool (e.g. "the album is done, no more additions").

---

## Ownership transfer

Owner selects a current member from the member list. The server swaps roles atomically:
`role = owner` for the new owner, `role = member` for the former owner. The former
owner remains a member and can leave (or stay).

**Edge case — last member:** If the owner is the only member, no transfer is required
before leaving. The leave action tombstones the plot immediately.

**Edge case — transfer then leave:** Former owner leaves after transferring; their
membership enters `status = left` like any other member. They can re-join later as a
regular member.

---

## Schema changes (V26)

```sql
-- V26__shared_plot_membership.sql

-- Extend plot_members
ALTER TABLE plot_members
    ADD COLUMN status      TEXT        NOT NULL DEFAULT 'joined'
        CHECK (status IN ('invited', 'joined', 'left')),
    ADD COLUMN local_name  TEXT        NULL,       -- member's chosen display name
    ADD COLUMN left_at     TIMESTAMPTZ NULL;

-- Backfill existing rows (M10 joined members)
UPDATE plot_members SET status = 'joined' WHERE status IS NULL;

-- Extend plots
ALTER TABLE plots
    ADD COLUMN plot_status   TEXT        NOT NULL DEFAULT 'open'
        CHECK (plot_status IN ('open', 'closed')),
    ADD COLUMN tombstoned_at TIMESTAMPTZ NULL,
    ADD COLUMN tombstoned_by UUID        NULL REFERENCES users(id),
    ADD COLUMN created_by    UUID        NULL REFERENCES users(id);

-- Backfill: original creator is the current owner for all existing plots
UPDATE plots SET created_by = owner_user_id WHERE created_by IS NULL;

-- Index for Shared Plots screen queries
CREATE INDEX idx_plot_members_user_status
    ON plot_members(user_id, status);

-- Index for tombstone cleanup job
CREATE INDEX idx_plots_tombstoned
    ON plots(tombstoned_at)
    WHERE tombstoned_at IS NOT NULL;
```

---

## Server changes

### Updated endpoints

| Method | Path | Change |
|---|---|---|
| `POST /api/plots/:id/members` | Invite a friend | `status = invited` instead of `joined`; no garden appearance yet |
| `POST /api/plots/join` | Redeem invite token | Same: `status = invited` |

### New endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/plots/shared` | List all plot_members rows for current user (all statuses) |
| `POST` | `/api/plots/:id/accept` | Accept invitation; body: `{ localName }` |
| `POST` | `/api/plots/:id/leave` | Leave plot; triggers tombstone if last member |
| `POST` | `/api/plots/:id/rejoin` | Rejoin a left plot; body: `{ localName? }` |
| `POST` | `/api/plots/:id/restore` | Restore tombstoned plot (only tombstoned_by user) |
| `POST` | `/api/plots/:id/transfer` | Transfer ownership; body: `{ newOwnerId }` |
| `PATCH` | `/api/plots/:id/status` | Set open/closed; body: `{ status }` |

### Tombstone cleanup job

Extend the existing compost cleanup job to also hard-delete plots where
`tombstoned_at < NOW() - INTERVAL '90 days'`. Cascades to `plot_members`,
`plot_items`, `plot_staging_decisions`.

---

## Garden display changes

Shared plots (status = `joined`) appear in the garden/plot list alongside personal
plots. Display:

- **Title:** member's `local_name`
- **Subtitle:** "Shared by [owner display name]" (web) / small attribution label
  (Android)
- **Visual distinction:** shared indicator icon (e.g. people icon) on the plot card

Personal plots are unchanged.

---

## Increment plan

### E1 — Schema + server (no client changes yet)

- V26 migration.
- Updated `POST /api/plots/:id/members` and `POST /api/plots/join` to produce
  `status = invited`.
- All new endpoints listed above.
- Tombstone logic in `leave` endpoint.
- Tombstone cleanup job extension.
- `GET /api/plots/shared` endpoint returning all membership states.
- Integration tests: full invitation → accept → leave → rejoin → tombstone → restore cycle.

### E2 — Web

- New "Shared" nav item → Shared Plots screen (four sections: Invitations, Joined,
  Left, Recently removed).
- Accept invitation flow: name prompt, conflict detection.
- Leave and re-join from the screen.
- Restore tombstoned plot.
- Owner controls: Open/Close toggle, Transfer Ownership dialog.
- Garden: shared plots show `local_name` + owner attribution.

### E3 — Android

- New "Shared" bottom tab → Shared Plots screen (matching web sections).
- Accept invitation flow: name prompt, conflict detection.
- Leave and re-join.
- Restore tombstoned plot.
- Owner controls: Open/Close and Transfer in plot settings screen.
- Garden: shared plots show `local_name` + attribution label.

---

## Out of scope

- Push notifications for new invitations (client polls `GET /api/plots/shared`)
- Plot key rotation on member removal (M11+)
- Kicking a member (schema supports it; no UI)
- Public plots (reserved in schema; deferred)
