package com.chrischappelear.tuner.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.tuning.TracePoint
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType

private const val TRACE_RANGE = 50f

/**
 * The last few seconds of tuning as a line drifting in toward center. The middle
 * is in tune; older readings fade like a comet's tail. Gaps are silence.
 * Redraws on every display frame so it keeps scrolling when nothing is sounding.
 */
@Composable
fun CentsTrace(
    points: List<TracePoint>,
    modifier: Modifier = Modifier,
    durationMs: Long = 8_000L,
    height: Dp = 112.dp
) {
    val colors = TunerTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.inkMuted, fontFeatureSettings = "tnum")
    val markStyle = TextStyle(fontSize = 12.sp, color = colors.inkFaint)

    val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { clock.longValue = SystemClock.elapsedRealtime() }
    }

    Column(
        modifier
            .background(colors.surface.copy(alpha = if (colors.isDark) 0.55f else 0.7f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row {
            Text(stringResource(R.string.trace_title), style = TunerType.label, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.trace_window, durationMs / 1000),
                style = TunerType.label,
                color = colors.inkFaint
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(height)
        ) {
            val now = clock.longValue
            val w = size.width
            val pad = 6.dp.toPx()
            val half = size.height / 2 - pad
            fun y(cents: Float) = size.height / 2 - cents.coerceIn(-TRACE_RANGE, TRACE_RANGE) / TRACE_RANGE * half
            fun x(time: Long) = w - (now - time).toFloat() / durationMs * w

            // In-tune band and center line.
            val bandTop = y(5f)
            drawRect(
                color = colors.inTune.copy(alpha = 0.10f),
                topLeft = Offset(0f, bandTop),
                size = Size(w, y(-5f) - bandTop)
            )
            drawLine(colors.inTune.copy(alpha = 0.4f), Offset(0f, y(0f)), Offset(w, y(0f)), 1.dp.toPx())
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
            for (c in listOf(-25f, 25f)) {
                drawLine(colors.inkFaint.copy(alpha = 0.5f), Offset(0f, y(c)), Offset(w, y(c)), 1.dp.toPx(), pathEffect = dash)
            }

            val stroke = 2.5.dp.toPx()
            var last: TracePoint? = null
            for (point in points) {
                val previous = last
                last = point
                val cents = point.cents ?: continue
                val px = x(point.timeMs)
                if (px < -stroke) continue
                val fade = (0.12f + 0.88f * (px / w)).coerceIn(0f, 1f)

                // Break the line at silence and at note changes; a jump to a new target isn't drift.
                if (previous?.cents != null && previous.note == point.note) {
                    val prevCents = previous.cents
                    drawLine(
                        color = colors.forCents((cents + prevCents) / 2).copy(alpha = fade),
                        start = Offset(x(previous.timeMs), y(prevCents)),
                        end = Offset(px, y(cents)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                // Label the start of each note.
                val note = point.note
                if (note != null && previous?.note != note) {
                    val layout = measurer.measure(note.displayName, labelStyle)
                    val ly = (y(cents) - layout.size.height - 4.dp.toPx())
                        .coerceIn(0f, size.height - layout.size.height)
                    drawText(layout, topLeft = Offset(px + 4.dp.toPx(), ly), alpha = fade)
                }
            }

            // Leading dot at the newest reading.
            points.lastOrNull()?.let { point ->
                val cents = point.cents ?: return@let
                drawCircle(colors.forCents(cents), radius = 3.5.dp.toPx(), center = Offset(x(point.timeMs), y(cents)))
            }

            val sharp = measurer.measure("♯", markStyle)
            drawText(sharp, topLeft = Offset(0f, 0f))
            val flat = measurer.measure("♭", markStyle)
            drawText(flat, topLeft = Offset(0f, size.height - flat.size.height))
        }
    }
}
