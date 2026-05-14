package digital.heirlooms.server.domain.capsule

import digital.heirlooms.server.domain.upload.UploadRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

enum class CapsuleShape { OPEN, SEALED }
enum class CapsuleState { OPEN, SEALED, DELIVERED, CANCELLED }

data class CapsuleRecord(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdByUser: String,
    val shape: CapsuleShape,
    val state: CapsuleState,
    val unlockAt: OffsetDateTime,
    val cancelledAt: Instant?,
    val deliveredAt: Instant?,
)

data class CapsuleSummary(
    val record: CapsuleRecord,
    val recipients: List<String>,
    val uploadCount: Int,
    val hasMessage: Boolean,
)

data class CapsuleDetail(
    val record: CapsuleRecord,
    val recipients: List<String>,
    val uploads: List<UploadRecord>,
    val message: String,
)
