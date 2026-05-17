#!/usr/bin/env python3
"""
Sharing flow smoke test — runs against a live Heirlooms API server.

Usage:
    python3 scripts/smoke_sharing_flow.py

Env vars (all optional):
    BASE_URL   API base URL  (default: https://test.api.heirlooms.digital)
    API_KEY    Static API key (default: fetched from GCP Secret Manager)

The test registers two disposable users, connects them as friends, uploads a
file as User A, shares it to User B, and asserts byte-exact retrieval.
Negative cases verify that ownership and friendship gates hold.
"""

import hashlib
import base64
import json
import os
import subprocess
import sys
import uuid
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("BASE_URL", "https://test.api.heirlooms.digital").rstrip("/")
API_KEY  = os.environ.get("API_KEY", "")

PASS = 0
FAIL = 0

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

def ok(label):
    global PASS
    PASS += 1
    print(f"  PASS  {label}")

def fail(label, detail=""):
    global FAIL
    FAIL += 1
    msg = f"  FAIL  {label}"
    if detail:
        msg += f"\n        {detail}"
    print(msg)

def section(title):
    print(f"\n=== {title} ===")

# ---------------------------------------------------------------------------
# Crypto helpers (no third-party deps)
# ---------------------------------------------------------------------------

def sha256(b: bytes) -> bytes:
    return hashlib.sha256(b).digest()

def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

def b64std(b: bytes) -> str:
    return base64.b64encode(b).decode()

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def _request(method: str, path: str, body=None, token: str = None,
             content_type: str = "application/json") -> tuple[int, bytes]:
    url = BASE_URL + path
    if isinstance(body, str):
        body = body.encode()
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Content-Type", content_type)
    if token:
        req.add_header("X-Api-Key", token)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()

def get(path: str, token: str = None) -> tuple[int, bytes]:
    return _request("GET", path, token=token)

def post_json(path: str, body: dict, token: str = None) -> tuple[int, bytes]:
    return _request("POST", path, body=json.dumps(body), token=token)

def post_bytes(path: str, body: bytes, token: str = None) -> tuple[int, bytes]:
    return _request("POST", path, body=body, token=token,
                    content_type="application/octet-stream")

def parse(raw: bytes) -> dict:
    return json.loads(raw.decode())

# ---------------------------------------------------------------------------
# API key
# ---------------------------------------------------------------------------

def fetch_api_key() -> str:
    if API_KEY:
        return API_KEY
    try:
        result = subprocess.run(
            ["gcloud", "secrets", "versions", "access", "latest",
             "--secret=heirlooms-test-api-key", "--project=heirlooms-495416"],
            capture_output=True, text=True, check=True
        )
        return result.stdout.strip()
    except Exception as e:
        print(f"ERROR: could not fetch API key from Secret Manager: {e}")
        print("Set API_KEY env var or ensure gcloud is authenticated.")
        sys.exit(1)

# ---------------------------------------------------------------------------
# User helpers
# ---------------------------------------------------------------------------

def generate_invite(session_token: str) -> str:
    status, raw = get("/api/auth/invites", token=session_token)
    assert status == 200, f"generate_invite failed {status}: {raw.decode()}"
    return parse(raw)["token"]

def register_user(invite_token: str, username: str) -> tuple[str, str]:
    """Returns (session_token, user_id)."""
    auth_key      = bytes((len(username) + i) % 256 for i in range(32))
    auth_salt     = bytes(range(16))
    auth_verifier = sha256(auth_key)
    wrapped_mk    = bytes([5] * 64)
    pubkey        = bytes([6] * 65)
    device_id     = str(uuid.uuid4())

    body = {
        "invite_token":      invite_token,
        "username":          username,
        "display_name":      username.capitalize(),
        "auth_salt":         b64url(auth_salt),
        "auth_verifier":     b64url(auth_verifier),
        "wrapped_master_key": b64std(wrapped_mk),
        "wrap_format":       "p256-ecdh-hkdf-aes256gcm-v1",
        "pubkey_format":     "p256-spki",
        "pubkey":            b64std(pubkey),
        "device_id":         device_id,
        "device_label":      f"{username}'s Phone",
        "device_kind":       "android",
    }
    status, raw = post_json("/api/auth/register", body)
    assert status == 201, f"register_user({username}) failed {status}: {raw.decode()}"
    data = parse(raw)
    return data["session_token"], data["user_id"]

# ---------------------------------------------------------------------------
# Happy-path sharing flow
# ---------------------------------------------------------------------------

