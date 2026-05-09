# Heirlooms Envelope Format Specification

**Version:** 1 (M7)
**Status:** Locked — E3 (Android) and E4 (Web) implement against this spec.

---

## Purpose

All encrypted blobs in Heirlooms use a single versioned envelope format. One format
covers file content, thumbnails, per-upload metadata, capsule message bodies, and
wrapped keys (DEKs under master key; master key under device pubkey or passphrase).

The format is:
- **Versioned:** the first byte identifies the envelope version. The current version is 1.
- **Algorithm-identified:** each envelope names its algorithm explicitly. Unknown algorithm
  IDs must fail loudly. This is the crypto-agility guarantee — new algorithms can be
  introduced without changing the binary structure.
- **Self-describing:** all length fields are within the binary. Callers do not pass lengths
  in from outside.

The server does no decryption. It validates that uploaded blobs are structurally correct
envelopes (correct version, recognised algorithm ID, sane lengths) but never inspects the
plaintext content.

---

## Binary format

Two variants: symmetric (for content encrypted with a symmetric key) and asymmetric
(for keys wrapped to a device public key via ECDH).

### Symmetric envelope

Used for: file bytes, thumbnail bytes, encrypted metadata blob, capsule message
body, and DEK-under-master-key wraps.

```
+------------------+------------+
| envelope_version |  1 byte    — currently 0x01
+------------------+------------+
| alg_id_len       |  1 byte    — byte length of the algorithm ID string (1–64)
+------------------+------------+
| alg_id           |  N bytes   — UTF-8 algorithm identifier string
+------------------+------------+
| nonce            | 12 bytes   — AES-GCM nonce; randomly generated per encryption
+------------------+------------+
| ciphertext       |  variable  — zero or more bytes
+------------------+------------+
| auth_tag         | 16 bytes   — AES-GCM authentication tag
+------------------+------------+
```

Minimum size: `1 + 1 + 1 + 12 + 0 + 16 = 31` bytes (1-byte alg_id, empty ciphertext).

### Asymmetric envelope

Used for: master-key-under-device-pubkey wraps (P-256 ECDH wrapping).

```
+------------------+------------+
| envelope_version |  1 byte    — currently 0x01
+------------------+------------+
| alg_id_len       |  1 byte
+------------------+------------+
| alg_id           |  N bytes
+------------------+------------+
| ephemeral_pubkey | 65 bytes   — P-256 SEC1 uncompressed (0x04 prefix + 32 + 32 bytes)
+------------------+------------+
| nonce            | 12 bytes
+------------------+------------+
| ciphertext       |  variable  — typically 32 bytes for a wrapped 256-bit key
+------------------+------------+
| auth_tag         | 16 bytes
+------------------+------------+
```

Minimum size: `1 + 1 + 1 + 65 + 12 + 0 + 16 = 96` bytes.

---

## Algorithm identifiers (M7)

| ID | Use |
|---|---|
| `aes256gcm-v1` | Symmetric content encryption (DEK encrypts file bytes, thumbnail bytes, encrypted metadata, capsule message bodies) |
| `master-aes256gcm-v1` | DEK wrap under master key (master key used directly as a 256-bit AES key) |
| `p256-ecdh-hkdf-aes256gcm-v1` | Master key wrap to device pubkey (P-256 ECDH → HKDF-SHA256 → AES-256-GCM) |
| `argon2id-aes256gcm-v1` | Master key wrap under passphrase (Argon2id KDF → AES-256-GCM) |

**Unknown IDs must fail loudly.** Decryption code must not silently skip or ignore an
unrecognised algorithm ID — throw an exception with the unrecognised ID in the message.

The format is open to new identifiers. Adding a new algorithm ID in a future milestone
does not require a version bump of the envelope format itself. Old clients that encounter
an unknown ID will correctly fail loudly rather than silently misinterpreting the bytes.

---

## Nonce generation

Nonces are 12 random bytes, generated from a cryptographically secure random number
generator (e.g. `SecureRandom` on Android/JVM, `crypto.getRandomValues` on Web).

**Never use a counter or a deterministic nonce.** AES-256-GCM nonce reuse under the same
key produces catastrophic plaintext disclosure. With 96-bit random nonces and reasonable
upload volumes (millions of files), collision probability is negligible.

One fresh nonce per encryption operation. The nonce is stored in the envelope and is never
reused across different encryptions under the same key.

