package digital.heirlooms.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader
import coil3.compose.AsyncImage
import digital.heirlooms.api.Upload
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import digital.heirlooms.ui.brand.OliveBranchIcon
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalImageLoader = compositionLocalOf<ImageLoader> { error("No ImageLoader provided") }

@Composable
fun HeirloomsImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    rotation: Int = 0,
) {
    val imageLoader = LocalImageLoader.current
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier.rotated(rotation),
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}

// Thumbnail composable that transparently handles both encrypted and plaintext uploads.
@Composable
fun UploadThumbnail(
    upload: Upload,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    rotation: Int = 0,
) {
    if (upload.isEncrypted) {
        EncryptedThumbnail(
            upload = upload,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            rotation = rotation,
        )
    } else {
        val api = LocalHeirloomsApi.current
        HeirloomsImage(
            url = api.thumbUrl(upload.id),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            rotation = rotation,
        )
    }
}

@Composable
private fun EncryptedThumbnail(
    upload: Upload,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    rotation: Int = 0,
) {
    val api = LocalHeirloomsApi.current
    var imageBitmap by remember(upload.id) { mutableStateOf<ImageBitmap?>(VaultSession.thumbnailCache[upload.id]) }
    var failed by remember(upload.id) { mutableStateOf(false) }

    LaunchedEffect(upload.id) {
        if (imageBitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val wrappedDek = upload.wrappedThumbnailDek
                if (wrappedDek == null) { failed = true; return@withContext }
                val mk = VaultSession.masterKey
                val dek = VaultCrypto.unwrapDekWithMasterKey(wrappedDek, mk)
                val encryptedBytes = api.fetchBytes(api.thumbUrl(upload.id))
                val decryptedBytes = VaultCrypto.decryptSymmetric(encryptedBytes, dek)
                val bmp = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                val ib = bmp?.asImageBitmap()
                if (ib != null) {
                    VaultSession.thumbnailCache[upload.id] = ib
                    imageBitmap = ib
                } else {
                    failed = true
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    when {
        imageBitmap != null -> Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier.rotated(rotation),
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
        failed -> Box(modifier.background(Forest08), contentAlignment = Alignment.Center) {
            OliveBranchIcon(Modifier.size(24.dp))
        }
        else -> Box(modifier.background(Forest08), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

private fun Modifier.rotated(rotation: Int): Modifier =
    if (rotation != 0) this.graphicsLayer { rotationZ = rotation.toFloat() } else this
