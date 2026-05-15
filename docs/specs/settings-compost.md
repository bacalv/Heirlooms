# Settings & Compost — Behavioral Specification

_Derived from: `KeysRoutes.kt`, `KeyService.kt`, `UploadRoutes.kt`, `UploadService.kt`, `AuthRoutes.kt`_

---

## Use Case Inventory

- **User views account info** — user calls `GET /me` to see their user ID, username, and display name.
- **User changes passphrase** — user generates a new auth salt + verifier, re-wraps master key with new passphrase (Argon2id), and calls `PUT /passphrase` to store the new recovery blob; optionally updates auth credentials server-side.
- **User sets up recovery passphrase** — user calls `PUT /passphrase` with passphrase-wrapped master key blob, Argon2id params, and salt; overwrites existing recovery blob (upsert).
- **User retrieves recovery passphrase record** — user calls `GET /passphrase` to check if a recovery blob exists.
- **User deletes recovery passphrase** — user calls `DELETE /passphrase` to remove the backup.
- **User lists devices** — user calls `GET /devices` to see all active (non-retired) device registrations.
- **User retires a device** — user calls `DELETE /devices/{deviceId}` to soft-retire a device (sets `retired_at`); retired devices cannot authenticate.
- **User composts an upload** — user calls `POST /uploads/{id}/compost`; requires that the upload has no tags and is not in any active capsule; soft-deletes by setting `composted_at`; 90-day cleanup window applies.
- **User views composted uploads** — user calls `GET /uploads/composted` for a cursor-paginated list of composted items.
- **User restores a composted upload** — user calls `POST /uploads/{id}/restore` to un-compost and return the item to the active garden.
- **User logs out** — user calls `POST /logout` to invalidate the current session token.

---

## Sequence Diagrams

### 1. View Account Info

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /me [X-Api-Key]
    S-->>S: resolve session -> userId
    S->>App: 200 {user_id, username, display_name}
```

### 2. Change Passphrase (Argon2id Re-wrap)

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server

    Note over App,C: User enters old passphrase -> verify -> enter new passphrase
    App->>C: argon2id(newPassphrase, newSalt, {m:65536, t:3, p:1}) -> kek (32 bytes)
    App->>C: encryptSymmetric("argon2id-aes256gcm-v1", kek, masterKey)<br/>-> wrappedMasterKey (symmetric envelope)

    App->>S: PUT /passphrase [X-Api-Key]<br/>{wrappedMasterKey (b64),<br/> wrapFormat: "argon2id-aes256gcm-v1",<br/> argon2Params: {m: 65536, t: 3, p: 1},<br/> salt (b64 - 16 random bytes)}
    S-->>S: validate fields<br/> upsert recovery_passphrase row
    alt Missing required fields
        S->>App: 400 "Missing required fields"
    else Valid
        S->>App: 200 {passphrase record}
    end

    Note over App,C: Optionally also update auth_verifier (login credentials)
    App->>C: newAuthSalt = generateSalt(16)
    App->>C: authKey = PBKDF(newPassphrase, newAuthSalt)
    App->>C: authVerifier = SHA-256(authKey)
    Note over App,S: No dedicated "change auth" endpoint exists in current code.<br/>Auth credentials are set via /setup-existing (first-time only).<br/>Future passphrase change flow may require additional endpoint.
```

### 3. Get / Delete Recovery Passphrase

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /passphrase [X-Api-Key]
    alt No recovery backup exists
        S->>App: 404
    else Exists
        S->>App: 200 {passphrase record with wrapFormat, argon2Params, salt}
    end

    App->>S: DELETE /passphrase [X-Api-Key]
    alt Not found
        S->>App: 404
    else Deleted
        S->>App: 204 No Content
    end
```

### 4. List and Retire Devices

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /devices [X-Api-Key]
    S-->>S: SELECT * FROM wrapped_keys WHERE user_id = userId AND retired_at IS NULL
    S->>App: 200 [{device records: deviceId, label, kind, createdAt, lastUsedAt, ...}]

    App->>S: DELETE /devices/{deviceId} [X-Api-Key]
    S-->>S: look up device for user
    alt Not found
        S->>App: 404
    else Already retired
        S->>App: 409 {error: "device is already retired"}
    else Success
        S-->>S: UPDATE wrapped_keys SET retired_at = NOW()
        S->>App: 204 No Content
    end
    Note over App: Retired device's session tokens remain valid until expiry<br/>but it can no longer authenticate after that
```

### 5. Compost an Upload (Eligibility Check)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /uploads/{id}/compost [X-Api-Key]
    S-->>S: load upload<br/> verify ownership
    alt Upload not found
        S->>App: 404
    else Already composted
        S->>App: 409 {error: "Upload is already composted"}
    else Has tags OR in active capsule
        S->>App: 422 {error: "Cannot compost: upload has tags or is in active capsules"}
    else Eligible
        S-->>S: UPDATE uploads SET composted_at = NOW()
        S->>App: 200 {upload record with composted_at set}
    end

    Note over App,S: Composted uploads are soft-deleted.<br/>A background cleanup job permanently deletes blobs<br/>after a 90-day window (PendingBlobsCleanupService).
```

### 6. View and Restore Composted Uploads

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads/composted?limit=50 [X-Api-Key]
    S-->>S: SELECT * FROM uploads WHERE user_id = userId AND composted_at IS NOT NULL<br/>ORDER BY composted_at DESC<br/> cursor-paginate
    S->>App: 200 {items: [...composted upload records], nextCursor}

    App->>S: POST /uploads/{id}/restore [X-Api-Key]
    S-->>S: verify upload is composted and owned by user
    alt Not composted
        S->>App: 409 {error: "Upload is not composted"}
    else Not found
        S->>App: 404
    else Success
        S-->>S: UPDATE uploads SET composted_at = NULL
        S->>App: 200 {restored upload record}
    end
    Note over App: Restored item appears in Garden on next load
```

### 7. Set Tags (Prerequisite for Compost Eligibility)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    Note over App: User must remove all tags before composting

    App->>S: PATCH /uploads/{id}/tags [X-Api-Key]<br/>{tags: []}
    S-->>S: validateTags([]) -> valid
    S-->>S: UPDATE uploads SET tags = '{}'
    S->>App: 200 {updated upload record with tags: []}

    Note over App: Now compost eligibility check will pass (no tags, no active capsules)
```

### 8. Logout

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: POST /logout [X-Api-Key]
    S-->>S: resolve session from X-Api-Key header
    alt Session not found / invalid
        S->>App: 401 Unauthorized
    else Valid session
        S-->>S: DELETE FROM sessions WHERE id = sessionId
        S->>App: 204 No Content
    end
    Note over App: Client clears master key from memory and local storage
```
