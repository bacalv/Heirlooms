package digital.heirlooms.ui.garden

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import digital.heirlooms.api.Upload
import digital.heirlooms.api.isShared
import digital.heirlooms.app.EndpointStore
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class PhotoDetailState {
    object Loading : PhotoDetailState()
    data class Ready(val upload: Upload, val capsuleRefs: List<CapsuleRef>) : PhotoDetailState()
    data class Error(val message: String) : PhotoDetailState()
}

class PhotoDetailViewModel(
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<PhotoDetailState>(PhotoDetailState.Loading)
    val state: StateFlow<PhotoDetailState> = _state

    // Decrypted full content for encrypted uploads (null = not yet loaded / plaintext)
    private val _decryptedBitmap = MutableStateFlow<ImageBitmap?>(null)
    val decryptedBitmap: StateFlow<ImageBitmap?> = _decryptedBitmap.asStateFlow()

    private val _decryptedVideoUri = MutableStateFlow<Uri?>(null)
    val decryptedVideoUri: StateFlow<Uri?> = _decryptedVideoUri.asStateFlow()

    // Unwrapped DEK for large encrypted videos — used by DecryptingDataSource instead of
    // downloading the full file.
    private val _contentDek = MutableStateFlow<ByteArray?>(null)
    val contentDek: StateFlow<ByteArray?> = _contentDek.asStateFlow()

    // All tags in the user's library — for TagInputField suggestions.
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    // Staged changes — null means not dirty (server value is authoritative).
    private val _stagedTags = MutableStateFlow<List<String>?>(null)
    val stagedTags: StateFlow<List<String>?> = _stagedTags.asStateFlow()

    private val _stagedRotation = MutableStateFlow<Int?>(null)
    val stagedRotation: StateFlow<Int?> = _stagedRotation.asStateFlow()

    val isDirty: StateFlow<Boolean> = combine(_stagedTags, _stagedRotation) { t, r ->
        t != null || r != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var viewTracked = false

    fun load(api: HeirloomsApi, uploadId: String, context: Context? = null, thresholdSeconds: Int = EndpointStore.DEFAULT_VIDEO_THRESHOLD) {
        viewModelScope.launch {
            _state.value = PhotoDetailState.Loading
            _stagedTags.value = null
            _stagedRotation.value = null
            _decryptedBitmap.value = null
            _decryptedVideoUri.value = null
            _contentDek.value = null
            try {
                val upload = api.getUpload(uploadId)
                val refs = try { api.getCapsulesForUpload(uploadId) } catch (_: Exception) { emptyList() }
                _state.value = PhotoDetailState.Ready(upload, refs)
                if (upload.isEncrypted && context != null) {
                    loadEncryptedContent(api, upload, context, thresholdSeconds)
                }
            } catch (e: Exception) {
                _state.value = PhotoDetailState.Error(e.message ?: "Couldn't load")
            }
        }
        viewModelScope.launch {
            try { _availableTags.value = api.listTags() } catch (_: Exception) {}
        }
    }

    private fun loadEncryptedContent(api: HeirloomsApi, upload: Upload, context: Context, thresholdSeconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val mk = VaultSession.masterKey

                // Decide whether to use the preview clip.
                val knowsDuration = upload.durationSeconds != null
                val exceedsThreshold = if (knowsDuration)
                    upload.durationSeconds!! > thresholdSeconds
                else
                    upload.fileSize > LARGE_VIDEO_THRESHOLD

                if (upload.isVideo && exceedsThreshold && upload.previewStorageKey != null) {
                    // Has a preview clip and video exceeds threshold — play the preview.
                    val wrappedPreviewDek = upload.wrappedPreviewDek ?: return@runCatching
                    val previewDek = VaultCrypto.unwrapDekWithMasterKey(wrappedPreviewDek, mk)
                    val encryptedBytes = api.fetchBytes(api.previewUrl(upload.id))
                    val decryptedBytes = VaultCrypto.decryptSymmetric(encryptedBytes, previewDek)
                    val tempDir = File(context.cacheDir, "vault_temp").also { it.mkdirs() }
                    val tempFile = File(tempDir, "${upload.id}_preview.mp4")
                    tempFile.writeBytes(decryptedBytes)
                    _decryptedVideoUri.value = Uri.fromFile(tempFile)
                    return@runCatching
                }

                val wrappedDek = upload.wrappedDek ?: return@runCatching
                val dek = when (upload.dekFormat) {
                    VaultCrypto.ALG_P256_ECDH_HKDF_V1 -> {
                        val privkey = VaultSession.sharingPrivkey ?: return@runCatching
                        VaultCrypto.unwrapWithSharingKey(wrappedDek, privkey)
                    }
                    VaultCrypto.ALG_PLOT_AES256GCM_V1 -> {
                        val plotKey = VaultSession.plotKeys.values.firstOrNull { key ->
                            runCatching { VaultCrypto.unwrapDekWithPlotKey(wrappedDek, key) }.isSuccess
                        } ?: return@runCatching
                        VaultCrypto.unwrapDekWithPlotKey(wrappedDek, plotKey)
                    }
                    else -> VaultCrypto.unwrapDekWithMasterKey(wrappedDek, mk)
                }

                if (upload.isVideo && exceedsThreshold && upload.previewStorageKey == null) {
                    // Exceeds threshold but no preview clip — show nothing (thumbnail displays).
                    return@runCatching
                }

                if (upload.isVideo && upload.fileSize > LARGE_VIDEO_THRESHOLD && !exceedsThreshold) {
                    // Under threshold but large file: stream-decrypt via DecryptingDataSource.
                    _contentDek.value = dek
                    return@runCatching
                }

                if (upload.isVideo && upload.fileSize > LARGE_VIDEO_THRESHOLD) {
                    // Legacy large video (no duration stored): stream-decrypt via DecryptingDataSource.
                    _contentDek.value = dek
                    return@runCatching
                }

                val encryptedBytes = api.fetchBytes(api.fileUrl(upload.id))
                // Envelope format starts with version byte 0x01; streaming chunk format starts
                // with a nonce whose first byte comes from the printable storageKey string.
                val decryptedBytes = if (encryptedBytes.isNotEmpty() && (encryptedBytes[0].toInt() and 0xFF) == 1) {
                    VaultCrypto.decryptSymmetric(encryptedBytes, dek)
                } else {
                    VaultCrypto.decryptStreamingContent(encryptedBytes, dek, upload.plainChunkSize ?: (4 * 1024 * 1024 - 28))
                }

                if (upload.isVideo) {
                    val ext = when {
                        upload.mimeType.contains("mp4") -> "mp4"
                        upload.mimeType.contains("mov") -> "mov"
                        else -> "bin"
                    }
                    val tempDir = File(context.cacheDir, "vault_temp").also { it.mkdirs() }
                    val tempFile = File(tempDir, "${upload.id}.$ext")
                    tempFile.writeBytes(decryptedBytes)
                    _decryptedVideoUri.value = Uri.fromFile(tempFile)
                } else {
                    val exif = exifRotationDegrees(decryptedBytes)
                    val ready = _state.value as? PhotoDetailState.Ready
                    if (exif != 0 && ready?.upload?.rotation == 0 && _stagedRotation.value == null) {
                        _stagedRotation.value = exif
                    }
                    val bmp = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                    _decryptedBitmap.value = bmp?.asImageBitmap()
                }
            }
        }
    }

    fun downloadFullFile(api: HeirloomsApi, upload: Upload, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val mk = VaultSession.masterKey
                val wrappedDek = upload.wrappedDek ?: return@runCatching
                val dek = when (upload.dekFormat) {
                    VaultCrypto.ALG_P256_ECDH_HKDF_V1 -> {
                        val privkey = VaultSession.sharingPrivkey ?: return@runCatching
                        VaultCrypto.unwrapWithSharingKey(wrappedDek, privkey)
                    }
                    VaultCrypto.ALG_PLOT_AES256GCM_V1 -> {
                        val plotKey = VaultSession.plotKeys.values.firstOrNull { key ->
                            runCatching { VaultCrypto.unwrapDekWithPlotKey(wrappedDek, key) }.isSuccess
                        } ?: return@runCatching
                        VaultCrypto.unwrapDekWithPlotKey(wrappedDek, plotKey)
                    }
                    else -> VaultCrypto.unwrapDekWithMasterKey(wrappedDek, mk)
                }
                val encryptedBytes = api.fetchBytes(api.fileUrl(upload.id))
                val plainBytes = if (encryptedBytes.isNotEmpty() && (encryptedBytes[0].toInt() and 0xFF) == 1) {
                    VaultCrypto.decryptSymmetric(encryptedBytes, dek)
                } else {
                    VaultCrypto.decryptStreamingContent(encryptedBytes, dek, upload.plainChunkSize ?: (4 * 1024 * 1024 - 28))
                }
                val ext = upload.mimeType.substringAfterLast('/').substringBefore(';').ifEmpty { "bin" }
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val outFile = File(downloadsDir, "heirloom_${upload.id.take(8)}.$ext")
                outFile.writeBytes(plainBytes)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
            }
        }
    }

    private companion object {
        const val LARGE_VIDEO_THRESHOLD = 10L * 1024 * 1024

        fun exifRotationDegrees(bytes: ByteArray): Int = try {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    fun trackView(api: HeirloomsApi, uploadId: String) {
        if (viewTracked) return
        viewTracked = true
        viewModelScope.launch { api.trackView(uploadId) }
    }

    fun stageTags(tags: List<String>) {
        _stagedTags.value = tags
    }

    fun stageRotate() {
        val current = _state.value as? PhotoDetailState.Ready ?: return
        val currentRotation = _stagedRotation.value ?: current.upload.rotation
        _stagedRotation.value = (currentRotation + 90) % 360
    }

    // Effective values to display — staged if present, server value otherwise.
    fun effectiveTags(): List<String> {
        val ready = _state.value as? PhotoDetailState.Ready ?: return emptyList()
        return _stagedTags.value ?: ready.upload.tags
    }

    fun effectiveRotation(): Int {
        val ready = _state.value as? PhotoDetailState.Ready ?: return 0
        return _stagedRotation.value ?: ready.upload.rotation
    }

    suspend fun saveChanges(api: HeirloomsApi, uploadId: String) {
        val tagsToSave = _stagedTags.value
        val rotationToSave = _stagedRotation.value
        if (tagsToSave == null && rotationToSave == null) return
        try {
            if (tagsToSave != null) {
                val updated = api.updateTags(uploadId, tagsToSave)
                (_state.value as? PhotoDetailState.Ready)?.let {
                    _state.value = it.copy(upload = updated)
                }
                _stagedTags.value = null
            }
            if (rotationToSave != null) {
                val updated = api.rotateUpload(uploadId, rotationToSave)
                (_state.value as? PhotoDetailState.Ready)?.let {
                    _state.value = it.copy(upload = updated)
                }
                _stagedRotation.value = null
            }
        } catch (_: Exception) {}
    }

    fun reload(api: HeirloomsApi, uploadId: String) {
        viewModelScope.launch {
            try {
                val upload = api.getUpload(uploadId)
                val refs = try { api.getCapsulesForUpload(uploadId) } catch (_: Exception) { emptyList() }
                _state.value = PhotoDetailState.Ready(upload, refs)
            } catch (_: Exception) {}
        }
    }

    // ── Add to shared plot ────────────────────────────────────────────────────

    /** Sealed result for the add-to-plot operation, observed by the UI. */
    sealed class AddToPlotResult {
        object Idle : AddToPlotResult()
        object Working : AddToPlotResult()
        object Success : AddToPlotResult()
        object AlreadyPresent : AddToPlotResult()
        data class Error(val message: String) : AddToPlotResult()
    }

    private val _addToPlotResult = MutableStateFlow<AddToPlotResult>(AddToPlotResult.Idle)
    val addToPlotResult: StateFlow<AddToPlotResult> = _addToPlotResult.asStateFlow()

    fun resetAddToPlotResult() {
        _addToPlotResult.value = AddToPlotResult.Idle
    }

    /**
     * Fetches shared plots the user belongs to, for display in the picker.
     * Returns only joined shared plots (excludes personal/private plots).
     */
    suspend fun listSharedPlots(api: HeirloomsApi): List<Plot> = withContext(Dispatchers.IO) {
        try {
            api.listPlots().filter { it.isShared }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Wraps the photo's DEK under the given plot's group key and calls addPlotItem.
     * Mirrors the staging approval pattern from StagingViewModel.
     */
    fun addToPlot(api: HeirloomsApi, plotId: String, upload: Upload) {
        _addToPlotResult.value = AddToPlotResult.Working
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // 1. Resolve the plot key (cache-first, then fetch from server).
                val plotKey = VaultSession.getPlotKey(plotId) ?: run {
                    val (wrappedKey, _) = api.getPlotKey(plotId)
                    val privkey = VaultSession.sharingPrivkey
                        ?: error("Sharing key not loaded — vault may need re-unlock")
                    val raw = VaultCrypto.unwrapPlotKey(
                        Base64.decode(wrappedKey, Base64.NO_WRAP),
                        privkey,
                    )
                    VaultSession.setPlotKey(plotId, raw)
                    raw
                }

                // 2. Unwrap the photo's DEK (same logic as loadEncryptedContent).
                val wrappedDek = upload.wrappedDek
                    ?: error("Upload has no wrapped DEK — cannot re-wrap for shared plot")
                val rawDek = when (upload.dekFormat) {
                    VaultCrypto.ALG_P256_ECDH_HKDF_V1 -> {
                        val privkey = VaultSession.sharingPrivkey
                            ?: error("Sharing key not loaded")
                        VaultCrypto.unwrapWithSharingKey(wrappedDek, privkey)
                    }
                    VaultCrypto.ALG_PLOT_AES256GCM_V1 -> {
                        // Already wrapped under a plot key — unwrap it.
                        val sourceKey = VaultSession.plotKeys.values.firstOrNull { key ->
                            runCatching { VaultCrypto.unwrapDekWithPlotKey(wrappedDek, key) }.isSuccess
                        } ?: error("No plot key available to unwrap source DEK")
                        VaultCrypto.unwrapDekWithPlotKey(wrappedDek, sourceKey)
                    }
                    else -> VaultCrypto.unwrapDekWithMasterKey(wrappedDek, VaultSession.masterKey)
                }

                // 3. Re-wrap the DEK under the target plot key.
                val rewrappedDek = VaultCrypto.wrapDekWithPlotKey(rawDek, plotKey)
                val wrappedDekB64 = Base64.encodeToString(rewrappedDek, Base64.NO_WRAP)

                // 4. Re-wrap the thumbnail DEK if present.
                var wrappedThumbB64: String? = null
                var thumbDekFormat: String? = null
                val wrappedThumbDek = upload.wrappedThumbnailDek
                if (wrappedThumbDek != null) {
                    val rawThumb = when (upload.thumbnailDekFormat) {
                        VaultCrypto.ALG_P256_ECDH_HKDF_V1 -> {
                            val privkey = VaultSession.sharingPrivkey
                                ?: error("Sharing key not loaded")
                            VaultCrypto.unwrapWithSharingKey(wrappedThumbDek, privkey)
                        }
                        VaultCrypto.ALG_PLOT_AES256GCM_V1 -> {
                            val sourceKey = VaultSession.plotKeys.values.firstOrNull { key ->
                                runCatching { VaultCrypto.unwrapDekWithPlotKey(wrappedThumbDek, key) }.isSuccess
                            } ?: error("No plot key available to unwrap thumbnail DEK")
                            VaultCrypto.unwrapDekWithPlotKey(wrappedThumbDek, sourceKey)
                        }
                        else -> VaultCrypto.unwrapDekWithMasterKey(wrappedThumbDek, VaultSession.masterKey)
                    }
                    val rewrappedThumb = VaultCrypto.wrapDekWithPlotKey(rawThumb, plotKey)
                    wrappedThumbB64 = Base64.encodeToString(rewrappedThumb, Base64.NO_WRAP)
                    thumbDekFormat = VaultCrypto.ALG_PLOT_AES256GCM_V1
                }

                // 5. Call the server.
                api.addPlotItem(
                    plotId = plotId,
                    uploadId = upload.id,
                    wrappedItemDek = wrappedDekB64,
                    itemDekFormat = VaultCrypto.ALG_PLOT_AES256GCM_V1,
                    wrappedThumbnailDek = wrappedThumbB64,
                    thumbnailDekFormat = thumbDekFormat,
                )
                _addToPlotResult.value = AddToPlotResult.Success
            }.onFailure { e ->
                val msg = e.message ?: "Couldn't add to plot"
                // 409 Conflict → already in plot
                if (msg.contains("409")) {
                    _addToPlotResult.value = AddToPlotResult.AlreadyPresent
                } else {
                    _addToPlotResult.value = AddToPlotResult.Error(msg)
                }
            }
        }
    }
}
