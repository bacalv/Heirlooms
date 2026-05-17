package digital.heirlooms.server.crypto.tlock

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stub TimeLockProvider for staging/testing. Implements the full tlock path
 * (seal, validate, decrypt) without real BLS12-381 cryptography.
 *
 * Blob format (64 bytes):
 *   blob[0..31]  = plaintextKey (DEK_client, 32 bytes)
 *   blob[32..63] = HMAC-SHA256(stubSecret, roundEpochSeconds || plaintextKey)
 *                  where roundEpochSeconds = STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS
 *
 * Round derivation:
 *   STUB_GENESIS_UNIX = 1_700_000_000L
 *   STUB_PERIOD_SECS  = 3L
 *   round = ceil((unlockAt.epochSecond - STUB_GENESIS_UNIX) / STUB_PERIOD_SECS)
 *   minimum round = 1
 *
 * @param stubSecret  32-byte secret key from TLOCK_STUB_SECRET env var (base64url-decoded).
 * @param clock       Injected clock for testing; defaults to system UTC.
 */
class StubTimeLockProvider(
    private val stubSecret: ByteArray,
    private val clock: Clock = Clock.systemUTC(),
) : TimeLockProvider {

    init {
        require(stubSecret.size == 32) { "stubSecret must be exactly 32 bytes, got ${stubSecret.size}" }
    }

    companion object {
        const val STUB_GENESIS_UNIX = 1_700_000_000L
        const val STUB_PERIOD_SECS  = 3L
        private const val BLOB_SIZE = 64
        private const val KEY_SIZE  = 32

        /**
         * Derive the drand round number from an unlock instant.
         * Round is 1-based; minimum is 1 (before genesis maps to round 1).
         */
        fun roundForInstant(unlockAt: Instant): Long {
            val delta = unlockAt.epochSecond - STUB_GENESIS_UNIX
            return if (delta <= 0L) 1L
            else {
                val q = delta / STUB_PERIOD_SECS
                val r = delta % STUB_PERIOD_SECS
                if (r == 0L) q else q + 1L
            }
        }

        /**
         * Expected publish time for a given round number.
         * expected_publish = STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS
         */
        fun publishTimeForRound(round: Long): Instant =
            Instant.ofEpochSecond(STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS)

        /**
         * Recover unlockAt epoch-seconds from a round number.
         * This is used by decrypt() to reconstruct the MAC input.
         */
        fun epochSecondsForRound(round: Long): Long =
            STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS
    }

    override fun seal(plaintextKey: ByteArray, unlockAt: Instant): TimeLockCiphertext {
        require(plaintextKey.size == KEY_SIZE) { "plaintextKey must be 32 bytes" }
        val round = roundForInstant(unlockAt)
        // Use the canonical epoch for this round (same value decrypt() will derive),
        // so seal() and decrypt() always use the same HMAC input.
        val roundEpoch = epochSecondsForRound(round)
        val mac = hmac(roundEpoch, plaintextKey)
        val blob = plaintextKey + mac
        return TimeLockCiphertext(
            chainId = TimeLockCiphertext.STUB_CHAIN_ID,
            round   = round,
            blob    = blob,
        )
    }

    override fun validate(ciphertext: TimeLockCiphertext): Boolean {
        if (ciphertext.blob.size != BLOB_SIZE) return false
        if (ciphertext.chainId != TimeLockCiphertext.STUB_CHAIN_ID) return false
        if (ciphertext.round < 1L) return false
        return true
    }

    override fun decrypt(ciphertext: TimeLockCiphertext): ByteArray? {
        val unlockAtEpoch = epochSecondsForRound(ciphertext.round)
        val now = clock.instant()

        // Gate: if now < unlockAt, return null (round not yet published)
        if (now.epochSecond < unlockAtEpoch) return null

        if (ciphertext.blob.size != BLOB_SIZE) {
            throw TimeLockDecryptionException("stub blob size invalid: ${ciphertext.blob.size}")
        }

        val plaintextKey = ciphertext.blob.copyOfRange(0, KEY_SIZE)
        val storedMac    = ciphertext.blob.copyOfRange(KEY_SIZE, BLOB_SIZE)
        val expectedMac  = hmac(unlockAtEpoch, plaintextKey)

        // Constant-time comparison
        if (!MessageDigest.isEqual(storedMac, expectedMac)) {
            throw TimeLockDecryptionException("stub blob MAC verification failed")
        }

        return plaintextKey
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun hmac(epochSeconds: Long, plaintextKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(stubSecret, "HmacSHA256"))
        val epochBuf = ByteBuffer.allocate(8).putLong(epochSeconds).array()
        mac.update(epochBuf)
        mac.update(plaintextKey)
        return mac.doFinal()
    }
}
