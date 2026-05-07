package digital.heirlooms.ui.brand

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.TextMuted

enum class WorkingDotsSize(val dotDp: Int, val gapDp: Int) {
    Small(4, 3),
    Medium(6, 4),
    Large(8, 5),
}

/**
 * The brand's universal "working" indicator. Three pulsing forest dots,
 * 1.4s pulse, 0.2s stagger per dot.
 *
 * Honours the system's animator-duration-scale preference: when the user
 * has animations disabled (scale == 0), dots render at static muted opacity
 * instead of pulsing — matching the web `prefers-reduced-motion` behaviour.
 *
 * @param label Optional voice label below the dots. Should use lowercase
 *   brand copy like "uploading…", not a system-style label.
 */
@Composable
fun WorkingDots(
    modifier: Modifier = Modifier,
    label: String? = null,
    size: WorkingDotsSize = WorkingDotsSize.Medium,
) {
    val context = LocalContext.current
    val animatorScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    val reduceMotion = animatorScale == 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size.gapDp.dp)) {
            repeat(3) { index ->
                // Always create the transition so the composable call count is
                // consistent across recompositions (Rules of Compose).
                val transition = rememberInfiniteTransition(label = "wd_$index")
                val animatedAlpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offsetMillis = index * 200),
                    ),
                    label = "wd_alpha_$index",
                )
                val alpha = if (reduceMotion) 0.6f else animatedAlpha

                Box(
                    modifier = Modifier
                        .size(size.dotDp.dp)
                        .clip(CircleShape)
                        .background(Forest.copy(alpha = alpha))
                        .semantics { contentDescription = "loading dot" },
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = HeirloomsSerifItalic.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                ),
                color = TextMuted,
            )
        }
    }
}
