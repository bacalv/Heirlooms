# Sending & Receiving — Behavioral Specification

_Derived from: `UploadRoutes.kt` (`shareUploadContractRoute`), `UploadService.kt` (`shareUpload`), `SharingKeyRoutes.kt`, `vaultCrypto.js` (`wrapDekForFriend`, `unwrapWithSharingKey`)_

---

## Use Case Inventory

- **User shares an item with a friend** — sender unwraps the item's DEK using their master key, re-wraps the DEK using the recipient's sharing pubkey (P-256 ECDH), and calls `POST /uploads/{id}/share`; server creates a recipient upload record linked to the original item.
- **Recipient views received items in Just Arrived** — recipient fetches uploads with `just_arrived=true` or `is_received=true`; received items have `shared_from_user_id` set; client decrypts by unwrapping the re-wrapped DEK with their own sharing private key.
- **Recipient views attribution** — upload record includes `shared_from_user_id` which the client resolves to a display name for attribution ("From: Alice").
- **User uploads their sharing key pair** — user calls `PUT /sharing` with their sharing pubkey (SPKI) and wrapped private key (wrapped under master key); this key pair is used for item and plot key distribution.
- **User retrieves own sharing key** — user calls `GET /sharing/me` to fetch their sharing keypair (e.g., on a new device, to restore sharing capability).
- **User retrieves a friend's sharing public key** — user calls `GET /sharing/{userId}` to get a friend's pubkey before wrapping a DEK or plot key for them; server enforces friendship.

---

## Sequence Diagrams

### 1. Share Item to Friend (DEK Re-wrap, Full Crypto Detail)

```mermaid
sequenceDiagram
    participant Sender as Sender (client)
    participant C as Crypto Layer
    participant S as Server

    Note over Sender,C: Step 1 — Fetch recipient's sharing pubkey
    Sender->>S: GET /sharing/{recipientUserId} [X-Api-Key]
    S-->>S: verify friendship(sender, recipient)
    alt Not friends
        S->>Sender: 403 "Not friends"
    else No sharing key
        S->>Sender: 404
    else Success
        S->>Sender: 200 {pubkey (b64 — SPKI bytes)}
    end

    Note over Sender,C: Step 2 — Unwrap item DEK with sender's master key
    Sender->>C: unwrapDekWithMasterKey(upload.wrappedDek, masterKey) → contentDek

    Note over Sender,C: Step 3 — Re-wrap DEK for recipient using their sharing pubkey
    Sender->>C: wrapDekForFriend(contentDek, recipientPubkeySpki)<br/>→ asymmetric envelope (p256-ecdh-hkdf-aes256gcm-v1)<br/>[ephemeral P-256 keygen → ECDH → HKDF-SHA256 → AES-256-GCM]

    Note over Sender,C: Step 4 — Re-wrap thumbnail DEK (if present)
    Sender->>C: unwrapDekWithMasterKey(upload.wrappedThumbnailDek) → thumbDek
    Sender->>C: wrapDekForFriend(thumbDek, recipientPubkeySpki) → wrappedThumbnailDek

    Note over Sender,S: Step 5 — Post share to server
    Sender->>S: POST /uploads/{uploadId}/share [X-Api-Key]<br/>{toUserId: recipientUserId,<br/> wrappedDek (b64 — asymmetric envelope),<br/> wrappedThumbnailDek? (b64),<br/> dekFormat: "p256-ecdh-hkdf-aes256gcm-v1",<br/> rotation? (int)}
    S-->>S: verify friendship(sender, recipient)<br/>verify upload owned by sender<br/>check recipient does not already have item
    alt Upload not found / not owned
        S->>Sender: 404
    else Not friends
        S->>Sender: 403 "Not friends"
    else Recipient already has item
        S->>Sender: 409 "Recipient already has this item"
    else Success
        S-->>S: INSERT INTO uploads (recipient's copy)<br/>  storage_key = same as sender's<br/>  wrapped_dek = re-wrapped DEK<br/>  dek_format = "p256-ecdh-hkdf-aes256gcm-v1"<br/>  shared_from_user_id = sender.userId<br/>  last_viewed_at = NULL (→ Just Arrived)
        S->>Sender: 201 {recipient upload record}
    end
```

