# SE Brief: M10 E1 — Predicate/Criteria System

**Date:** 12 May 2026
**Milestone:** M10 — Shared Plots
**Increment:** E1 of 4
**Type:** Server + Web. No Android (E4).

---

## Goal

Replace the `plot_tag_criteria` junction table with a JSONB expression language on
`plots.criteria`. After E1, plots can express arbitrary boolean combinations of
predicates (tags, media type, date ranges, location presence, sharing, capsule
membership, device) rather than just tag lists. Hidden plots (`show_in_garden = false`)
serve as reusable predicate building blocks. The Explore filter state serialises to the
same format, so "Save as plot" captures the full filter — not just tags. No flows, no
shared plots, no Android in this increment.

---

## Migration: V24

```sql
-- V24__predicate_criteria.sql

ALTER TABLE plots
    ADD COLUMN criteria       JSONB   NULL,
    ADD COLUMN show_in_garden BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN visibility     TEXT    NOT NULL DEFAULT 'private'
        CHECK (visibility IN ('private', 'shared', 'public'));

-- Migrate existing tag criteria to the new expression format before dropping the table.
-- Single-tag plots become a tag atom; multi-tag plots become an AND of tag atoms.
-- Plots with no criteria remain NULL (match-nothing; user can rebuild or delete).
UPDATE plots p
SET criteria = (
    SELECT CASE
        WHEN COUNT(*) = 0 THEN NULL
        WHEN COUNT(*) = 1 THEN jsonb_build_object('type', 'tag', 'tag', MIN(tag))
        ELSE jsonb_build_object('type', 'and', 'operands',
            jsonb_agg(jsonb_build_object('type', 'tag', 'tag', tag)
                      ORDER BY tag))
    END
    FROM plot_tag_criteria ptc
    WHERE ptc.plot_id = p.id
)
WHERE NOT p.is_system_defined;

-- System plot gets a first-class criteria expression.
UPDATE plots
    SET criteria = '{"type": "just_arrived"}'::jsonb
    WHERE is_system_defined = TRUE AND name = '__just_arrived__';

DROP TABLE plot_tag_criteria;

-- Partial index: only index plots that appear in the Garden sidebar.
CREATE INDEX idx_plots_garden
    ON plots(owner_user_id, show_in_garden, sort_order)
    WHERE show_in_garden = TRUE;
```

---

## CriteriaEvaluator

New server component: `CriteriaEvaluator.kt`. Takes a criteria JSON object and an
authenticated `userId: UUID`. Returns a SQL fragment (safe for embedding in a
parameterised WHERE clause) and its bound parameters.

Evaluated against the `uploads` table. The `userId` is always AND-ed in as the outer
scope regardless of criteria content — a user can never see another user's uploads via
a plot.

**Atom implementations:**

| Type | SQL fragment |
|---|---|
| `tag` | `tags @> ARRAY[:tag]::text[]` |
| `media_type = image` | `mime_type LIKE 'image/%'` |
| `media_type = video` | `mime_type LIKE 'video/%'` |
| `taken_after` | `taken_at >= :date::date` |
| `taken_before` | `taken_at < (:date::date + INTERVAL '1 day')` |
| `uploaded_after` | `uploaded_at >= :date::date` |
| `uploaded_before` | `uploaded_at < (:date::date + INTERVAL '1 day')` |
| `has_location` | `latitude IS NOT NULL AND longitude IS NOT NULL` |
| `device_make` | `device_make ILIKE :value` |
| `device_model` | `device_model ILIKE :value` |
| `is_received` | `shared_from_user_id IS NOT NULL` |
| `received_from` | `shared_from_user_id = :friendUserId` |
| `in_capsule` | `EXISTS (SELECT 1 FROM capsule_photos cp WHERE cp.upload_id = uploads.id)` |
| `just_arrived` | existing just_arrived predicate (last_viewed_at IS NULL AND tags = '{}' AND composted_at IS NULL AND NOT in active capsule) |
| `composted` | `composted_at IS NOT NULL` |
| `plot_ref` | recursive: resolve referenced plot's criteria (see below) |

