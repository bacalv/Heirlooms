package digital.heirlooms.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The serif italic style used for the wordmark and brand voice moments.
 * Use sparingly — every appearance is a moment of voice, not a label.
 * Resolves to Noto Serif Italic on most Android devices (Georgia-equivalent).
 * Will be replaced with the custom-drawn Heirlooms wordmark in a future milestone.
 */
val HeirloomsSerifItalic = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
)

val HeirloomsTypography = Typography(
    // Wordmark and brand voice — serif italic
    headlineLarge  = HeirloomsSerifItalic.copy(fontSize = 28.sp),
    headlineMedium = HeirloomsSerifItalic.copy(fontSize = 22.sp),
    headlineSmall  = HeirloomsSerifItalic.copy(fontSize = 18.sp),

    // Body and labels — system sans
    bodyLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium  = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall   = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),

    labelLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall  = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
