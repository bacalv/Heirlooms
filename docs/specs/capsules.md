# Capsules — Behavioral Specification

_Derived from: `CapsuleRoutes.kt`, `CapsuleService.kt`_

> **Scope note:** Capsule **delivery** (M12) and **posthumous unlock** (M13) are out of scope for this spec. Those milestones are not yet implemented in the codebase. This document covers creation, editing, sealing, viewing, and cancellation of capsules — all of which are currently implemented.

---

## Use Case Inventory

- **User creates a capsule** — user calls `POST /capsules` with a `shape` ("open" or "sealed"), `unlock_at` (ISO-8601 timestamp with timezone), one or more `recipients` (usernames), optional `upload_ids`, and an optional `message` (max 50,000 bytes); server validates and returns the capsule in "open" or "sealed" state.
- **User lists capsules** — user calls `GET /capsules` with optional `state` filter (open, sealed, delivered, cancelled) and `order` (updated_at or unlock_at).
- **User views capsule detail** — user calls `GET /capsules/{id}` to get full capsule including all uploads, recipients, and current message.
- **User edits an open capsule** — user calls `PATCH /capsules/{id}` to update `unlock_at`, `recipients`, `upload_ids`, or `message`; not allowed once in a terminal state; upload_ids cannot be changed on sealed capsules.
- **User seals an open capsule** — user calls `POST /capsules/{id}/seal`; capsule must be in "open" state and contain at least one upload; moves to "sealed" state (contents become immutable).
- **User cancels a capsule** — user calls `POST /capsules/{id}/cancel`; allowed from "open" or "sealed" states; moves to "cancelled" (terminal state).
- **User views upload's capsule memberships** — user calls `GET /uploads/{id}/capsules` to see which active (open + sealed) capsules contain a given upload; used to block composting of in-capsule uploads.
- **Capsule delivery** — _out of scope (M12)_: delivery mechanism, unlock notification, and recipient viewing flow.
- **Posthumous unlock** — _out of scope (M13)_: posthumous trigger and executor unlock flow.

---

## Capsule States

```
open  →  sealed  →  [delivered — M12, out of scope]
 ↓          ↓
cancelled  cancelled
```

| State | Description |
|-------|-------------|
| `open` | Capsule created with shape="open"; contents mutable |
| `sealed` | Sealed by user; contents immutable; unlock_at mutable |
| `delivered` | Unlock date reached, delivered to recipients (M12 — not implemented) |
| `cancelled` | Cancelled by owner; terminal state |

---

## Sequence Diagrams

### 1. Create Capsule (Open Shape)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /capsules [X-Api-Key]<br/>{shape: "open",<br/> unlock_at: "2027-01-01T00:00:00+00:00",<br/> recipients: ["alice", "bob"],<br/> upload_ids: ["uuid1", "uuid2"],<br/> message: "To be opened on our 10th anniversary."}
    S-->>S: validate shape ("open" or "sealed")<br/>validate unlock_at (ISO-8601 with tz)<br/>validate recipients non-empty<br/>validate upload_ids belong to user
    alt Invalid shape
        S->>App: 400 "shape must be 'open' or 'sealed'"
    else Invalid unlock_at
        S->>App: 400 "unlock_at must be a valid ISO-8601 timestamp with timezone"
    else Recipients empty / invalid
        S->>App: 422 {error: "recipients is required and must be non-empty"}
    else Unknown upload_id
        S->>App: 400 {error: "unknown upload_id", id: "..."}
    else Valid
        S-->>S: INSERT INTO capsules (state="open", ...)<br/>INSERT INTO capsule_contents (upload_ids)<br/>INSERT INTO capsule_recipients
        S->>App: 201 {capsule detail}
    end
