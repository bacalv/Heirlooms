package digital.heirlooms.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest

private const val DURATION_MS = 2000

/**
 * The didn't-take animation: partial branch with one leaf pair, a pause beat,
 * earth-coloured seed appears on the soil line, "didn't take" text fades in.
 * Honours `rememberReducedMotion()` by snapping to end state.
 *
 * Shares `LeafPair1`, `drawBranch`, `drawLeafPair`, and `lerp01` from
 * `OliveBranchArrival.kt` (same package, same module — `internal` visibility).
 *
 * @param onComplete Called once when the animation finishes.
 * @param reduceMotion Override for tests.
 */
@Composable
fun OliveBranchDidntTake(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            progress.snapTo(1f)
            onComplete()
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = DURATION_MS, easing = LinearEasing),
            )
            onComplete()
        }
    }

    Box(modifier = modifier.aspectRatio(140f / 200f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = progress.value
            val s = size.width / 140f
            drawSoilLine(s)
            drawBranch(t, s, DidntTakeBranchPath, branchPhaseEnd = 0.25f)
            drawLeafPair(t, 0.20f, 0.45f, LeafPair1, s)
            drawFallenSeed(t, s)
        }
        Text(
            text = "didn't take",
            style = DidntTakeTextStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .alpha(lerp01(progress.value, 0.75f, 1.0f)),
        )
    }
}

private val DidntTakeTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    color = Earth,
)

private fun DrawScope.drawSoilLine(s: Float) {
    drawLine(
        color = Forest.copy(alpha = 0.3f),
        start = Offset(20f * s, 142f * s),
        end = Offset(120f * s, 142f * s),
        strokeWidth = 0.4f * s,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * s, 2f * s)),
    )
}

private fun DrawScope.drawFallenSeed(t: Float, s: Float) {
    val sp = lerp01(t, 0.55f, 0.80f)
    if (sp <= 0f) return
    val rx = 3f * sp
    val ry = 2f * sp
    val pivot = Offset(80f * s, 148f * s)
    withTransform({ rotate(degrees = 15f, pivot = pivot) }) {
        drawOval(
            color = Earth,
            topLeft = Offset((80f - rx) * s, (148f - ry) * s),
            size = Size(rx * 2f * s, ry * 2f * s),
        )
    }
}
