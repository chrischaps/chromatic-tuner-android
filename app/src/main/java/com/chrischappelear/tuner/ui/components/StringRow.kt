package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.audio.PluckVoice
import com.chrischappelear.tuner.audio.ReferenceTone
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.TuningString
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import kotlin.math.exp
import kotlin.math.ln

private val PILL_SIZE = 46.dp
private val RIPPLE_SPREAD = 16.dp
private const val RIPPLES = 3
private val TONE_MS = (PluckVoice.DURATION_SECONDS * 1000).toInt()
private const val DRONE_RIPPLE_MS = 2_400

/** E1 to C6: low enough for a bass, high enough for a violin's E string and beyond. */
val REFERENCE_RANGE = 28..84

/**
 * One pill per string. The one being played lights up; each string that has come
 * into tune keeps a small sage mark, so the whole instrument fills in as you go.
 * Tapping a pill plucks that string's reference tone, which ripples outward while it
 * rings. Pills shrink to fit when an instrument has more strings than the row holds.
 */
@Composable
fun StringRow(
    state: TunerState,
    reference: ReferenceTone?,
    onPluck: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = state.tuning.strings
    if (strings.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = if (strings.size > 6) 6.dp else 10.dp
        val size = min(PILL_SIZE, (maxWidth - gap * (strings.size - 1)) / strings.size)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
        ) {
            strings.forEachIndexed { index, string ->
                StringPill(
                    string = string,
                    flats = state.tuning.flats,
                    size = size,
                    active = state.status == TunerStatus.Active && state.stringIndex == index,
                    tuned = index in state.tunedStrings,
                    tint = if (state.locked) TunerTheme.colors.inTune
                    else TunerTheme.colors.forCents(state.cents.toFloat()),
                    ringing = reference?.takeIf { it.stringIndex == index },
                    onClick = { onPluck(index) }
                )
            }
        }
    }
}

/**
 * Chromatic mode has no strings to tap, so it offers one pill to pluck, with steps a
 * semitone down and up. Stepping plucks the new note too, so you can walk to it by ear.
 */
@Composable
fun ReferencePicker(
    note: Note,
    reference: ReferenceTone?,
    onStep: (Int) -> Unit,
    onPluck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            "−",
            stringResource(R.string.reference_step_down),
            enabled = note.midi > REFERENCE_RANGE.first,
            size = 36.dp
        ) { onStep(-1) }
        StringPill(
            string = TuningString(note),
            flats = false,
            size = PILL_SIZE,
            active = false,
            tuned = false,
            tint = TunerTheme.colors.inTune,
            ringing = reference?.takeIf { it.stringIndex == null && it.note == note },
            onClick = onPluck
        )
        StepButton(
            "+",
            stringResource(R.string.reference_step_up),
            enabled = note.midi < REFERENCE_RANGE.last,
            size = 36.dp
        ) { onStep(1) }
    }
}

/**
 * The practice view's drone: one pill for its note, with steps a semitone down and up.
 * Tapping the pill starts and stops the drone, and it breathes slow sage rings while
 * it plays, the colour of the drone's rows on the pitch line. With the drone off,
 * stepping plucks the new note, as the reference picker does; with it on, the drone moves.
 */
