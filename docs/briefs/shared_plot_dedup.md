# SE Brief: Shared plot duplicate item prevention

**Date:** 14 May 2026
**Type:** Server only. No Android or web changes.

---

## Goal

Prevent the same photo or video appearing twice in a shared plot when two members
independently upload the same file (e.g. both photographing the same event moment)
and both have flows that stage it to the same shared plot.

---

## Background

Personal gardens already have upload-time dedup via `content_hash` (v0.50.4): the
server checks SHA-256 before allocating a GCS slot and returns early if the hash
already exists for that user.

Shared plots have no equivalent guard. Two members can each upload the same file,
each gets their own `uploads` row, each has a flow that stages the item to the shared
plot, and both approvals write a `plot_items` row — leaving two visually identical
entries in the plot, encrypted with different DEKs.

Loops (A's item triggers B's flow which re-stages to A's plot...) do not occur
because flows operate only on a user's own personal uploads. Items received in a
shared plot are never re-staged by the recipient's flows. This feature addresses
only the independent-duplicate case.

---

## Design

### Where the check happens

At **staging time** — when a flow (or any future staging trigger) tries to add an
item to a shared plot's staging queue — before the row is written to `plot_staging`.

Checking here means duplicates never appear in the staging queue at all. The user
(or flow automation) gets a success response and the item simply doesn't enter the
queue.

### What the check looks at

**Approved items only (`plot_items`).** If the same content is already confirmed in
the plot, staging is silently skipped. Items already sitting in staging for other
members are not checked — this keeps the logic simple and avoids a race window
between two concurrent staging attempts (which is harmless: only the first approval
will write a `plot_items` row; the second is a no-op at approval time due to the
same guard).

### The check

```sql
SELECT 1
FROM plot_items pi
JOIN uploads u ON pi.upload_id = u.id
WHERE pi.plot_id    = :plotId
  AND u.content_hash = :contentHash
  AND u.content_hash IS NOT NULL
LIMIT 1
```

If a row is returned: skip staging, return 200 (success). The caller behaves
identically to a successful stage — no warning, no distinction.

The `AND u.content_hash IS NOT NULL` guard means null-hash items always pass
through (see Known gap below).

### Scope

Shared plots only. Personal plots are protected at upload time and do not need a
plot-level check.

### Response

200 in all cases (duplicate detected or not). The server's job is to ensure the
plot contains no duplicates; whether the item was newly staged or already present
is not information the caller needs.

---

## Known gap — null content_hash

Checkpoint-resumed uploads (v0.50.4) do not compute `content_hash` at upload time
and store `null` in the `uploads` row. These items bypass the dedup check silently.

**Why:** The hash check fires before any GCS work on fresh uploads. A resumed upload
begins mid-session, after the GCS slot is already allocated, so the check is skipped
and `content_hash` is never stored.

**In practice:** Rare. Checkpoint resumes only occur on genuinely interrupted uploads.
If two members both resume-uploaded the same file, a duplicate could appear in the
shared plot — but the user would never know why, and both copies are valid content.
Accepted for now.

**How to close this gap later (not in scope for this increment):**

In `Uploader.kt`, at the point where the confirm body is assembled for a
checkpoint-resumed upload, compute SHA-256 of the source file (which is still in
`cacheDir`) and include it as `contentHash` in the confirm request. The server's
confirm endpoint already stores `contentHash` when present (v0.50.4). Once the hash
is stored, future staging attempts for the same file will be covered by the check
above.

This is a small Android-only change that can be delivered as a standalone fix
whenever the gap becomes a real concern.

---

## Changes required

### `Database.kt` (or query layer)

Add `existsInPlotByContentHash(plotId: UUID, contentHash: String): Boolean`.
Executes the query above. Returns false immediately if `contentHash` is blank
(null-hash guard at the call site).

### `PlotHandler.kt` (or wherever staging is handled)

In the handler that writes to `plot_staging`, before the INSERT:

1. If the item's `content_hash` is not null: call `existsInPlotByContentHash`.
2. If true: return 200 immediately — no staging row written.
3. Otherwise: proceed as normal.

---

## Out of scope

- Web or Android client changes: none. The server returns 200 in all cases.
- Personal plot dedup: already handled at upload time.
- Loop prevention: not a real problem in the current architecture (see Background).
- Null-hash closure: deferred, described above as a future Android-only fix.
- Dedup within the staging queue itself (two concurrent staging attempts): accepted
  race window; harmless in practice.

---

## Acceptance criteria

1. If member A's item with hash `abc123` is already in `plot_items` for a shared
   plot, a staging attempt for any upload with hash `abc123` (by any member)
   returns 200 and writes no row to `plot_staging`.
2. The staging queue never contains two items with the same `content_hash` for the
   same shared plot after this check is in place.
3. Items with `content_hash = null` pass through the check unchanged — no error,
   no block.
4. Personal plot staging is unaffected.
5. All existing integration tests pass.
