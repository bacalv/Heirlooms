# Heirlooms API Client — Kotlin CLI

A standalone Kotlin command-line tool that exercises the full capsule lifecycle
against the Heirlooms API. Runs without the Android or web app.

## Purpose

1. **Proving ground for the M11/M12 API** — exercises create, upload, seal,
   list, and retrieve without depending on any app client.
2. **Patent evidence artefact** — a runnable demonstration that the capsule
   construction is implementable end-to-end. Phase 2 will add tlock + Shamir.
3. **Integration test harness** — can be pointed at test or production and run
   a full capsule round-trip programmatically.

## Phase 1 scope

The following steps are executed in sequence:

| Step | Endpoint |
|------|----------|
| 1. Authenticate | `POST /api/auth/challenge` then `POST /api/auth/login` |
| 2. Upload a file | `POST /api/content/uploads/initiate`, PUT to GCS, `POST /api/content/uploads/confirm` |
| 3. Create a capsule | `POST /api/capsules` |
| 4. Add upload to capsule | `PATCH /api/capsules/{id}` |
| 5. Seal the capsule | `POST /api/capsules/{id}/seal` |
| 6. List capsules | `GET /api/capsules` |
| 7. Retrieve the capsule | `GET /api/capsules/{id}` |

No client-side E2EE in Phase 1 — the upload uses `storage_class: "public"`.
Phase 2 will add tlock IBE lower-bound and Shamir deletion upper-bound via the
M11 `PUT /api/capsules/{id}/seal` endpoint.

## Prerequisites

- JDK 21
- A registered Heirlooms account on the target environment
- The account's `auth_key` (32-byte hex string)

## Configuration

Configuration is loaded from (in priority order):

1. **JVM system properties** (`-Dheirlooms.username=foo`)
2. **Environment variables** (`HEIRLOOMS_USERNAME=foo`)
3. **`config.properties`** file in the working directory

### Required properties

| Property | Env var | Description |
|----------|---------|-------------|
| `heirlooms.username` | `HEIRLOOMS_USERNAME` | Your account username |
| `heirlooms.auth_key` | `HEIRLOOMS_AUTH_KEY` | 64-char lowercase hex (32 raw bytes) |

### Optional properties

| Property | Env var | Default |
|----------|---------|---------|
| `heirlooms.base_url` | `HEIRLOOMS_BASE_URL` | `https://test.api.heirlooms.digital` |

### What is `auth_key`?

The Heirlooms server stores `auth_verifier = SHA-256(auth_key)`.
At login, the client sends the raw `auth_key` bytes (base64url-encoded);
the server hashes them and compares against the stored verifier.

To create a test account and derive the correct `auth_key`:

```python
import os, hashlib, base64, json, urllib.request

base_url = "https://test.api.heirlooms.digital"
api_key  = "<founding-api-key>"

auth_key_bytes  = os.urandom(32)
auth_key_hex    = auth_key_bytes.hex()          # store this in config.properties
auth_verifier   = hashlib.sha256(auth_key_bytes).digest()
auth_salt       = os.urandom(16)

def b64url(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def b64std(b): return base64.b64encode(b).decode()

# 1. Get invite
req = urllib.request.Request(f"{base_url}/api/auth/invites",
                             headers={"X-Api-Key": api_key})
invite_token = json.loads(urllib.request.urlopen(req).read())["token"]

# 2. Register
body = json.dumps({
    "invite_token": invite_token,
    "username": "my_cli_user",
    "display_name": "CLI User",
    "auth_salt": b64url(auth_salt),
    "auth_verifier": b64url(auth_verifier),
    "wrapped_master_key": b64std(b"\x00" * 64),
    "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
    "pubkey_format": "p256-spki",
    "pubkey": b64std(b"\x00" * 65),
    "device_id": "cli-tool-001",
    "device_label": "CLI Tool",
    "device_kind": "android",
}).encode()
req = urllib.request.Request(f"{base_url}/api/auth/register",
                             data=body,
                             headers={"Content-Type": "application/json"},
                             method="POST")
urllib.request.urlopen(req)
print(f"auth_key_hex = {auth_key_hex}")
```

Then put `auth_key_hex` into your `config.properties`.

## Setup

```bash
cd tools/api-client
cp config.properties.template config.properties
# Edit config.properties with your credentials
```

## Running

### Via Gradle run

```bash
cd tools/api-client
./gradlew run --no-daemon
```

Or with credentials as system properties (no config file needed):

```bash
./gradlew run --no-daemon \
  -Dheirlooms.username=myuser \
  -Dheirlooms.auth_key=<64-hex-chars> \
  -Dheirlooms.base_url=https://test.api.heirlooms.digital
```

### Via the shadow JAR (after `./gradlew build`)

```bash
./gradlew build --no-daemon
java -jar build/libs/api-client-0.1.0.jar \
  -Dheirlooms.username=myuser \
  -Dheirlooms.auth_key=<64-hex-chars>
```

### Against production

```bash
./gradlew run --no-daemon \
  -Dheirlooms.base_url=https://api.heirlooms.digital \
  -Dheirlooms.username=myuser \
  -Dheirlooms.auth_key=<64-hex-chars>
```

## Running tests

```bash
./gradlew test --no-daemon
```

The unit tests cover `ClientConfig` loading and hex decoding, and the
auth-key/auth-verifier contract.

## Expected output

A successful run prints each step to stdout and exits 0:

```
============================================================
[api-client] Heirlooms API Client — Phase 1 Capsule Lifecycle
[api-client] base_url=https://test.api.heirlooms.digital
[api-client] username=myuser
============================================================

[api-client] --- Step 1: Authenticate ---
[api-client] [authenticate] username=myuser base_url=https://test.api.heirlooms.digital
[api-client] [authenticate] challenge OK — auth_salt=abcd1234…
[api-client] [authenticate] login OK — user_id=... session_token=abcd…

[api-client] --- Step 2: Upload a file ---
...

[api-client] --- Step 7: Retrieve the capsule ---
[api-client] [getCapsule] capsule_id=... state=sealed uploads=1

============================================================
[api-client] SUCCESS — full capsule lifecycle round-trip complete
[api-client] capsule_id=<uuid>  state=sealed  uploads=1
============================================================
```

Exit code 0 = success. Exit code 1 = failure (error printed to stderr).

## Code structure

```
src/main/kotlin/digital/heirlooms/tools/apiclient/
  Main.kt            — Entry point; orchestrates the 7-step lifecycle
  HeirloomsClient.kt — HTTP client class; one method per lifecycle step
  ClientConfig.kt    — Configuration loading (system props / env / file)
```

### Extending for Phase 2

Phase 2 (tlock + Shamir + client-side E2EE) will:

1. Override `sealCapsule()` to call `PUT /api/capsules/{id}/seal` with a
   `recipient_keys[]` array, a `tlock` block, and a `shamir` block.
2. Inject a crypto delegate (e.g. `TlockProvider`) into `HeirloomsClient`.
3. Add a `retrieveAndDecrypt()` method that calls `GET /api/capsules/{id}/tlock-key`
   post-unlock and reconstructs the DEK.

No structural changes to `ClientConfig` or the authentication/upload steps are needed.
