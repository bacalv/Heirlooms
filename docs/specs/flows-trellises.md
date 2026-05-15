# Flows / Trellises — Behavioral Specification

_Derived from: `FlowRoutes.kt`, `FlowService.kt`, `PlotItemRepository` (inferred from service)_

> **Note on naming:** The codebase uses "Flow" throughout. REF-001 (Flow → Trellis rename) is queued but not yet applied. This spec uses "Flow/Trellis" to indicate the pending rename. All HTTP endpoints currently use `/flows` and `/plots/{id}/staging`.

---

## Use Case Inventory

- **User creates a Flow/Trellis** — user calls `POST /flows` with a name, criteria expression, target plot ID, and `requiresStaging` flag; criteria is validated; target plot must be owned by user.
- **User updates a Flow/Trellis** — user calls `PUT /flows/{id}` to change name, criteria, or `requiresStaging`.
- **User deletes a Flow/Trellis** — user calls `DELETE /flows/{id}`; returns 204 or 404.
- **Auto-routing trigger** — when an upload is confirmed, the server evaluates all active flows for the user; uploads matching a flow's criteria are placed in the staging queue or immediately added to the target plot (if `requiresStaging=false`).
- **User views flow staging queue** — user calls `GET /flows/{id}/staging` to see pending items for a specific flow; or `GET /plots/{id}/staging` to see all pending items for a plot.
- **User approves a staging item** — user calls `POST /plots/{id}/staging/{uploadId}/approve`; for shared plots, client must supply re-wrapped DEK under plot key; item moves from staging to collection.
- **User rejects a staging item** — user calls `POST /plots/{id}/staging/{uploadId}/reject`; item is excluded from this staging pass; can be un-rejected.
- **User un-rejects a staging decision** — user calls `DELETE /plots/{id}/staging/{uploadId}/decision`; removes the rejection record; item re-enters staging queue.
- **User views rejected items** — user calls `GET /plots/{id}/staging/rejected` to review previously rejected items.
- **User manually adds item to collection** — user calls `POST /plots/{id}/items` with `uploadId`; for shared plots, must include re-wrapped DEK.
- **User removes item from collection** — user calls `DELETE /plots/{id}/items/{uploadId}`.
- **`requiresStaging=false` bypass** — flow is configured without staging requirement; matching uploads are automatically added to the target plot collection directly, with no staging step.

---

## Sequence Diagrams

### 1. Create Flow/Trellis

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /flows [X-Api-Key]<br/>{name: "Holidays",<br/> criteria: {type:"tag", tag:"holiday"},<br/> targetPlotId: "uuid",<br/> requiresStaging: true}
    S-->>S: validateAndSerializeCriteria(criteria, userId)
    S-->>S: verify targetPlotId owned by userId
    alt Invalid criteria or plot not owned
        S->>App: 400 error message
    else Valid
        S-->>S: INSERT INTO flows (name, criteria_json, target_plot_id, requires_staging)
        S->>App: 201 {flow record}
    end
```

### 2. Auto-Routing Trigger (Upload Confirmed)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server
    participant FlowEval as Flow Evaluator (server-side)

    App->>S: POST /uploads/confirm [X-Api-Key] {all fields}
    S-->>S: confirmEncryptedUpload(...)
    S-->>FlowEval: launchCompostCleanup + flow routing (async)
    Note over FlowEval: For each active flow owned by userId:
    FlowEval-->>FlowEval: evaluate flow.criteria against new upload
    alt Upload matches criteria AND requiresStaging = true
        FlowEval-->>FlowEval: INSERT INTO plot_staging_items<br/>(plot_id, upload_id, source_flow_id, state="pending")
    else Upload matches AND requiresStaging = false
        FlowEval-->>FlowEval: INSERT INTO plot_items (direct add, no staging step)
    end
    S->>App: 201 Created
    Note over App: Staging items appear in GET /flows/{id}/staging on next poll
```

### 3. View Staging Queue

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /flows/{flowId}/staging [X-Api-Key]
    S-->>S: load flow<br/> verify ownership<br/> query staging items for this flow
    S->>App: 200 [{upload records in pending staging state}]

    App->>S: GET /plots/{plotId}/staging [X-Api-Key]
    S-->>S: find all flows targeting this plot with requiresStaging=true<br/>aggregate pending staging items across all those flows
    S->>App: 200 [{upload records}]
