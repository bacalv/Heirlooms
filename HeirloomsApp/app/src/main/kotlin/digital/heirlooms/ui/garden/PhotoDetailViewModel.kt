package digital.heirlooms.ui.garden

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
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
                val refs = api.getCapsulesForUpload(uploadId)
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
                val dek = VaultCrypto.unwrapDekWithMasterKey(wrappedDek, mk)

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
                val dek = VaultCrypto.unwrapDekWithMasterKey(wrappedDek, mk)
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
                val refs = api.getCapsulesForUpload(uploadId)
                _state.value = PhotoDetailState.Ready(upload, refs)
            } catch (_: Exception) {}
        }
    }
}