def run_happy_path(founding_token: str):
    section("Happy-path sharing flow")

    suffix = uuid.uuid4().hex[:6]
    alice_name = f"smoke_alice_{suffix}"
    bob_name   = f"smoke_bob_{suffix}"

    # Register Alice
    inv1 = generate_invite(founding_token)
    session_a, user_id_a = register_user(inv1, alice_name)
    ok(f"Register Alice ({alice_name})")

    # Register Bob
    inv2 = generate_invite(founding_token)
    session_b, user_id_b = register_user(inv2, bob_name)
    ok(f"Register Bob ({bob_name})")

    # Alice generates friend invite; Bob connects
    inv3 = generate_invite(session_a)
    status, raw = post_json(f"/api/auth/invites/{inv3}/connect", {}, token=session_b)
    if status == 200:
        ok("Friend connect (Alice → Bob)")
    else:
        fail("Friend connect (Alice → Bob)", f"{status}: {raw.decode()}")
        return None  # can't continue without friendship

    # Alice uploads a binary blob
    file_bytes = b"hello heirlooms - sharing smoke test"
    status, raw = post_bytes("/api/content/upload", file_bytes, token=session_a)
    if status == 201:
        upload_id = parse(raw)["id"]
        ok(f"Alice uploads file (id={upload_id[:8]}…)")
    else:
        fail("Alice uploads file", f"{status}: {raw.decode()}")
        return None

    # Alice shares to Bob
    fake_dek = b64std(bytes(range(32)))
    status, raw = post_json(
        f"/api/content/uploads/{upload_id}/share",
        {"toUserId": user_id_b, "wrappedDek": fake_dek, "dekFormat": "test-v1"},
        token=session_a,
    )
    if status == 201:
        recipient_id = parse(raw)["id"]
        ok(f"Alice shares to Bob (recipient id={recipient_id[:8]}…)")
    else:
        fail("Alice shares to Bob", f"{status}: {raw.decode()}")
        return None

    # Bob retrieves file bytes
    status, raw = get(f"/api/content/uploads/{recipient_id}/file", token=session_b)
    if status == 200 and raw == file_bytes:
        ok("Bob retrieves file — bytes match exactly")
    elif status == 200:
        fail("Bob retrieves file — bytes MISMATCH",
             f"expected={file_bytes!r}, got={raw!r}")
    else:
        fail("Bob retrieves file", f"{status}: {raw.decode()}")

    # Bob's received list contains at least one item
    status, raw = get("/api/content/uploads?is_received=true", token=session_b)
    if status == 200:
        items = parse(raw).get("items", [])
        if len(items) >= 1:
            ok(f"Bob's received list has {len(items)} item(s)")
        else:
            fail("Bob's received list", "expected ≥1 item, got 0")
    else:
        fail("Bob's received list", f"{status}: {raw.decode()}")

    return {"session_a": session_a, "session_b": session_b,
            "user_id_b": user_id_b, "upload_id": upload_id,
            "recipient_id": recipient_id, "founding_token": founding_token}


# ---------------------------------------------------------------------------
# Negative tests
# ---------------------------------------------------------------------------

def run_negative_tests(ctx: dict):
    section("Negative tests")

    founding_token = ctx["founding_token"]
    session_a      = ctx["session_a"]
    session_b      = ctx["session_b"]
    upload_id      = ctx["upload_id"]      # Alice's original
    recipient_id   = ctx["recipient_id"]   # Bob's copy

    # 1. Bob cannot access Alice's original upload
    status, _ = get(f"/api/content/uploads/{upload_id}/file", token=session_b)
    if status == 404:
        ok("B cannot access A's original upload (→ 404)")
    else:
        fail("B cannot access A's original upload", f"expected 404, got {status}")

    # 2. Sharing requires friendship — register Charlie (no friend link to Alice)
    inv_c = generate_invite(founding_token)
    suffix = uuid.uuid4().hex[:6]
    session_c, user_id_c = register_user(inv_c, f"smoke_charlie_{suffix}")
    fake_dek = b64std(bytes([0xAA] * 32))
    status, raw = post_json(
        f"/api/content/uploads/{upload_id}/share",
        {"toUserId": user_id_c, "wrappedDek": fake_dek, "dekFormat": "test-v1"},
        token=session_a,
    )
    if status == 403:
        ok("Share to non-friend returns 403")
    else:
        fail("Share to non-friend", f"expected 403, got {status}: {raw.decode()}")

    # 3. Non-member (Dave) cannot retrieve Bob's recipient copy
    inv_d = generate_invite(founding_token)
    session_d, _ = register_user(inv_d, f"smoke_dave_{suffix}")
    status, _ = get(f"/api/content/uploads/{recipient_id}/file", token=session_d)
    if status == 404:
        ok("Non-member cannot retrieve shared file (→ 404)")
    else:
        fail("Non-member retrieve", f"expected 404, got {status}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print(f"Target: {BASE_URL}")
    founding_token = fetch_api_key()
    print(f"API key: {'*' * 8}{founding_token[-4:]}\n")

    # Verify server is up
    status, raw = get("/health")
    if status == 200 and raw == b"ok":
        ok("Server health check")
    else:
        fail("Server health check", f"{status}: {raw}")
        sys.exit(1)

    ctx = run_happy_path(founding_token)

    if ctx:
        run_negative_tests(ctx)
    else:
        print("\n  (negative tests skipped — happy path did not complete)")

    print(f"\n{'='*40}")
    total = PASS + FAIL
    print(f"Results: {PASS}/{total} passed, {FAIL} failed")
    print('='*40)
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
