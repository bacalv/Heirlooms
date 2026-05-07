package digital.heirlooms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HeirloomsColorScheme = lightColorScheme(
    primary          = Forest,
    onPrimary        = Parchment,
    secondary        = NewLeaf,
    onSecondary      = Forest,
    tertiary         = Bloom,
    onTertiary       = Forest,
    background       = Parchment,
    onBackground     = TextBody,
    surface          = Parchment,
    onSurface        = TextBody,
    surfaceVariant   = Forest08,
    onSurfaceVariant = TextMuted,
    error            = Earth,
    onError          = Parchment,
)

/**
 * The Heirlooms brand theme. Wrap any Composable hierarchy that should
 * render with brand styling.
 *
 * No dark-mode variant — Heirlooms renders in parchment/forest regardless
 * of system preference. This is deliberate; see docs/BRAND.md for rationale.
 */
@Composable
fun HeirloomsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeirloomsColorScheme,
        typography  = HeirloomsTypography,
        content     = content,
    )
}
