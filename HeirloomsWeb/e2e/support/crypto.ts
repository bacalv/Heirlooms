/**
 * Test-side crypto helpers for E2EE assertions.
 *
 * These are lightweight helpers for verifying encrypted data properties
 * in E2E tests — they do NOT duplicate the app's vaultCrypto.js logic.
 * They exist to let tests assert structural correctness of crypto outputs
 * (e.g., that an envelope has the expected format prefix) without
 * needing to decrypt real ciphertext.
 *
 * See: docs/envelope_format.md for envelope format details.
 * See: docs/specs/onboarding.md for wrapped key format conventions.
 */

/** Known envelope format identifiers used by Heirlooms E2EE */
export const ENVELOPE_FORMATS = {
  /** Asymmetric wrap: P-256 ECDH + HKDF + AES-256-GCM */
  DEVICE_WRAP: 'p256-ecdh-hkdf-aes256gcm-v1',
  /** Symmetric wrap under master key */
  MASTER_WRAP: 'master-aes256gcm-v1',
  /** Passphrase wrap: Argon2id + AES-256-GCM */
  PASSPHRASE_WRAP: 'argon2id-aes256gcm-v1',
  /** Plot key wrap (shared plots) */
  PLOT_WRAP: 'plot-aes256gcm-v1',
} as const;

/**
 * Check that a base64url string is non-empty and plausibly a valid
 * base64url-encoded blob (no padding, URL-safe alphabet).
 */
export function isBase64Url(value: string): boolean {
  return /^[A-Za-z0-9_-]+$/.test(value) && value.length > 0;
}

/**
 * Decode a base64url string to a Uint8Array.
 * Useful for byte-length assertions on wrapped keys.
 */
export function fromBase64Url(value: string): Uint8Array {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Assert that a wrapped key blob is non-empty and has a plausible minimum
 * length for the given format. Does NOT attempt actual decryption.
 *
 * Minimum sizes (conservative lower bounds):
 *   - p256-ecdh-hkdf-aes256gcm-v1: ephemeral pubkey (65 bytes) + nonce (12) + tag (16) ≈ 93 bytes
 *   - master-aes256gcm-v1: nonce (12) + ciphertext (32) + tag (16) ≈ 60 bytes
 *   - argon2id-aes256gcm-v1: salt (16) + params + nonce (12) + ct + tag ≈ 80 bytes
 */
export function assertWrappedKeyPlausible(
  b64url: string,
  format: string
): void {
  if (!isBase64Url(b64url)) {
    throw new Error(`assertWrappedKeyPlausible: value is not valid base64url: "${b64url}"`);
  }
  const bytes = fromBase64Url(b64url);
  const minBytes: Record<string, number> = {
    [ENVELOPE_FORMATS.DEVICE_WRAP]: 93,
    [ENVELOPE_FORMATS.MASTER_WRAP]: 60,
    [ENVELOPE_FORMATS.PASSPHRASE_WRAP]: 80,
    [ENVELOPE_FORMATS.PLOT_WRAP]: 60,
  };
  const min = minBytes[format] ?? 32;
  if (bytes.length < min) {
    throw new Error(
      `assertWrappedKeyPlausible: blob for format "${format}" is too short ` +
        `(${bytes.length} bytes, expected >= ${min})`
    );
  }
}