### 2. Recipient Receives in Just Arrived

```mermaid
sequenceDiagram
    participant Recipient as Recipient (client)
    participant C as Crypto Layer
    participant S as Server

    Recipient->>S: GET /uploads?just_arrived=true [X-Api-Key]
    S-->>S: filter WHERE last_viewed_at IS NULL<br/>(includes received items with shared_from_user_id set)
    S->>Recipient: 200 {items: [..., {id, shared_from_user_id, wrappedDek, dekFormat: "p256-...", ...}]}

    Note over Recipient,C: Received items have dekFormat = "p256-ecdh-hkdf-aes256gcm-v1"
    Recipient->>C: unwrapWithSharingKey(wrappedDek, ownSharingPrivkey)<br/>→ contentDek (asymmetric envelope unwrap:<br/>   extract ephemeralPubkey → ECDH → HKDF → AES-GCM decrypt)

    Recipient->>S: GET /uploads/{id}/thumb [X-Api-Key]
    S->>Recipient: 200 encrypted thumbnail blob

    Recipient->>C: unwrapWithSharingKey(wrappedThumbnailDek, ownSharingPrivkey) → thumbDek
    Recipient->>C: decryptSymmetric(thumbBlob, thumbDek) → JPEG bytes
    Recipient-->>Recipient: display thumbnail

    Note over Recipient: User taps item to open detail
    Recipient->>S: GET /uploads/{id}/file [X-Api-Key]
    S->>Recipient: 200 encrypted content blob
    Recipient->>C: decryptStreamingContent(blob, contentDek) → plaintext
    Recipient-->>Recipient: display / play

    Recipient->>S: POST /uploads/{id}/view [X-Api-Key]
    S-->>S: SET last_viewed_at = NOW()
    S->>Recipient: 204 No Content
    Note over Recipient: Item no longer in Just Arrived
```

### 3. Attribution Display

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads?is_received=true [X-Api-Key]
    S-->>S: filter WHERE shared_from_user_id IS NOT NULL
    S->>App: 200 {items: [{..., shared_from_user_id: "uuid", ...}]}

    Note over App: Client resolves shared_from_user_id to display name
    App->>S: GET /me [X-Api-Key]  (or from cached friends list)
    App->>S: GET /friends [X-Api-Key]
    S->>App: 200 [{user_id, username, display_name, ...}]
    App-->>App: match shared_from_user_id → "From: Alice"
```

### 4. Upload and Retrieve Sharing Key Pair

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server

    Note over App,C: On first setup or new device — generate sharing key pair
    App->>C: generateKeyPair P-256 → (sharingPrivkey, sharingPubkeySpki)
    App->>C: wrapDekUnderMasterKey(sharingPrivkeyBytes, masterKey)<br/>→ wrappedPrivkey (master-aes256gcm-v1 envelope)

    App->>S: PUT /sharing [X-Api-Key]<br/>{pubkey (b64 SPKI bytes),<br/> wrappedPrivkey (b64),<br/> wrapFormat: "master-aes256gcm-v1"}
    S-->>S: upsert sharing_keys record for userId
    S->>App: 204 No Content

    Note over App,C: On another device — restore sharing key
    App->>S: GET /sharing/me [X-Api-Key]
    S->>App: 200 {pubkey (b64), wrappedPrivkey (b64), wrapFormat}
    App->>C: unwrapDekWithMasterKey(wrappedPrivkey, masterKey) → sharingPrivkeyBytes
    App->>C: importSharingPrivkey(sharingPrivkeyBytes) → CryptoKey
    Note over App,C: Sharing private key restored; can decrypt received items + plot keys
```
