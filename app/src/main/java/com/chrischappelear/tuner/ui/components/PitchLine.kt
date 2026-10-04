package com.chrischappelear.tuner.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TracePoint
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

private val ROW_HEIGHT = 22.dp
private val KEY_WIDTH = 34.dp
private const val FOLLOW_MS = 450f
private const val GAP_MS = 250L
private const val TICK_MS = 5_000L
private const val LEAP_SEMITONES = 4f
private const val SPAN_TRIM = 0.05f

/** Where the view is centred, in MIDI; eased toward the singing every frame. */
private class Camera {
    var center = Float.NaN
    var lastFrameMs = 0L
}

/**
 * Half a minute of pitch on a piano roll: a slim keyboard down the side, a row for
 * every semitone, and the line of what's sung or played gliding across them, sage
 * where it sits on a note and warming to amber between. The key under the voice lights
 * as if pressed. The drone's note glows in every octave, so you can see what you hear.
 *
 * The view follows the voice, keeping all of the last half minute in sight when it fits.
 */
@Composable
fun PitchLine(
    points: List<TracePoint>,
    drone: Note?,
    modifier: Modifier = Modifier,
    flats: Boolean = false,
    durationMs: Long = 30_000L
) {
    val colors = TunerTheme.colors
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val keyLabel = TextStyle(fontSize = 9.sp, letterSpacing = 0.3.sp, fontFeatureSettings = "tnum")
    val camera = remember { Camera() }

    val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { clock.longValue = SystemClock.elapsedRealtime() }
    }

    val whiteKey = if (colors.isDark) colors.ink.copy(alpha = 0.09f) else colors.backgroundGlow.copy(alpha = 0.85f)
    val blackKey = if (colors.isDark) colors.background.copy(alpha = 0.85f) else colors.inkMuted.copy(alpha = 0.32f)

    Column(
        modifier
            .background(colors.surface.copy(alpha = if (colors.isDark) 0.55f else 0.7f), RoundedCornerShape(20.dp))
            .padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Row(Modifier.padding(start = 6.dp)) {
            Text(stringResource(R.string.pitch_title), style = TunerType.label, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.trace_window, durationMs / 1000),
                style = TunerType.label,
                color = colors.inkFaint
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .clipToBounds()
        ) {
            val now = clock.longValue
            val rowHeight = ROW_HEIGHT.toPx()
            val keyWidth = KEY_WIDTH.toPx()
            val plotLeft = keyWidth + 8.dp.toPx()
            val plotWidth = size.width - plotLeft
            val rows = size.height / rowHeight

            // Follow the singing: the span of the last half minute if it fits, leaving out
            // the odd stray reading, with the newest always well inside the edges.
            val heard = points.mapNotNull { point -> point.midi?.takeIf { now - point.timeMs <= durationMs } }.sorted()
            val newest = points.lastOrNull()?.midi
            val target = when {
                heard.isEmpty() -> camera.center.takeUnless { it.isNaN() } ?: drone?.midi?.toFloat() ?: 60f
                else -> {
                    val low = heard[(heard.size * SPAN_TRIM).toInt()]
                    val high = heard[(heard.size * (1 - SPAN_TRIM)).toInt().coerceAtMost(heard.lastIndex)]
                    val margin = rows / 2 - 2.5f
                    val middle = (low + high) / 2
                    if (newest == null) middle else middle.coerceIn(newest - margin, newest + margin)
                }
            }
            val dt = (now - camera.lastFrameMs).coerceIn(0L, 100L)
            camera.lastFrameMs = now
            camera.center = if (camera.center.isNaN()) target
            else camera.center + (target - camera.center) * (1 - exp(-dt / FOLLOW_MS))
            val center = camera.center

            fun y(midi: Float) = size.height / 2 - (midi - center) * rowHeight
            fun x(time: Long) = plotLeft + (1 - (now - time).toFloat() / durationMs) * plotWidth

            val sung = newest?.roundToInt()
            val sungTint = newest?.let { colors.forCents((it - it.roundToInt()) * 100) }

            // Rows, and the keyboard down the side.
            val first = floor(center - rows / 2).toInt() - 1
            val last = ceil(center + rows / 2).toInt() + 1
            for (midi in first..last) {
                val spelling = Note(midi).spelled(flats)
                val natural = spelling.accidental.isEmpty()
                val home = drone != null && Math.floorMod(midi - drone.midi, 12) == 0
                val mid = y(midi.toFloat())
                val top = mid - rowHeight / 2

                if (home) {
                    drawRect(colors.inTune.copy(alpha = 0.10f), Offset(plotLeft, top), Size(plotWidth, rowHeight))
                }
                if (midi == sung) {
                    drawRect(colors.ink.copy(alpha = 0.035f), Offset(plotLeft, top), Size(plotWidth, rowHeight))
                }
                drawLine(
                    color = when {
                        home -> colors.inTune.copy(alpha = 0.55f)
                        natural -> colors.inkFaint.copy(alpha = 0.40f)
                        else -> colors.inkFaint.copy(alpha = 0.16f)
                    },
                    start = Offset(plotLeft, mid),
                    end = Offset(size.width, mid),
                    strokeWidth = 1.dp.toPx()
                )

                val pressed = midi == sung && sungTint != null
                val keyColor = when {
                    pressed -> sungTint!!.copy(alpha = if (natural) 0.55f else 0.8f)
                    natural -> whiteKey
                    else -> blackKey
                }
                drawRect(
                    color = keyColor,
                    topLeft = Offset(0f, top + 0.5.dp.toPx()),
                    size = Size(if (natural) keyWidth else keyWidth * 0.6f, rowHeight - 1.dp.toPx())
                )
                if (natural) {
                    val label = if (spelling.letter == "C") spelling.displayName else spelling.letter
                    val layout = measurer.measure(
                        label,
                        keyLabel.copy(
                            color = when {
                                pressed -> colors.ink
                                home -> colors.inTune
                                else -> colors.inkMuted
                            },
                            fontWeight = if (home || pressed) FontWeight.Medium else FontWeight.Normal
                        )
                    )
                    drawText(
                        layout,
                        topLeft = Offset(keyWidth - layout.size.width - 3.dp.toPx(), mid - layout.size.height / 2)
                    )
                }
            }

            clipRect(left = plotLeft) {
                // Faint time marks every five seconds drift by, so stillness still moves.
                val dotted = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))
                var tick = now - Math.floorMod(now, TICK_MS)
                while (now - tick <= durationMs) {
                    val tx = x(tick)
                    drawLine(
                        colors.inkFaint.copy(alpha = 0.22f),
                        Offset(tx, 0f), Offset(tx, size.height), 1.dp.toPx(), pathEffect = dotted
                    )
                    tick -= TICK_MS
                }

                val stroke = 2.5.dp.toPx()
                var previous: TracePoint? = null
                for (point in points) {
                    val before = previous
                    previous = point
                    val midi = point.midi ?: continue
                    val prior = before?.midi ?: continue
                    // A leap isn't a glide: draw a jump as a jump, and a misreading as a speck.
                    if (point.timeMs - before.timeMs > GAP_MS || abs(midi - prior) > LEAP_SEMITONES) continue
                    val px = x(point.timeMs)
                    if (px < plotLeft - stroke) continue
                    val between = (midi + prior) / 2
                    val fade = (0.12f + 0.88f * (px - plotLeft) / plotWidth).coerceIn(0f, 1f)
                    drawLine(
                        color = colors.forCents((between - between.roundToInt()) * 100).copy(alpha = fade),
                        start = Offset(x(before.timeMs), y(prior)),
                        end = Offset(px, y(midi)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                // The voice now: a dot with a soft halo of its own colour.
                val head = points.lastOrNull()
                val headMidi = head?.midi
                if (head != null && headMidi != null) {
                    val tint = colors.forCents((headMidi - headMidi.roundToInt()) * 100)
                    val at = Offset(x(head.timeMs), y(headMidi))
                    val halo = 16.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), at, halo),
                        radius = halo,
                        center = at
                    )
                    drawCircle(tint, radius = 4.dp.toPx(), center = at)
                }
            }
        }
    }
}