**`near` atom:** deferred. GPS coordinates are NULL for all M7+ encrypted uploads.
Implement when a coarse plaintext geohash is added as an opt-in alongside encrypted
metadata. Do not implement the atom in E1 — the evaluator should return a clear error
if it encounters `near`.

**Composition:**
- `and`: `(expr1 AND expr2 AND ...)`
- `or`: `(expr1 OR expr2 OR ...)`
- `not`: `NOT (expr)`

**`plot_ref` evaluation:**
1. Resolve the referenced `plot_id` via a DB lookup (scoped to `owner_user_id = :userId`
   — cross-user plot refs are rejected with a clear error).
2. Evaluate the referenced plot's `criteria` recursively.
3. Track visited plot IDs in a `Set<UUID>` passed through the recursion. If a `plot_id`
   appears twice, throw `CriteriaCycleException` — fail loudly, do not silently return
   empty results.
4. Maximum recursion depth: 10 levels. Exceed this → error.

---

## Updated plot API

`PlotHandler.kt` updated throughout. The `tag_criteria` field is removed from all
requests and responses. Replace with:

**Request body (create / update):**
```json
{
  "name": "Sadaar's photos",
  "criteria": { "type": "and", "operands": [...] },
  "showInGarden": true,
  "visibility": "private"
}
```

**Response (get / list):**
```json
{
  "id": "uuid",
  "name": "Sadaar's photos",
  "criteria": { "type": "and", "operands": [...] },
  "showInGarden": true,
  "visibility": "private",
  "sortOrder": 0,
  "isSystemDefined": false,
  "createdAt": "...",
  "updatedAt": "..."
}
```

**List uploads filtered by plot (`GET /api/content/uploads?plot_id=:id`):**
Retrieve the plot row, pass `criteria` through `CriteriaEvaluator`, use the result as
an additional WHERE clause. The existing cursor pagination, sort, and other query
params compose with it normally.

**Validation:** Before persisting `criteria`, validate the expression tree server-side:
- All node `type` values are known.
- `and`/`or` have at least one operand.
- `not` has exactly one operand.
- `tag` value is non-empty.
- `plot_ref` target exists and belongs to the requesting user.
- No cycles (same cycle detection as evaluation).
- Reject `near` atom in E1 with a clear 400 error.

---

## System plot: `__just_arrived__`

After V24, the system plot has `criteria = {"type": "just_arrived"}`. The evaluator
expands this to the full predicate. The application code that special-cased the system
plot by name can be removed — it now flows through the evaluator like any other plot.
The `is_system_defined` flag remains for UI purposes (cannot be renamed or deleted).

---

## Web changes

### `api.js`

- `createPlot(name, criteria, showInGarden)` — posts `criteria` JSON (not `tag_criteria`).
- `updatePlot(id, name, criteria, showInGarden)` — same.
- `listPlots()` — response now includes `criteria`, `show_in_garden`, `visibility`.
- `listUploads({ plotId })` — unchanged endpoint; server-side changes are transparent.

### Criteria builder component (`CriteriaBuilder.jsx`)

Minimal for E1: supports the most common atoms via the existing filter UI shapes.

| UI control | Criteria atom(s) produced |
|---|---|
| Tag multi-select | `tag` atoms, combined with `and` |
| Media type toggle (All / Photos / Videos) | `media_type` atom |
| Date taken — from / to | `taken_after` + `taken_before` atoms |
| Has location toggle | `has_location` atom |
| Received items toggle | `is_received` atom |

Composition: items in the builder are always AND-ed together at the top level. For E1
the UI does not expose OR or NOT directly — those arrive via the builder in E2/E3 when
flows need them. Advanced users can create NOT expressions via `plot_ref` to a hidden
plot whose criteria is a NOT expression (a power-user path; no special UI needed yet).

### Plot creation/editing

Update `PlotEditModal.jsx` (or equivalent) to use `CriteriaBuilder` instead of the
tag list. Show a `show_in_garden` toggle (default on). `visibility` is always `private`
in E1 (shared plots are E3).

### Garden

Hidden plots (`show_in_garden = false`) do not appear in the sidebar. No other Garden
changes needed.

### Explore: "Save as plot" fix