@Composable
fun DronePicker(
    note: Note,
    droning: Boolean,
    reference: ReferenceTone?,
    onStep: (Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunerTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton(
                "−",
                stringResource(R.string.drone_step_down),
                enabled = note.midi > REFERENCE_RANGE.first,
                size = 36.dp
            ) { onStep(-1) }
            StringPill(
                string = TuningString(note),
                flats = false,
                size = PILL_SIZE,
                active = false,
                tuned = false,
                tint = colors.inTune,
                ringing = reference?.takeIf { it.stringIndex == null && it.note == note },
                droning = droning,
                clickLabel = stringResource(if (droning) R.string.drone_stop else R.string.drone_start),
                onClick = onToggle
            )
            StepButton(
                "+",
                stringResource(R.string.drone_step_up),
                enabled = note.midi < REFERENCE_RANGE.last,
                size = 36.dp
            ) { onStep(1) }
        }
        Text(
            text = stringResource(if (droning) R.string.drone_on else R.string.drone_off),
            style = TunerType.detail,
            color = if (droning) colors.inTune else colors.inkMuted,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun StringPill(
    string: TuningString,
    flats: Boolean,
    size: Dp,
    active: Boolean,
    tuned: Boolean,
    tint: Color,
    ringing: ReferenceTone?,
    onClick: () -> Unit,
    droning: Boolean = false,
    clickLabel: String = stringResource(R.string.play_reference)
) {
    val colors = TunerTheme.colors
    val sounding = ringing != null
    val border by animateColorAsState(
        targetValue = when {
            droning -> colors.inTune
            sounding -> colors.ink.copy(alpha = 0.7f)
            active -> tint
            tuned -> colors.inTune.copy(alpha = 0.6f)
            else -> colors.track
        },
        label = "pillBorder"
    )
    val fill by animateColorAsState(
        targetValue = when {
            droning -> colors.inTune.copy(alpha = 0.14f)
            sounding -> colors.ink.copy(alpha = 0.07f)
            active -> tint.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        label = "pillFill"
    )
    val text by animateColorAsState(
        targetValue = if (active || sounding || droning) colors.ink else colors.inkMuted,
        label = "pillText"
    )
    val scale by animateFloatAsState(if (active || sounding || droning) 1.08f else 1f, label = "pillScale")
    val spelling = string.note.spelled(flats)
    val description = spelling.displayName + TuningString.centsLabel(string.cents) + if (tuned) ", tuned" else ""
    val textScale = size / PILL_SIZE

    // 0 at the pluck, 1 once the tone has died away; it restarts on every pluck.
    val ring = remember { Animatable(1f) }
    LaunchedEffect(ringing?.id) {
        if (ringing == null) ring.snapTo(1f)
        else {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(TONE_MS, easing = LinearEasing))
        }
    }
    val ripplePeriod = ripplePeriodMs(ringing?.frequency ?: 110.0)
    val rippleColor = colors.ink
    val droneColor = colors.inTune
    val breath = if (droning) dronePhase() else null

    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                breath?.value?.let { phase ->
                    // The drone never dies away, so its rings don't either.
                    for (k in 0 until RIPPLES) {
                        val p = (phase + k.toFloat() / RIPPLES) % 1f
                        drawCircle(
                            color = droneColor.copy(alpha = 0.45f * (1 - p) * (1 - p)),
                            radius = this.size.minDimension / 2 + p * RIPPLE_SPREAD.toPx() * 1.3f,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }
                val t = ring.value
                if (t >= 1f) return@drawBehind
                // Rings spread from the pill like the air around a string, fading with the tone.
                val elapsed = t * TONE_MS
                val fade = (1 - t) * (1 - t)
                for (k in 0 until RIPPLES) {
                    val phase = elapsed / ripplePeriod - k.toFloat() / RIPPLES
                    if (phase < 0f) continue
                    val p = phase % 1f
                    drawCircle(
                        color = rippleColor.copy(alpha = 0.4f * fade * (1 - p) * (1 - p)),
                        radius = this.size.minDimension / 2 + p * RIPPLE_SPREAD.toPx(),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }
            .scale(scale)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = clickLabel,
                onClick = onClick
            )
            .background(fill, CircleShape)
            .border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                append(spelling.name)
                withStyle(SpanStyle(fontSize = 10.sp * textScale, color = colors.inkFaint)) {
                    append(spelling.octave.toString())
                }
            },
            style = TunerType.detail.copy(fontSize = 16.sp * textScale),
            color = text
        )
        if (tuned) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(8.dp)
                    .background(colors.inTune, CircleShape)
            )
        }
    }
}

@Composable
private fun dronePhase(): State<Float> = rememberInfiniteTransition(label = "drone").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(DRONE_RIPPLE_MS, easing = LinearEasing)),
    label = "breath"
)

/** Low strings ripple slowly and high ones quickly: about 1.5 s at B0, 0.6 s at C6. */
private fun ripplePeriodMs(frequency: Double): Float {
    val octaves = (ln(frequency / 30.0) / ln(2.0)).coerceIn(0.0, 5.1)
    return (1500 * exp(-octaves * 0.18)).toFloat()
}
