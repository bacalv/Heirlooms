package digital.heirlooms.ui.brand

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has requested reduced animations system-wide.
 *
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE` — the closest Android
 * equivalent to CSS `prefers-reduced-motion: reduce`. Treats exactly-zero
 * as "off". Users who scale down to 0.5x still want some motion; only zero
 * counts as disabled.
 *
 * Cached for the lifetime of the calling Composable's owning context. If the
 * user changes the setting at runtime the change won't be reflected until the
 * Composable is recreated — that's acceptable; this setting is rarely toggled
 * mid-session.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
