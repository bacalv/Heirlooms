# Plots — Behavioral Specification

_Derived from: `PlotRoutes.kt`, `PlotService.kt`, `CriteriaEvaluator.kt`_

---

## Use Case Inventory

- **User lists plots** — authenticated user calls `GET /plots`; returns all plots owned by the user ordered by `sort_order`; system-defined plots are included.
- **User creates a private plot** — user calls `POST /plots` with a name and optional criteria JSON; visibility defaults to `"private"`; criteria atoms are validated server-side.
- **User creates a shared plot** — user calls `POST /plots` with `visibility: "shared"` and provides `wrappedPlotKey` (plot key wrapped to owner's sharing pubkey) and `plotKeyFormat`; creates the plot and member record for owner.
- **User edits a plot** — user calls `PUT /plots/{id}` to update name, sort_order, showInGarden, and/or criteria; system-defined plots return 403.
- **User deletes a private plot** — user calls `DELETE /plots/{id}`; system-defined plots return 403.
- **User reorders plots** — user calls `PATCH /plots` with an array of `{id, sort_order}` objects; batch update is atomic.
- **User hides a plot from Garden** — user calls `PUT /plots/{id}` with `show_in_garden: false`; plot still appears in sidebar but not in garden layout.
- **User builds a criteria expression** — user combines atom types using `and`/`or`/`not` logical operators; server validates each atom type and detects cycles in `plot_ref` atoms (max depth 10).

---

## Criteria Atom Types

The following atom types are validated by `CriteriaEvaluator.kt`:

| Type | Required Fields | Description |
|------|----------------|-------------|
| `tag` | `tag` (string) | Items tagged with a specific tag |
| `media_type` | `value` ("image" or "video") | Filter by MIME type prefix |
| `taken_after` | `date` (ISO date) | `taken_at >= date` |
| `taken_before` | `date` (ISO date) | `taken_at < date + 1 day` |
| `uploaded_after` | `date` (ISO date) | `uploaded_at >= date` |
| `uploaded_before` | `date` (ISO date) | `uploaded_at < date + 1 day` |
| `has_location` | — | GPS coordinates present |
| `device_make` | `value` (string, ILIKE) | Camera make (legacy plaintext only) |
| `device_model` | `value` (string, ILIKE) | Camera model (legacy plaintext only) |
| `is_received` | — | `shared_from_user_id IS NOT NULL` |
| `received_from` | `user_id` (UUID) | Received from specific friend |
| `in_capsule` | — | Contained in an active capsule |
| `plot_ref` | `plot_id` (UUID) | Items that match another plot's criteria |
| `and` | `children` (array) | Logical AND |
| `or` | `children` (array) | Logical OR |
| `not` | `child` (node) | Logical NOT |

---

## Sequence Diagrams

### 1. List Plots

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /plots [X-Api-Key]
    S-->>S: SELECT * FROM plots WHERE owner_user_id = userId<br/>ORDER BY sort_order ASC
    S->>App: 200 [{plot records including criteria JSON, visibility, showInGarden, ...}]
    Note over App: System plots (Just Arrived, Compost, etc.) are included;<br/>they cannot be edited or deleted
```

### 2. Create Private Plot with Criteria

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /plots [X-Api-Key]<br/>{name: "Summer 2024",<br/> criteria: {type:"and", children:[<br/>   {type:"taken_after", date:"2024-06-01"},<br/>   {type:"taken_before", date:"2024-08-31"}]},<br/> show_in_garden: true,<br/> visibility: "private"}
    S-->>S: validateAndSerializeCriteria(node, userId)<br/>→ checks atom types, recursion depth ≤ 10<br/>→ resolves plot_ref atoms (cycle detection)
    alt Criteria invalid
        S->>App: 400 "Invalid criteria: ..."
    else Valid
        S-->>S: INSERT INTO plots (name, criteria, sort_order, visibility)
        S->>App: 201 {plot record}
    end
```

### 3. Create Shared Plot (with E2EE plot key)

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server

    App->>C: generatePlotKey() → 32 random bytes (plotKey)
    App->>C: wrapPlotKeyForMember(plotKey, ownSharingPubkey)<br/>→ {wrappedKey, format: "p256-ecdh-hkdf-aes256gcm-v1"}

    App->>S: POST /plots [X-Api-Key]<br/>{name: "Family Album",<br/> visibility: "shared",<br/> wrappedPlotKey (b64),<br/> plotKeyFormat: "p256-ecdh-hkdf-aes256gcm-v1",<br/> show_in_garden: true}
    S-->>S: validate wrappedPlotKey + plotKeyFormat required for shared
    S-->>S: createPlot(visibility="shared")<br/>createMemberRecord(owner, wrappedPlotKey, plotKeyFormat)
    S->>App: 201 {plot record}
```

### 4. Edit Plot

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: PUT /plots/{id} [X-Api-Key]<br/>{name: "New Name", show_in_garden: false,<br/> criteria: {type: "tag", tag: "vacation"}}
    S-->>S: load plot; check ownership
    alt System-defined plot
        S->>App: 403 {error: "Cannot modify a system-defined plot"}
    else User-defined
        S-->>S: validateAndSerializeCriteria if criteria provided
        S-->>S: UPDATE plots SET name, show_in_garden, criteria
        S->>App: 200 {updated plot record}
    end
```

### 5. Delete Plot

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: DELETE /plots/{id} [X-Api-Key]
    S-->>S: load plot; check ownership
    alt System-defined
        S->>App: 403 {error: "Cannot delete a system-defined plot"}
    else Not found
        S->>App: 404
    else User-defined
        S-->>S: DELETE FROM plots WHERE id = plotId AND owner_user_id = userId
        S->>App: 204 No Content
    end
```

### 6. Batch Reorder Plots

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: PATCH /plots [X-Api-Key]<br/>[{id: "uuid1", sort_order: 1},<br/> {id: "uuid2", sort_order: 2}]
    S-->>S: validate all IDs are user-owned, non-system
    alt Any system-defined plot in list
        S->>App: 403 {error: "Cannot reorder system-defined plots"}
    else All valid
        S-->>S: batch UPDATE sort_order atomically
        S->>App: 204 No Content
    end
```

### 7. Plot Reference Cycle Detection

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    Note over App: User attempts to create a plot that references another plot<br/>which already references back (cycle)

    App->>S: POST /plots [X-Api-Key]<br/>{criteria: {type:"plot_ref", plot_id:"uuid-A"}}
    S-->>S: evalNode(plot_ref) → load plot A's criteria<br/>recurse into plot A → encounters plot being created<br/>or exceeds depth=10
    S->>App: 400 "Circular plot_ref detected"<br/>or "Criteria expression exceeds maximum nesting depth of 10"
```