```

### 4. Approve Staging Item (Private Plot)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /plots/{plotId}/staging/{uploadId}/approve [X-Api-Key]<br/>{sourceFlowId: "uuid"}
    S-->>S: verify plot ownership (PlotNotOwned -> 404)
    S-->>S: verify plot not closed (PlotClosed -> 403)
    S-->>S: check item in pending state (NotFound -> 404)
    S-->>S: check no duplicate content (DuplicateContent -> 204 silently)
    alt Already approved
        S->>App: 409 "Item is already in the collection"
    else Success
        S-->>S: UPDATE staging_item state="approved"<br/>INSERT INTO plot_items (plot_id, upload_id)
        S->>App: 204 No Content
    end
```

### 5. Approve Staging Item (Shared Plot — DEK Re-wrap)

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server

    Note over App,C: User fetches plot key and re-wraps item DEK
    App->>S: GET /plots/{plotId}/plot-key [X-Api-Key]
    S->>App: 200 {wrappedPlotKey (b64), format}

    App->>C: unwrapPlotKey(wrappedPlotKey, ownSharingPrivkey) -> plotKey
    App->>C: unwrapDekWithMasterKey(upload.wrappedDek) -> contentDek
    App->>C: wrapDekWithPlotKey(contentDek, plotKey)<br/>-> {wrappedItemDek, format: "plot-aes256gcm-v1"}
    App->>C: unwrapDekWithMasterKey(upload.wrappedThumbnailDek) -> thumbDek
    App->>C: wrapDekWithPlotKey(thumbDek, plotKey) -> {wrappedThumbnailDek, format}

    App->>S: POST /plots/{plotId}/staging/{uploadId}/approve [X-Api-Key]<br/>{sourceFlowId, wrappedItemDek (b64),<br/> itemDekFormat: "plot-aes256gcm-v1",<br/> wrappedThumbnailDek (b64), thumbnailDekFormat}
    S-->>S: decode wrappedItemDek<br/> store alongside plot_items record
    S-->>S: INSERT INTO plot_items (plot_id, upload_id, wrapped_dek, dek_format, ...)
    S->>App: 204 No Content
```

### 6. Reject Staging Item

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /plots/{plotId}/staging/{uploadId}/reject [X-Api-Key]<br/>{sourceFlowId: "uuid"}
    S-->>S: verify plot ownership
    alt Already approved
        S->>App: 409 "Item is already approved - remove it from the collection first"
    else Success
        S-->>S: INSERT/UPDATE staging decision state="rejected"
        S->>App: 204 No Content
    end

    Note over App: Item no longer appears in pending staging queue
```

### 7. Un-Reject (Delete Decision)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: DELETE /plots/{plotId}/staging/{uploadId}/decision [X-Api-Key]
    S-->>S: verify plot ownership<br/> delete decision record
    alt Found and deleted
        S->>App: 204 No Content
    else Not found
        S->>App: 404
    end
    Note over App: Item reappears in staging queue on next load
```

### 8. Manual Add / Remove Plot Item

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /plots/{plotId}/items [X-Api-Key]<br/>{uploadId: "uuid"}<br/>or for shared plot:<br/>{uploadId, wrappedItemDek (b64), itemDekFormat,<br/> wrappedThumbnailDek? (b64), thumbnailDekFormat?}
    S-->>S: verify plot ownership or membership<br/>verify upload owned by userId<br/>for shared: decode wrappedItemDek<br/> store with record
    alt Already present
        S->>App: 409 "Item already in collection"
    else Plot closed
        S->>App: 403 "Plot is closed"
    else Success
        S-->>S: INSERT INTO plot_items
        S->>App: 201 Created
    end

    App->>S: DELETE /plots/{plotId}/items/{uploadId} [X-Api-Key]
    S-->>S: verify ownership/membership<br/> delete plot_items row
    S->>App: 204 No Content
```

### 9. `requiresStaging=false` Direct Add Bypass

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /flows [X-Api-Key]<br/>{name: "Auto-Collect All",<br/> criteria: {type:"tag", tag:"auto"},<br/> targetPlotId: "uuid",<br/> requiresStaging: false}
    S->>App: 201 {flow with requires_staging=false}

    Note over App,S: On next upload confirm matching this criteria:
    App->>S: POST /uploads/confirm [X-Api-Key] {tags:["auto"], ...}
    S-->>S: flow routing: match found, requiresStaging=false
    S-->>S: INSERT INTO plot_items directly (no staging step)
    S->>App: 201 Created
    Note over App: Item is immediately in collection<br/> no staging approval needed
```
