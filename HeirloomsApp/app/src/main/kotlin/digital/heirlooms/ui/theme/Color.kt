package digital.heirlooms.ui.theme

import androidx.compose.ui.graphics.Color

// Heirlooms brand palette — must match docs/BRAND.md and res/values/colors.xml
val Parchment = Color(0xFFF2EEDF)
val Forest    = Color(0xFF3F4F33)
val Bloom     = Color(0xFFD89B85)
val Earth     = Color(0xFFB5694B)
val NewLeaf   = Color(0xFF7DA363)
val Ink       = Color(0xFF2C2A26)

// Forest tints — use these instead of Forest.copy(alpha = ...) at call sites
// to keep the surface palette enumerable.
val Forest04 = Forest.copy(alpha = 0.04f)
val Forest08 = Forest.copy(alpha = 0.08f)
val Forest15 = Forest.copy(alpha = 0.15f)
val Forest25 = Forest.copy(alpha = 0.25f)

// Text shades.
val TextPrimary = Ink
val TextBody    = Color(0xFF5A5A52)
val TextMuted   = Color(0xFF6B7559)
