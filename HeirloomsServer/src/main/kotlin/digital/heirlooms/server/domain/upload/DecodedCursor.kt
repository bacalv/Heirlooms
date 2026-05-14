package digital.heirlooms.server.domain.upload

import java.util.UUID

// Internal to the repository layer — exposed here so UploadRepository can use it
data class DecodedCursor(val sort: UploadSort, val sortKeyMs: Long?, val id: UUID)
