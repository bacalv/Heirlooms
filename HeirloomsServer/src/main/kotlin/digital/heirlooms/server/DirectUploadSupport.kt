package digital.heirlooms.server

data class PreparedUpload(
    val storageKey: StorageKey,
    val uploadUrl: String,
)

interface DirectUploadSupport {
    fun prepareUpload(mimeType: String): PreparedUpload
}