```

### 2. Create Capsule (Sealed Shape — Immediate Seal)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /capsules [X-Api-Key]<br/>{shape: "sealed",<br/> unlock_at: "2030-06-15T00:00:00+00:00",<br/> recipients: ["charlie"],<br/> upload_ids: ["uuid1"]}
    S-->>S: shape="sealed" -> require at least one upload
    S-->>S: INSERT capsule with state="sealed" directly
    S->>App: 201 {capsule detail with state: "sealed"}
    Note over App: Contents are immediately immutable
```

### 3. Edit Open Capsule

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: PATCH /capsules/{id} [X-Api-Key]<br/>{message: "Updated message.",<br/> unlock_at: "2028-01-01T00:00:00+00:00"}
    S-->>S: load capsule<br/> verify ownership
    alt Terminal state (delivered / cancelled)
        S->>App: 409 {error: "capsule is in a terminal state and cannot be modified"}
    else Sealed + trying to change upload_ids
        S->>App: 409 {error: "cannot edit upload contents of a sealed capsule"}
    else Message too long (> 50,000 bytes)
        S->>App: 422 {error: "message exceeds maximum size of 50000 bytes"}
    else Valid
        S-->>S: UPDATE capsules (unlock_at, message, ...)<br/>update capsule_contents / recipients if provided
        S->>App: 200 {updated capsule detail}
    end
```

### 4. Seal an Open Capsule

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /capsules/{id}/seal [X-Api-Key]
    S-->>S: load capsule<br/> verify ownership
    alt Not in "open" state
        S->>App: 409 {error: "capsule cannot be sealed in its current state"}
    else No uploads
        S->>App: 422 {error: "Cannot seal an empty capsule"}
    else Success
        S-->>S: UPDATE capsules SET state = "sealed"
        S->>App: 200 {capsule detail with state: "sealed"}
    end
    Note over App: Contents are now immutable<br/> unlock_at still editable via PATCH
```

### 5. Cancel a Capsule

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /capsules/{id}/cancel [X-Api-Key]
    S-->>S: load capsule<br/> verify ownership
    alt Already terminal (delivered / cancelled)
        S->>App: 409 {error: "capsule is already in a terminal state"}
    else Not found
        S->>App: 404
    else Success (from open or sealed)
        S-->>S: UPDATE capsules SET state = "cancelled"
        S->>App: 200 {capsule detail with state: "cancelled"}
    end
```

### 6. List and View Capsules

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /capsules?state=open,sealed&order=unlock_at [X-Api-Key]
    S-->>S: filter by state list<br/> order by unlock_at
    S->>App: 200 {capsules: [{capsule summaries}]}

    App->>S: GET /capsules/{id} [X-Api-Key]
    S-->>S: verify ownership
    alt Not found / not owned
        S->>App: 404
    else Success
        S->>App: 200 {capsule detail including uploads, recipients, message, state}
    end
```

### 7. Upload Capsule Reverse Lookup (Compost Eligibility)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads/{uploadId}/capsules [X-Api-Key]
    S-->>S: find active capsules (state in "open","sealed") containing upload
    alt Upload not found
        S->>App: 404
    else Success
        S->>App: 200 {capsules: [{id, state, unlock_at}]}
    end
    Note over App: If non-empty, compost is blocked for this upload<br/>(POST /uploads/{id}/compost returns 422)
```

### 8. Capsule Delivery — STUB (M12, Out of Scope)

```mermaid
sequenceDiagram
    participant S as Server
    participant Recipient as Recipient (client)

    Note over S,Recipient: M12 - not implemented<br/>Trigger: scheduled job checks unlock_at <= NOW() for sealed capsules<br/>Action: update state to "delivered"<br/> notify recipients<br/>Recipient view: GET /capsules/{id} returns full contents
```

### 9. Posthumous Unlock — STUB (M13, Out of Scope)

```mermaid
sequenceDiagram
    participant S as Server
    participant Executor as Designated Executor

    Note over S,Executor: M13 - not implemented<br/>Mechanism TBD (executor key, dead-man switch, or notary)<br/>Outcome: designated executor can unlock capsule on behalf of deceased user
```
