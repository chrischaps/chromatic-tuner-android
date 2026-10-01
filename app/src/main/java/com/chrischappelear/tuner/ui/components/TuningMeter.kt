package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.ui.theme.TunerTheme
import kotlin.math.cos
import kotlin.math.sin

/** Degrees of arc per cent; ±50 cents spans ±[HALF_SWEEP] degrees. */
private const val HALF_SWEEP = 50f
private const val RANGE_CENTS = 50f

/**
 * Arc gauge for ±50 cents, echoing the arc over the logo's letters.
 *
 * The arc's center sits below this composable's bottom edge, so the note can be
 * laid out directly beneath it the way the logo's letters sit under its arc.
 * Height should be about `36.dp + 0.36 * radius`; see [meterHeightFor].
 *
 * @param cents reading to show, or null while idle (the pip then breathes at center)
 * @param dimmed true while a note is fading out
 */
@Composable
fun TuningMeter(
    cents: Float?,
    locked: Boolean,
    dimmed: Boolean,
    radius: Dp,
    modifier: Modifier = Modifier
) {
    val colors = TunerTheme.colors
    val target = (cents ?: 0f).coerceIn(-RANGE_CENTS * 1.04f, RANGE_CENTS * 1.04f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "needle"
    )
    val pipColor by animateColorAsState(
        targetValue = if (cents == null) colors.inkFaint else colors.forCents(animated),
        label = "pipColor"
    )
    val presence by animateFloatAsState(
        targetValue = when {
            cents == null -> 0f
            dimmed -> 0.45f
            else -> 1f
        },
        animationSpec = tween(400),
        label = "presence"
    )
    val lockGlow by animateFloatAsState(if (locked) 1f else 0f, tween(500), label = "lockGlow")
    val breath by rememberInfiniteTransition(label = "idle").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breath"
    )

    val measurer = rememberTextMeasurer()
    val markStyle = TextStyle(color = colors.inkMuted, fontSize = 18.sp)

    Canvas(modifier) {
        val r = radius.toPx()
        val center = Offset(size.width / 2, size.height - 8.dp.toPx() + 0.643f * r)

        drawTrack(center, r, colors.track, colors.inkFaint, colors.inTune, lockGlow)

        // Lit segment from center to the reading.
        if (presence > 0f && kotlin.math.abs(animated) > 0.3f) {
            drawArc(
                color = pipColor.copy(alpha = 0.55f * presence),
                startAngle = 270f,
                sweepAngle = animated.coerceIn(-RANGE_CENTS, RANGE_CENTS) / RANGE_CENTS * HALF_SWEEP,
                useCenter = false,
                topLeft = center - Offset(r, r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // The pip, with a halo.
        val angle = animated.coerceIn(-RANGE_CENTS, RANGE_CENTS) / RANGE_CENTS * HALF_SWEEP
        val pip = pointOnArc(center, r, angle)
        val alpha = if (cents == null) breath else presence
        val haloRadius = (16 + 10 * lockGlow).dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                listOf(pipColor.copy(alpha = 0.45f * alpha), Color.Transparent),
                center = pip,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = pip
        )
        drawCircle(pipColor.copy(alpha = alpha), radius = 6.dp.toPx(), center = pip)

        // ♭ and ♯ at the ends.
        for ((mark, side) in listOf("♭" to -1f, "♯" to 1f)) {
            val layout = measurer.measure(mark, markStyle)
            val at = pointOnArc(center, r + 4.dp.toPx(), side * (HALF_SWEEP + 7f))
            drawText(
                layout,
                topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f)
            )
        }
    }
}

/** Height that fits the arc, its ticks and the ♭/♯ marks for a given [radius]. */
fun meterHeightFor(radius: Dp): Dp = 36.dp + radius * 0.36f

private fun pointOnArc(center: Offset, radius: Float, degreesFromTop: Float): Offset {
    val rad = Math.toRadians(degreesFromTop.toDouble())
    return Offset(center.x + radius * sin(rad).toFloat(), center.y - radius * cos(rad).toFloat())
}

private fun DrawScope.drawTrack(
    center: Offset,
    r: Float,
    track: Color,
    tick: Color,
    inTune: Color,
    lockGlow: Float
) {
    drawArc(
        color = track,
        startAngle = 270f - HALF_SWEEP,
        sweepAngle = HALF_SWEEP * 2,
        useCenter = false,
        topLeft = center - Offset(r, r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    // The in-tune window, ±5 cents, brightening on lock.
    drawArc(
        color = inTune.copy(alpha = 0.28f + 0.5f * lockGlow),
        startAngle = 270f - 5f,
        sweepAngle = 10f,
        useCenter = false,
        topLeft = center - Offset(r, r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    )

    val gap = 7.dp.toPx()
    for (c in -50..50 step 5) {
        val length = when {
            c == 0 -> 16.dp.toPx()
            c % 10 == 0 -> 9.dp.toPx()
            else -> 4.dp.toPx()
        }
        val angle = c / RANGE_CENTS * HALF_SWEEP
        drawLine(
            color = if (c == 0) inTune else tick,
            start = pointOnArc(center, r + gap, angle),
            end = pointOnArc(center, r + gap + length, angle),
            strokeWidth = (if (c == 0) 2.dp else 1.dp).toPx(),
            cap = StrokeCap.Round
        )
    }
}
