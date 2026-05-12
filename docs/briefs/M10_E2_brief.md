# SE Brief: M10 E2 — Flows + Staging

**Date:** 12 May 2026
**Milestone:** M10 — Shared Plots
**Increment:** E2 of 4
**Type:** Server + Web. No Android (E4).

---

## Goal

Introduce flows — automatic routing from a criteria expression into any collection
plot — and a staging review gate. After E2, a user can create a private collection
plot (explicit items, not criteria-driven), attach a flow to it, and review items
in staging before they enter the collection. Staging is a derived view (not stored):
items that match the flow's criteria and have no decision yet. Approving moves an
item into the collection; rejecting excludes it persistently (but reversibly).

Shared plots with E2EE arrive in E3. E2 works entirely within private plots.

---

## New concepts

**Collection plot:** a plot with `criteria IS NULL`. Its contents are stored in
`plot_items`, not derived from a query. Created like any other plot but with no
`criteria` field in the request body. Can be private, shared, or public (only
private in E2; shared/public in E3).

**Flow:** a named rule. Evaluates `criteria` against the authenticated user's
uploads and routes matching items toward `target_plot_id`. If `requires_staging`
is true, items enter the staging queue first; if false, they go straight to
`plot_items` on the next evaluation pass.

**Staging:** a live derived view per (user, flow). Items currently matching the
flow's criteria that have no entry in `plot_staging_decisions` and are not already
in `plot_items`. Criteria changes automatically update staging with no server-side
bookkeeping.

---

## Migration: V25 partial

```sql
-- V25a__flows_staging.sql

-- Explicit item collections for non-criteria-driven plots
CREATE TABLE plot_items (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plot_id               UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    upload_id             UUID        NOT NULL REFERENCES uploads(id),
    added_by              UUID        NOT NULL REFERENCES users(id),
    source_flow_id        UUID        NULL,        -- FK added after flows table
    -- E2EE columns (NULL for private plots; populated in E3 for shared plots)
    wrapped_item_dek      BYTEA       NULL,
    item_dek_format       TEXT        NULL,
    wrapped_thumbnail_dek BYTEA       NULL,
    thumbnail_dek_format  TEXT        NULL,
    added_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (plot_id, upload_id)
);

-- Flows: criteria → target collection plot
CREATE TABLE flows (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users(id),
    name             TEXT        NOT NULL,
    criteria         JSONB       NOT NULL,
    target_plot_id   UUID        NOT NULL REFERENCES plots(id),
    requires_staging BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE plot_items
    ADD CONSTRAINT fk_plot_items_flow
    FOREIGN KEY (source_flow_id) REFERENCES flows(id) ON DELETE SET NULL;

-- Per-item staging decisions (scoped to the plot, not the flow)
-- No row = pending (appears in staging if criteria still matches)
CREATE TABLE plot_staging_decisions (
    plot_id        UUID        NOT NULL REFERENCES plots(id)   ON DELETE CASCADE,
    upload_id      UUID        NOT NULL REFERENCES uploads(id),
    decision       TEXT        NOT NULL CHECK (decision IN ('approved', 'rejected')),
    source_flow_id UUID        NULL     REFERENCES flows(id)   ON DELETE SET NULL,
    decided_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plot_id, upload_id)
);

CREATE INDEX idx_plot_staging ON plot_staging_decisions(plot_id, decision);
CREATE INDEX idx_flows_user   ON flows(user_id);
```

---

## Staging query

Staging for a flow is computed at request time — not stored. The server builds this
query using `CriteriaEvaluator` to expand `flows.criteria`:

```sql
SELECT u.*
FROM uploads u
WHERE u.user_id = :userId
  AND [CriteriaEvaluator(flow.criteria, userId)]
  AND NOT EXISTS (
      SELECT 1 FROM plot_staging_decisions psd
      WHERE psd.plot_id = :plotId
        AND psd.upload_id = u.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM plot_items pi
      WHERE pi.plot_id = :plotId
        AND pi.upload_id = u.id
  );
```

