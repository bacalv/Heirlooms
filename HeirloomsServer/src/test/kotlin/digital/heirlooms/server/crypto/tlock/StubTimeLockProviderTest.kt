package digital.heirlooms.server.crypto.tlock

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for StubTimeLockProvider (DEV-005 / ARCH-006 §2).
 *
 * Covers:
 * - seal() + decrypt() round-trip when clock is past unlockAt.
 * - decrypt() returns null before unlockAt.
 * - validate() returns false for wrong chain_id, wrong blob size.
 * - MAC verification failure throws TimeLockDecryptionException.
 * - Round derivation constants.
 */
class StubTimeLockProviderTest {

    private val rng = SecureRandom()

    private fun randomSecret(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }
    private fun randomKey(): ByteArray   = ByteArray(32).also { rng.nextBytes(it) }

    private fun clockAt(instant: Instant): Clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    // ─── seal + decrypt round-trip ─────────────────────────────────────────────

    @Test
    fun `seal then decrypt round-trips the key when clock is past unlockAt`() {
        val secret   = randomSecret()
        val key      = randomKey()
        val unlockAt = Instant.parse("2027-01-01T00:00:00Z")
        val afterUnlock = unlockAt.plusSeconds(10)

        val sealProvider    = StubTimeLockProvider(secret)
        val decryptProvider = StubTimeLockProvider(secret, clockAt(afterUnlock))

        val ciphertext = sealProvider.seal(key, unlockAt)

        assertEquals(TimeLockCiphertext.STUB_CHAIN_ID, ciphertext.chainId)
        assertTrue(ciphertext.round >= 1L)
        assertEquals(64, ciphertext.blob.size)

        val decrypted = decryptProvider.decrypt(ciphertext)
        assertNotNull(decrypted)
        assertArrayEquals(key, decrypted)
    }

    @Test
    fun `decrypt returns null before unlockAt`() {
        val secret   = randomSecret()
        val key      = randomKey()
        val unlockAt = Instant.parse("2050-01-01T00:00:00Z")
        val beforeUnlock = Instant.parse("2026-01-01T00:00:00Z")

        val provider = StubTimeLockProvider(secret, clockAt(beforeUnlock))
        val ciphertext = StubTimeLockProvider(secret).seal(key, unlockAt)

        val result = provider.decrypt(ciphertext)
        assertNull(result, "decrypt() should return null when gate is not yet open")
    }

    @Test
    fun `decrypt returns null exactly at unlockAt epoch boundary - before`() {
        val secret   = randomSecret()
        val key      = randomKey()
        val unlockAt = Instant.parse("2027-06-01T12:00:00Z")
        val round    = StubTimeLockProvider.roundForInstant(unlockAt)
        val epochSeconds = StubTimeLockProvider.epochSecondsForRound(round)
        // One second before the unlock epoch
        val justBefore = Instant.ofEpochSecond(epochSeconds - 1)

        val sealer  = StubTimeLockProvider(secret)
        val decrypter = StubTimeLockProvider(secret, clockAt(justBefore))
        val ciphertext = sealer.seal(key, unlockAt)

        assertNull(decrypter.decrypt(ciphertext), "Should be null 1 second before epoch")
    }

    @Test
    fun `decrypt succeeds exactly at unlock epoch boundary`() {
        val secret   = randomSecret()
        val key      = randomKey()
        val unlockAt = Instant.parse("2027-06-01T12:00:00Z")
        val round    = StubTimeLockProvider.roundForInstant(unlockAt)
        val epochSeconds = StubTimeLockProvider.epochSecondsForRound(round)
        val atEpoch  = Instant.ofEpochSecond(epochSeconds)

        val sealer    = StubTimeLockProvider(secret)
        val decrypter = StubTimeLockProvider(secret, clockAt(atEpoch))
        val ciphertext = sealer.seal(key, unlockAt)

        val result = decrypter.decrypt(ciphertext)
        assertNotNull(result)
        assertArrayEquals(key, result)
    }

    // ─── MAC tamper detection ──────────────────────────────────────────────────

    @Test
    fun `decrypt throws TimeLockDecryptionException on MAC verification failure`() {
        val secret   = randomSecret()
        val key      = randomKey()
        val unlockAt = Instant.parse("2027-01-01T00:00:00Z")
        val afterUnlock = unlockAt.plusSeconds(60)

        val provider   = StubTimeLockProvider(secret, clockAt(afterUnlock))
        val ciphertext = StubTimeLockProvider(secret).seal(key, unlockAt)

        // Tamper the MAC portion (last 32 bytes)
        val tampered = ciphertext.blob.copyOf()
        tampered[32] = (tampered[32].toInt() xor 0xFF).toByte()

        val tamperedCiphertext = TimeLockCiphertext(
            chainId = ciphertext.chainId,
            round   = ciphertext.round,
            blob    = tampered,
        )

        assertThrows(TimeLockDecryptionException::class.java) {
            provider.decrypt(tamperedCiphertext)
        }
    }

