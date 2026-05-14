package digital.heirlooms.server.domain.upload

data class UploadPage(val items: List<UploadRecord>, val nextCursor: String?)
