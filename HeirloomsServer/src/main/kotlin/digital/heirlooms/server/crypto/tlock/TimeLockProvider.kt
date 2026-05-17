package digital.heirlooms.server.crypto.tlock

import java.time.Instant

/**
 * Abstraction over the tlock time-lock scheme.
 *
 * All implementations must satisfy:
 * - seal() and decrypt() are inverses: decrypt(seal(key, t)) == key when now() >= t.
 * - validate() is a structural check only; it must not perform decryption.
 * - decrypt() returns null (not an exception) when the gate is not yet open.
 *
 * BLINDING SCHEME NOTE (ARCH-003 §9):
 * The plaintext passed to seal() is DEK_client (the 32-byte client-side blinding
 * mask), NOT the capsule DEK. The server never sees or stores the full DEK.
 */
interface TimeLockProvider {

    /**
     * Produce a tlock ciphertext for the given plaintext key, gated to unlockAt.
     *
     * @param plaintextKey  32-byte value to encrypt (DEK_client under the blinding scheme).
     * @param unlockAt      The earliest instant at which decrypt() may succeed.
     * @return              A [TimeLockCiphertext] carrying the chain ID, round, and opaque blob.
     */
    fun seal(plaintextKey: ByteArray, unlockAt: Instant): TimeLockCiphertext

    /**
     * Attempt to decrypt a previously sealed ciphertext.
     *
     * Returns the plaintext key (DEK_client) if the gate is open (round has published
     * and now() >= unlockAt for stub), or null if the gate is not yet open.
     *
     * Never throws for a time-gate not-yet-open condition; only throws for structural
     * corruption or an unrecognised chain ID.
     */
    fun decrypt(ciphertext: TimeLockCiphertext): ByteArray?

    /**
     * Validate the structural integrity of a tlock ciphertext blob.
     * Called at sealing time — does not attempt decryption.
     *
     * @return true if the blob is structurally valid; false otherwise.
     */
    fun validate(ciphertext: TimeLockCiphertext): Boolean
}

/**
 * Sentinel implementation used when TLOCK_PROVIDER=disabled.
 * All operations either throw or return false — sealing with tlock fields is rejected.
 */
object DisabledTimeLockProvider : TimeLockProvider {
    override fun seal(plaintextKey: ByteArray, unlockAt: Instant): TimeLockCiphertext =
        throw UnsupportedOperationException("tlock provider is disabled")

    override fun decrypt(ciphertext: TimeLockCiphertext): ByteArray? =
        throw UnsupportedOperationException("tlock provider is disabled")

    override fun validate(ciphertext: TimeLockCiphertext): Boolean = false
}
