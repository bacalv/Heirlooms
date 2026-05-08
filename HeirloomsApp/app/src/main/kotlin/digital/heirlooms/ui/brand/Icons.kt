package digital.heirlooms.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Forest

/**
 * Garden bottom-nav icon: simplified olive branch (stem + three leaf pairs, no apex olive).
 * Rendered at 24dp with Forest stroke. The full brand mark's apex olive is omitted so the
 * icon reads cleanly at navigation-bar scale and is visually distinct from the Capsules icon.
 */
@Composable
fun OliveBranchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        val strokeW = 1.5f * s

        // Stem: gentle S-curve from bottom to top
        drawPath(
            path = Path().apply {
                moveTo(12f * s, 22f * s)
                cubicTo(12.8f * s, 17f * s, 11.2f * s, 13f * s, 12f * s, 4f * s)
            },
            color = Forest,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )

        // Leaf pair 1 — bottom
        drawLeafPair(s, cx = 12f, cy = 17f, rx = 4f, ry = 1.4f, angle = 20f)
        // Leaf pair 2 — middle
        drawLeafPair(s, cx = 12f, cy = 12f, rx = 3.5f, ry = 1.2f, angle = 25f)
        // Leaf pair 3 — upper
        drawLeafPair(s, cx = 12f, cy = 8f, rx = 3f, ry = 1.0f, angle = 30f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeafPair(
    s: Float, cx: Float, cy: Float, rx: Float, ry: Float, angle: Float,
) {
    // Left leaf
    withTransform({
        rotate(degrees = -angle, pivot = Offset(cx * s, cy * s))
    }) {
        drawOval(
            color = Forest,
            topLeft = Offset((cx - rx - rx) * s, (cy - ry) * s),
            size = Size(rx * 2 * s, ry * 2 * s),
        )
    }
    // Right leaf
    withTransform({
        rotate(degrees = angle, pivot = Offset(cx * s, cy * s))
    }) {
        drawOval(
            color = Forest,
            topLeft = Offset(cx * s, (cy - ry) * s),
            size = Size(rx * 2 * s, ry * 2 * s),
        )
    }
}

/**
 * Capsules bottom-nav icon: wax-seal olive — a single bloom-coloured ovoid,
 * slightly tapered at the top. Matches the sealed-capsule mark from BRAND.md.
 * Rendered at 24dp.
 */
@Composable
fun WaxSealOliveIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        // Ovoid: 8dp wide, 14dp tall, centred — slightly narrower at top via Bezier path
        val cx = 12f * s
        val cy = 12f * s
        val rx = 4f * s
        val ryTop = 6.5f * s
        val ryBottom = 7f * s
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - ryTop)
                cubicTo(cx + rx, cy - ryTop, cx + rx, cy + ryBottom, cx, cy + ryBottom)
                cubicTo(cx - rx, cy + ryBottom, cx - rx, cy - ryTop, cx, cy - ryTop)
                close()
            },
            color = Bloom,
        )
    }
}

/**
 * Large-format wax-seal olive for capsule detail view.
 * At [size] dp. Accepts [color] to support ceremonial-size (Bloom) and
 * backdrop-size (Bloom at 25% alpha) variants.
 */
@Composable
fun WaxSealOlive(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Bloom,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.38f
        val ryTop = size.height * 0.45f
        val ryBottom = size.height * 0.48f
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - ryTop)
                cubicTo(cx + rx, cy - ryTop, cx + rx, cy + ryBottom, cx, cy + ryBottom)
                cubicTo(cx - rx, cy + ryBottom, cx - rx, cy - ryTop, cx, cy - ryTop)
                close()
            },
            color = color,
        )
    }
}