---

## Argon2id parameters (default for M7)

Used for passphrase-wrapped master key backups (`argon2id-aes256gcm-v1`).

| Parameter | Value |
|---|---|
| Memory cost | 65536 KiB (64 MiB) |
| Iterations | 3 |
| Parallelism | 1 |
| Salt | 16 random bytes per user (stored in `recovery_passphrase.salt`) |
| Output length | 32 bytes (256-bit AES key) |

These parameters are stored in `recovery_passphrase.argon2_params` as JSON alongside
each wrapped blob. Future parameter changes only apply to new wraps; old wraps keep
working because their parameters are stored with the blob.

Benchmark on the Galaxy A02s (Bret's reference low-spec Android target) before
committing. Memory cost may need to be lowered if passphrase login takes >1.5 seconds
on that device. Lowering to 32 MiB is acceptable; raise again later for new wraps if
desired.

---

## Encrypted metadata JSON schema

The `encrypted_metadata` blob on an upload row contains EXIF fields that are too
sensitive to expose server-side. The blob is encrypted under the upload's DEK using
`aes256gcm-v1`. Its plaintext is UTF-8 JSON with the following structure:

```json
{
  "v": 1,
  "gps_lat": null,
  "gps_lon": null,
  "gps_alt": null,
  "camera_make": null,
  "camera_model": null,
  "lens_model": null,
  "focal_length_mm": null,
  "iso": null,
  "exposure_num": null,
  "exposure_den": null,
  "aperture": null
}
```

Field definitions:

| Field | Type | Notes |
|---|---|---|
| `v` | `Int`, required | Schema version. Always `1` in M7. Clients must check this and fail loudly on unknown versions. |
| `gps_lat` | `Double?` | WGS84 decimal degrees. Negative = South. |
| `gps_lon` | `Double?` | WGS84 decimal degrees. Negative = West. |
| `gps_alt` | `Double?` | Metres above sea level. |
| `camera_make` | `String?` | EXIF Make tag (e.g. `"Apple"`, `"Samsung"`). |
| `camera_model` | `String?` | EXIF Model tag (e.g. `"iPhone 15 Pro"`). |
| `lens_model` | `String?` | EXIF LensModel tag, if present. |
| `focal_length_mm` | `Double?` | Actual (not 35mm-equivalent) focal length in millimetres. |
| `iso` | `Int?` | ISO speed rating. |
| `exposure_num` | `Int?` | Exposure time numerator. `1` for 1/250s. |
| `exposure_den` | `Int?` | Exposure time denominator. `250` for 1/250s. |
| `aperture` | `Double?` | F-number (e.g. `1.8` for f/1.8). |

All fields except `v` are optional (omit or set to `null` if not available). An all-null
blob is valid — it is produced when EXIF extraction yields nothing (common for videos).

Clients must tolerate unknown fields. Future schema versions may add fields; M7 clients
must parse what they know and ignore the rest.

**`taken_at` is not in this blob.** It lives plaintext in the `uploads.taken_at` column
and powers sort, filter, and Just arrived regardless of storage class.

**`device_make` and `device_model`** on the `uploads` row are legacy fields retained for
`legacy_plaintext` uploads. For `encrypted` uploads, these fields are `NULL`; camera
identity is in the encrypted metadata blob under `camera_make`/`camera_model`.

---

## Minimum and maximum blob sizes

For validators doing a fast pre-check before full parsing:

| Envelope type | Minimum bytes |
|---|---|
| Symmetric | 31 |
| Asymmetric (P-256) | 96 |

Maximum: no hard limit. Algorithm ID is capped at 64 bytes. Ciphertext length is
unbounded (it is whatever the plaintext was).

---

## Cross-platform implementation notes

The envelope format is the single shared contract between the server validator (Kotlin),
the Android client (Kotlin, M7 E3), and the web client (TypeScript, M7 E4).

**Cross-platform round-trip test discipline:** every encrypted blob written during a test
must be read back and validated against the original plaintext, byte for byte. This is
the primary safeguard against encryption bugs that produce permanently unrecoverable data.

**The server never decrypts.** The `EnvelopeFormat` Kotlin library in HeirloomsServer
validates structure only (version, algorithm ID, lengths). BouncyCastle appears in the
test suite as a reference implementation for generating test envelopes; it is scoped to
`testImplementation` and has no production dependency.
