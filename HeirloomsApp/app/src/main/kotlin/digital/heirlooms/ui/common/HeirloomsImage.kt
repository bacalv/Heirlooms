package digital.heirlooms.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage

// Provided by MainNavigation with an OkHttp client that adds the X-Api-Key header.
val LocalImageLoader = compositionLocalOf<ImageLoader> { error("No ImageLoader provided") }

@Composable
fun HeirloomsImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
) {
    val imageLoader = LocalImageLoader.current
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}
