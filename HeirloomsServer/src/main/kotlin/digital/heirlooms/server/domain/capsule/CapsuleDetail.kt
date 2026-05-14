package digital.heirlooms.server.domain.capsule

import digital.heirlooms.server.domain.upload.UploadRecord

data class CapsuleDetail(
    val record: CapsuleRecord,
    val recipients: List<String>,
    val uploads: List<UploadRecord>,
    val message: String,
)
