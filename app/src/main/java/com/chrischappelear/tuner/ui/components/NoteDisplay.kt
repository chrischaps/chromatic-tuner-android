package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.TuningString
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The note being tuned, large, with a soft bloom behind it once it locks in tune.
 * Always occupies the same space, so nothing below it jumps when it comes and goes.
 */
@Composable
fun NoteGlyph(state: TunerState, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    val active = state.status != TunerStatus.Idle
    val bloom by animateFloatAsState(if (state.locked) 1f else 0f, tween(600), label = "bloom")
    val presence by animateFloatAsState(
        targetValue = if (state.status == TunerStatus.Fading) 0.4f else 1f,
        animationSpec = tween(500),
        label = "presence"
    )
    val ink by animateColorAsState(
        targetValue = if (state.locked) colors.glow else colors.ink,
        animationSpec = tween(500),
        label = "ink"
    )
    val swell by animateFloatAsState(if (state.locked) 1.04f else 1f, tween(450), label = "swell")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .drawBehind {
                if (bloom > 0f) {
                    val radius = size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colors.glow.copy(alpha = 0.30f * bloom), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state.note.takeIf { active },
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "note"
        ) { note ->
            if (note != null) {
                NoteName(
                    note = note,
                    flats = state.tuning.flats,
                    modifier = Modifier
                        .alpha(presence)
                        .scale(swell),
                    color = ink
                )
            }
        }
    }
}

@Composable
private fun NoteName(note: Note, flats: Boolean, color: Color, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    val spelling = note.spelled(flats)
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        // A matching blank column on the left keeps the letter itself centered.
        Box(Modifier.width(34.dp))
        Text(spelling.letter, style = TunerType.noteLetter, color = color)
        Column(
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight()
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(spelling.accidental, style = TunerType.noteAccidental, color = color)
            Text(spelling.octave.toString(), style = TunerType.noteOctave, color = colors.inkMuted)
        }
    }
}

/** Cents offset, frequency, and which way to turn the peg. */
@Composable
fun Readout(state: TunerState, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    val active = state.status != TunerStatus.Idle
    val cents = state.cents.roundToInt()
    val tint by animateColorAsState(
        targetValue = when {
            !active -> colors.inkMuted
            state.locked || cents == 0 -> colors.inTune
            else -> colors.forCents(state.cents.toFloat())
        },
        label = "readoutTint"
    )
    val presence by animateFloatAsState(
        if (state.status == TunerStatus.Fading) 0.45f else 1f, tween(500), label = "presence"
    )

    val turnHint = stringResource(if (cents < 0) R.string.tune_up else R.string.tune_down)
    // A microtonal string is tuned to its note plus an offset; say so, since the glyph can't.
    val targetLabel = stringResource(R.string.string_target, TuningString.centsLabel(state.stringCents))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(presence),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                !active -> stringResource(
                    if (state.tuning.isChromatic) R.string.hint_play_note else R.string.hint_play_string
                )
                state.locked || cents == 0 -> stringResource(R.string.in_tune)
                else -> stringResource(R.string.cents_format, if (cents > 0) "+$cents" else "−${abs(cents)}")
            },
            style = TunerType.cents,
            color = tint,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (!active) " " else buildString {
                if (state.stringCents != 0) {
                    append(targetLabel)
                    append("  ·  ")
                }
                append(String.format(Locale.getDefault(), "%.1f Hz", state.frequency))
                if (!state.locked && abs(cents) >= 2) {
                    append("  ·  ")
                    append(turnHint)
                }
            },
            style = TunerType.detail,
            color = colors.inkMuted,
            textAlign = TextAlign.Center
        )
    }
}