Un-rejecting an item: `DELETE FROM plot_staging_decisions WHERE plot_id = ? AND upload_id = ?`
→ item reappears in staging immediately if it still matches criteria.

---

## Server: validation rules

- `target_plot_id` must reference a collection plot (`criteria IS NULL`). Return 400
  if the target plot has criteria — flows route into collections, not queries.
- A flow's `criteria` is validated by `CriteriaEvaluator` (same rules as plot criteria).
- `requires_staging = false` AND target plot `visibility = 'public'` → server rejects
  with 400. Public plots always require staging (not yet in E2, but enforce from day one).
- A flow's `user_id` must match the authenticated user — you cannot create flows on
  behalf of others.

---

## Server: new endpoints

### Flow CRUD

**`GET /api/flows`**
Returns all flows for the authenticated user.
```json
[
  {
    "id": "uuid",
    "name": "Photos of Sadaar → Family",
    "criteria": { ... },
    "targetPlotId": "uuid",
    "requiresStaging": true,
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

**`POST /api/flows`**
Create a flow.
```json
{
  "name": "Photos of Sadaar → Family",
  "criteria": { "type": "and", "operands": [...] },
  "targetPlotId": "uuid",
  "requiresStaging": true
}
```
Response 201 with the created flow.

**`PUT /api/flows/:id`**
Update a flow (name, criteria, requiresStaging). `target_plot_id` is immutable after
creation — delete and recreate to retarget.
Response 200.

**`DELETE /api/flows/:id`**
Deletes the flow. `plot_staging_decisions.source_flow_id` and
`plot_items.source_flow_id` SET NULL on delete (FK constraint). Existing items in the
collection are not removed — they were approved by the user, not by the flow.
Response 204.

### Staging

**`GET /api/flows/:id/staging`**
Returns the staging query result for this flow (pending items, paginated with cursor).
Cursor pagination follows the same pattern as `/api/content/uploads`.

**`GET /api/plots/:id/staging`**
All pending staging items across all flows targeting this plot. Useful for the plot-level
staging view (shows items regardless of which flow sourced them).

**`POST /api/plots/:id/staging/:uploadId/approve`**
Creates a `plot_items` row and a `plot_staging_decisions(approved)` row.
- For private plots: `wrapped_item_dek` and related columns are left NULL (client
  accesses the item via its own upload record, no re-wrapping needed).
- For shared plots (E3): body must include `wrappedItemDek`, `itemDekFormat`,
  `wrappedThumbnailDek`, `thumbnailDekFormat`. Return 400 if omitted on a shared plot.
Response 204.

**`POST /api/plots/:id/staging/:uploadId/reject`**
Creates a `plot_staging_decisions(rejected)` row. Idempotent — if the item was
previously approved, this call returns 409 (cannot reject an already-approved item;
remove it from `plot_items` separately).
Response 204.

**`DELETE /api/plots/:id/staging/:uploadId/decision`**
Removes the decision row (un-reject). Item reappears in staging if criteria still
matches. Returns 404 if no decision exists.
Response 204.

**`GET /api/plots/:id/staging/rejected`**
Returns items with `decision = 'rejected'` for this plot. For the recovery UI.

### Collection plot items

**`GET /api/plots/:id/items`**
List items in a collection plot (criteria IS NULL). Returns upload records for
each `plot_items` row. Pagination: cursor on `added_at`.

**`POST /api/plots/:id/items`**
Manually add an item to a collection plot (bypassing staging).
```json
{ "uploadId": "uuid" }
```
For private plots: no DEK fields needed.
For shared plots (E3): body must include DEK fields.
Response 201.

**`DELETE /api/plots/:id/items/:uploadId`**
Remove an item from a collection plot. Owner can remove any item. Member can
remove only items they added (`added_by = userId`). Returns 404 if not found,
403 if member tries to remove another member's item.
Response 204.

---

## Web changes

### Flow management (`FlowsPage.jsx` or inline in `GardenPage`)

- List of flows with name, target plot, criteria summary, requires_staging toggle.
- Create flow: criteria builder (from E1) + target collection plot picker + staging toggle.
- Edit flow: name and criteria editable; target plot immutable.
- Delete flow: confirmation dialog; note that existing items in the collection are kept.

### Staging panel (`StagingPanel.jsx`)

Accessible from:
1. A shared/collection plot's detail view (shows all pending items for that plot).
2. A flow's detail view (shows pending items for that specific flow).

**Pending items:** grid of thumbnails. Tapping opens a detail view with approve/reject.
**Rejected items:** collapsed section at the bottom. Each has an "Add after all" button.
**Empty state:** "Nothing waiting for review" when staging is empty.

### Collection plots in Garden

Collection plots (criteria IS NULL) appear in the Garden sidebar with a different
visual treatment from query plots (e.g. a stack-of-photos icon vs a filter icon).
Their item count is from `plot_items`, not from a criteria query.

---

## Tests

### Schema migration (~4)

1. V25a runs cleanly on a fresh database.
2. V25a runs cleanly after V24.
3. `flows`, `plot_staging_decisions`, `plot_items` tables exist with correct columns.
4. FK from `plot_items.source_flow_id → flows.id ON DELETE SET NULL` — deleting a flow
   sets `source_flow_id` to NULL on orphaned plot_items rows.

### Flow CRUD integration tests (~8)

5. `POST /api/flows` with valid criteria and collection target → 201.
6. `POST /api/flows` targeting a query plot (criteria IS NOT NULL) → 400.
7. `GET /api/flows` → returns own flows only (isolation: another user's flows not returned).
8. `PUT /api/flows/:id` → criteria updated, evaluation reflects new criteria.
9. `DELETE /api/flows/:id` → flow deleted; approved items in `plot_items` remain.
10. `DELETE /api/flows/:id` → `plot_items.source_flow_id` set to NULL for items sourced by this flow.
11. Another user's flow → 404.
12. `POST /api/flows` with `near` atom → 400 (not implemented).

### Staging integration tests (~10)

13. Item matching flow criteria → appears in `GET /api/flows/:id/staging`.
14. Item approved → no longer in staging; appears in `GET /api/plots/:id/items`.
15. Item rejected → no longer in staging; appears in `GET /api/plots/:id/staging/rejected`.
16. Un-reject (DELETE decision) → item reappears in staging.
17. Change tag on an item so it no longer matches flow criteria → disappears from staging.
18. Re-tag item so it matches again → reappears in staging (no stale state).
19. `requires_staging = false` flow → items added directly to `plot_items` without staging decision.
20. Approve already-approved item → 409.
21. Reject an approved item → 409 (must remove from `plot_items` first).
22. `GET /api/plots/:id/items` returns correct items after approvals.

### Regression

23. All E1 tests still pass.
24. All existing upload/capsule/auth isolation tests still pass.

---

## What E2 does NOT include

- Shared plot E2EE (wrapped_item_dek populated for shared plots — E3).
- `plot_members`, `plot_invites` (E3).
- `near` atom (still deferred).
- Android (E4).

---

## Acceptance criteria

1. `./gradlew test` passes — all new tests green, no regressions.
2. Create a collection plot, create a flow targeting it, upload an item matching the
   flow criteria — item appears in staging.
3. Approve the item — it leaves staging and appears in the collection.
4. Reject a different item — it leaves staging and appears in rejected list.
5. Un-reject — item reappears in staging.
6. Remove the matching tag — item disappears from staging with no manual cleanup.
7. Flow with `requires_staging = false` — items bypass staging and land directly in
   the collection.

---

## Documentation updates

- `docs/VERSIONS.md` — entry when E2 ships (v0.48.0)
- `docs/PROMPT_LOG.md` — standard entry
