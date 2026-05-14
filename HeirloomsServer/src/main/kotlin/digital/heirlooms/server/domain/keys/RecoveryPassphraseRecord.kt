package digital.heirlooms.server.domain.keys

import java.time.Instant

data class RecoveryPassphraseRecord(
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val argon2Params: String,
    val salt: ByteArray,
    val createdAt: Instant,
    val updatedAt: Instant,
)
