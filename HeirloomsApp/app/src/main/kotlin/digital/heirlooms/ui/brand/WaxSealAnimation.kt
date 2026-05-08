package digital.heirlooms.ui.brand

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import digital.heirlooms.ui.theme.Bloom

// ease-out cubic: matches web's sealing animation timing curve
private val SealEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val SEAL_DURATION_MS = 700

/**
 * Wraps a [WaxSealOlive] that scales in from 0→1 over 700ms when [sealed] flips to true.
 * Honours reduced-motion: snaps to end state immediately.
 */
@Composable
fun AnimatedWaxSeal(
    sealed: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    val scale by animateFloatAsState(
        targetValue = if (sealed) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(SEAL_DURATION_MS, easing = SealEasing),
        label = "sealScale",
    )

    WaxSealOlive(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        color = Bloom,
    )
}
