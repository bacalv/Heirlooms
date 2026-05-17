package digital.heirlooms.server.crypto.tlock

/**
 * A tlock ciphertext and its associated chain metadata.
 *
 * @param chainId  drand chain hash (hex string). Stub uses STUB_CHAIN_ID.
 * @param round    drand round number. Stub derives from unlockAt.
 * @param blob     Opaque ciphertext bytes. Stored in capsules.tlock_wrapped_key.
 */
data class TimeLockCiphertext(
    val chainId: String,
    val round: Long,
    val blob: ByteArray,
) {
    companion object {
        /** Fixed chain ID used by StubTimeLockProvider. Never matches any real drand chain. */
        const val STUB_CHAIN_ID = "stub-chain-0000000000000000000000000000000000000000000000000000000000000000"
    }

    override fun equals(other: Any?): Boolean =
        other is TimeLockCiphertext && chainId == other.chainId &&
            round == other.round && blob.contentEquals(other.blob)

    override fun hashCode(): Int = 31 * (31 * chainId.hashCode() + round.hashCode()) +
        blob.contentHashCode()
}
