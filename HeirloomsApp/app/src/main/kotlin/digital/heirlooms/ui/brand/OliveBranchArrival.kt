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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Forest

private const val DURATION_MS = 3000

/**
 * The arrival animation: branch draws, leaves emerge in pairs base-to-tip,
 * olive forms in forest then ripens to bloom, optional wordmark settles.
 * Honours `rememberReducedMotion()` by snapping to end state and firing
 * `onComplete` on the next composition.
 *
 * Uses `LinearEasing` — the phase timing table assumes constant-rate progress;
 * non-linear easing would shift the visual beats relative to the phase ranges.
 *
 * @param withWordmark Fades in the "Heirlooms" wordmark in the final phase.
 *   Set false inside the receive screen where the header already shows it.
 * @param onComplete Called once when the animation finishes (or immediately
 *   under reduced motion).
 * @param reduceMotion Override the system-derived check — production code
 *   uses the default; tests pass `true` to exercise the fast-path.
 */
@Composable
fun OliveBranchArrival(
    modifier: Modifier = Modifier,
    withWordmark: Boolean = true,
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
            drawBranch(t, s, ArrivalBranchPath, branchPhaseEnd = 0.30f)
            drawLeafPair(t, 0.22f, 0.42f, LeafPair1, s)
            drawLeafPair(t, 0.38f, 0.58f, LeafPair2, s)
            drawLeafPair(t, 0.54f, 0.72f, LeafPair3, s)
            drawOliveArrival(t, s)
        }
        if (withWordmark) {
            Text(
                text = "Heirlooms",
                style = WordmarkStyle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .alpha(lerp01(progress.value, 0.88f, 1.0f)),
            )
        }
    }
}

private val WordmarkStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    color = Forest,
)

private fun DrawScope.drawOliveArrival(t: Float, s: Float) {
    val rxMul: Float
    val color: Color
    when {
        t < 0.70f -> { rxMul = 0f; color = Forest }
        t < 0.84f -> { rxMul = lerp01(t, 0.70f, 0.84f); color = Forest }
        t < 0.92f -> { rxMul = 1f; color = lerp(Forest, Bloom, lerp01(t, 0.84f, 0.92f)) }
        else      -> { rxMul = 1f; color = Bloom }
    }
    if (rxMul <= 0f) return
    val rx = 3.5f * rxMul
    val ry = 5.5f * rxMul
    drawOval(
        color = color,
        topLeft = Offset((70f - rx) * s, (55f - ry) * s),
        size = Size(rx * 2f * s, ry * 2f * s),
    )
}

// ── Shared helpers used by both Arrival and UploadFailed animations ──────────────────────

internal data class LeafSpec(
    val cx: Float, val cy: Float,
    val rx: Float, val ry: Float,
    val rotationDeg: Float,
    val pivotX: Float, val pivotY: Float,
)

internal val LeafPair1 = listOf(
    LeafSpec(56f, 124f, 9.5f, 2.4f,  18f, 67f, 122f),
    LeafSpec(84f, 124f, 9.5f, 2.4f, -18f, 73f, 122f),
)
internal val LeafPair2 = listOf(
    LeafSpec(55f, 103f, 8.5f, 2.2f,  22f, 64f, 101f),
    LeafSpec(85f, 103f, 8.5f, 2.2f, -22f, 76f, 101f),
)
internal val LeafPair3 = listOf(
    LeafSpec(58f,  82f, 7f,   1.9f,  26f, 65f,  80f),
    LeafSpec(82f,  82f, 7f,   1.9f, -26f, 75f,  80f),
)

internal data class BranchPathSpec(
    val startX: Float, val startY: Float,
    val c1X: Float, val c1Y: Float,
    val c2X: Float, val c2Y: Float,
    val endX: Float, val endY: Float,
)

internal val ArrivalBranchPath = BranchPathSpec(
    startX = 70f, startY = 140f,
    c1X = 73f, c1Y = 118f,
    c2X = 67f, c2Y =  88f,
    endX = 70f, endY =  58f,
)

internal val UploadFailedBranchPath = BranchPathSpec(
    startX = 70f, startY = 140f,
    c1X = 73f, c1Y = 128f,
    c2X = 69f, c2Y = 118f,
    endX = 71f, endY = 110f,
)

internal fun DrawScope.drawBranch(
    t: Float,
    s: Float,
    spec: BranchPathSpec,
    branchPhaseEnd: Float,
) {
    val progress = lerp01(t, 0f, branchPhaseEnd)
    if (progress <= 0f) return
    val full = Path().apply {
        moveTo(spec.startX * s, spec.startY * s)
        cubicTo(
            spec.c1X * s, spec.c1Y * s,
            spec.c2X * s, spec.c2Y * s,
            spec.endX * s, spec.endY * s,
        )
    }
    val measure = PathMeasure().apply { setPath(full, false) }
    val visible = Path()
    measure.getSegment(0f, measure.length * progress, visible, true)
    drawPath(
        path = visible,
        color = Forest,
        style = Stroke(width = 1.7f * s, cap = StrokeCap.Round),
    )
}

internal fun DrawScope.drawLeafPair(
    t: Float,
    rangeStart: Float,
    rangeEnd: Float,
    pair: List<LeafSpec>,
    s: Float,
) {
    val p = lerp01(t, rangeStart, rangeEnd)
    if (p <= 0f) return
    pair.forEach { leaf ->
        val pivot = Offset(leaf.pivotX * s, leaf.pivotY * s)
        withTransform({
            rotate(degrees = leaf.rotationDeg, pivot = pivot)
            scale(scaleX = p, scaleY = p, pivot = pivot)
        }) {
            drawOval(
                color = Forest.copy(alpha = p),
                topLeft = Offset((leaf.cx - leaf.rx) * s, (leaf.cy - leaf.ry) * s),
                size = Size(leaf.rx * 2f * s, leaf.ry * 2f * s),
            )
        }
    }
}

/** Linear ramp 0→1 over [start, end], clamped to [0, 1] outside. */
internal fun lerp01(t: Float, start: Float, end: Float): Float {
    if (t <= start) return 0f
    if (t >= end) return 1f
    return (t - start) / (end - start)
}
