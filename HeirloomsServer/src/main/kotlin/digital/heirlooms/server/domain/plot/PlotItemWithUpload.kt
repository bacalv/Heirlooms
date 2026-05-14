package digital.heirlooms.server.domain.plot

import digital.heirlooms.server.domain.upload.UploadRecord
import java.util.UUID

data class PlotItemWithUpload(
    val upload: UploadRecord,
    val addedBy: UUID,
    val wrappedItemDek: ByteArray?,
    val itemDekFormat: String?,
    val wrappedThumbnailDek: ByteArray?,
    val thumbnailDekFormat: String?,
)