    @Test
    fun `decrypt throws TimeLockDecryptionException when blob is wrong size`() {
        val secret = randomSecret()
        val unlockAt = Instant.parse("2027-01-01T00:00:00Z")
        val afterUnlock = unlockAt.plusSeconds(60)

        val provider = StubTimeLockProvider(secret, clockAt(afterUnlock))
        val badCiphertext = TimeLockCiphertext(
            chainId = TimeLockCiphertext.STUB_CHAIN_ID,
            round   = 100L,
            blob    = ByteArray(10),  // wrong size
        )

        assertThrows(TimeLockDecryptionException::class.java) {
            provider.decrypt(badCiphertext)
        }
    }

    // ─── validate() ───────────────────────────────────────────────────────────

    @Test
    fun `validate returns true for a structurally correct stub ciphertext`() {
        val secret = randomSecret()
        val key    = randomKey()
        val unlockAt = Instant.parse("2027-01-01T00:00:00Z")

        val provider   = StubTimeLockProvider(secret)
        val ciphertext = provider.seal(key, unlockAt)

        assertTrue(provider.validate(ciphertext))
    }

    @Test
    fun `validate returns false for wrong chain_id`() {
        val secret = randomSecret()
        val provider = StubTimeLockProvider(secret)
        val wrongChain = TimeLockCiphertext(
            chainId = "not-the-stub-chain",
            round   = 42L,
            blob    = ByteArray(64),
        )
        assertFalse(provider.validate(wrongChain))
    }

    @Test
    fun `validate returns false for blob size not 64`() {
        val secret = randomSecret()
        val provider = StubTimeLockProvider(secret)

        assertFalse(provider.validate(TimeLockCiphertext(TimeLockCiphertext.STUB_CHAIN_ID, 1L, ByteArray(63))))
        assertFalse(provider.validate(TimeLockCiphertext(TimeLockCiphertext.STUB_CHAIN_ID, 1L, ByteArray(65))))
        assertFalse(provider.validate(TimeLockCiphertext(TimeLockCiphertext.STUB_CHAIN_ID, 1L, ByteArray(0))))
    }

    @Test
    fun `validate returns false for round 0`() {
        val secret = randomSecret()
        val provider = StubTimeLockProvider(secret)
        val bad = TimeLockCiphertext(TimeLockCiphertext.STUB_CHAIN_ID, 0L, ByteArray(64))
        assertFalse(provider.validate(bad))
    }

    // ─── Round derivation ─────────────────────────────────────────────────────

    @Test
    fun `roundForInstant returns 1 for instants before genesis`() {
        val before = Instant.ofEpochSecond(StubTimeLockProvider.STUB_GENESIS_UNIX - 100L)
        assertEquals(1L, StubTimeLockProvider.roundForInstant(before))
    }

    @Test
    fun `roundForInstant computes correct round for instant exactly at genesis + N periods`() {
        val genesis = Instant.ofEpochSecond(StubTimeLockProvider.STUB_GENESIS_UNIX)
        // Exactly 3 periods after genesis → round 3
        val at3 = genesis.plusSeconds(3L * StubTimeLockProvider.STUB_PERIOD_SECS)
        assertEquals(3L, StubTimeLockProvider.roundForInstant(at3))
    }

    @Test
    fun `roundForInstant rounds up when not on a period boundary`() {
        val genesis = Instant.ofEpochSecond(StubTimeLockProvider.STUB_GENESIS_UNIX)
        // 1 second after genesis → not on a boundary → round = ceil(1/3) = 1
        val at1 = genesis.plusSeconds(1)
        assertEquals(1L, StubTimeLockProvider.roundForInstant(at1))

        // 3 periods + 1 second → round = 4
        val at3plus1 = genesis.plusSeconds(3L * StubTimeLockProvider.STUB_PERIOD_SECS + 1L)
        assertEquals(4L, StubTimeLockProvider.roundForInstant(at3plus1))
    }

    @Test
    fun `publishTimeForRound is inverse of roundForInstant on period boundaries`() {
        val genesis = Instant.ofEpochSecond(StubTimeLockProvider.STUB_GENESIS_UNIX)
        for (r in 1L..20L) {
            val publish = StubTimeLockProvider.publishTimeForRound(r)
            // publishTime should be after genesis
            assertTrue(publish.epochSecond > StubTimeLockProvider.STUB_GENESIS_UNIX)
            // roundForInstant(publishTime) should be == r (it's exactly on the boundary)
            val derived = StubTimeLockProvider.roundForInstant(publish)
            assertEquals(r, derived, "round mismatch for r=$r: publishTime=$publish derived=$derived")
        }
    }

    @Test
    fun `constructor rejects stubSecret not 32 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            StubTimeLockProvider(ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StubTimeLockProvider(ByteArray(33))
        }
    }
}