`handleSavePlot` in `ExplorePage.jsx` currently builds `{ name, tag_criteria }`.
Replace with: build a criteria expression from the current full filter state. Example:

```js
function filterStateToCriteria(filters) {
  const operands = [];
  filters.tags.forEach(tag => operands.push({ type: 'tag', tag }));
  if (filters.mediaType) operands.push({ type: 'media_type', value: filters.mediaType });
  if (filters.fromDate)  operands.push({ type: 'taken_after',  date: filters.fromDate });
  if (filters.toDate)    operands.push({ type: 'taken_before', date: filters.toDate });
  if (filters.hasLocation) operands.push({ type: 'has_location' });
  if (filters.isReceived)  operands.push({ type: 'is_received' });
  if (operands.length === 0) return null;
  if (operands.length === 1) return operands[0];
  return { type: 'and', operands };
}
```

---

## Tests

### Schema migration (~5)

1. V24 runs cleanly on a fresh database.
2. V24 runs cleanly on a database with existing plots and tag criteria — criteria migrated correctly.
3. `plot_tag_criteria` table no longer exists after V24.
4. `criteria`, `show_in_garden`, `visibility` columns exist on `plots`.
5. System plot has `criteria = '{"type":"just_arrived"}'` after V24.

### CriteriaEvaluator unit tests (~20)

For each atom type: evaluate → correct SQL fragment + parameters.
6. `tag` atom.
7. `media_type = image` and `media_type = video`.
8. `taken_after`, `taken_before`.
9. `uploaded_after`, `uploaded_before`.
10. `has_location`.
11. `device_make` (case-insensitive).
12. `is_received`, `received_from`.
13. `in_capsule`.
14. `just_arrived`.
15. `composted`.
16. `and` of two atoms.
17. `or` of two atoms.
18. `not` wrapping an atom.
19. Nested: `and(tag, not(tag))`.
20. `plot_ref` to a single-atom plot (inlines correctly).
21. `plot_ref` cycle (A → B → A) throws `CriteriaCycleException`.
22. `plot_ref` to non-existent plot → error (not silent empty).
23. `near` atom → 400 error (not implemented yet).
24. Unknown atom type → 400 error.
25. Empty `and` operands → 400 validation error.

### Plot CRUD integration tests (~10)

26. `POST /api/plots` with criteria JSON → 201, response includes criteria.
27. `GET /api/plots/:id` → returns criteria.
28. `PUT /api/plots/:id` with updated criteria → persisted correctly.
29. `GET /api/plots` → list includes criteria + show_in_garden + visibility.
30. Hidden plot (`show_in_garden: false`) — not returned in standard list (or returned with flag; UI filters it).
31. `GET /api/content/uploads?plot_id=:id` with tag criteria plot → returns matching uploads.
32. `GET /api/content/uploads?plot_id=:id` with media_type criteria → returns only images or only videos.
33. `GET /api/content/uploads?plot_id=:id` with `just_arrived` criteria → same results as existing just_arrived query.
34. `plot_ref` in criteria — resolves correctly in list query.
35. `POST /api/plots` with `near` atom → 400.

### Regression

36. All existing integration tests pass (upload CRUD, capsules, garden, auth isolation).

---

## What E1 does NOT include

- `near` atom (deferred — E2EE complication; earthdistance extension setup).
- Flows and staging (E2).
- Collection plots (`plot_items` table) — E2.
- Shared plots, plot_members (E3).
- Android (E4).
- OR / NOT in the Web criteria builder UI (power-user path via plot_ref only).

---

## Acceptance criteria

1. `./gradlew test` passes — all new tests green, no regressions.
2. V24 runs cleanly on both fresh and pre-existing databases.
3. `__just_arrived__` system plot loads and returns the same items as before.
4. A plot created with an `and(tag, not(tag), media_type)` criteria returns the correct uploads.
5. Explore "Save as plot" creates a plot with a full criteria expression, not just tags.
6. Hidden plots do not appear in the Garden sidebar on web.

---

## Documentation updates

- `docs/VERSIONS.md` — entry when E1 ships (v0.47.0)
- `docs/PROMPT_LOG.md` — standard entry
