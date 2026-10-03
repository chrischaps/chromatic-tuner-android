package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.TuningString
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType

private val PILL_SIZE = 46.dp

/**
 * One pill per string. The one being played lights up; each string that has come
 * into tune keeps a small sage mark, so the whole instrument fills in as you go.
 * Pills shrink to fit when an instrument has more strings than the row holds.
 */
@Composable
fun StringRow(state: TunerState, modifier: Modifier = Modifier) {
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
                    else TunerTheme.colors.forCents(state.cents.toFloat())
                )
            }
        }
    }
}

@Composable
private fun StringPill(string: TuningString, flats: Boolean, size: Dp, active: Boolean, tuned: Boolean, tint: Color) {
    val colors = TunerTheme.colors
    val border by animateColorAsState(
        targetValue = when {
            active -> tint
            tuned -> colors.inTune.copy(alpha = 0.6f)
            else -> colors.track
        },
        label = "pillBorder"
    )
    val fill by animateColorAsState(
        targetValue = if (active) tint.copy(alpha = 0.16f) else Color.Transparent,
        label = "pillFill"
    )
    val text by animateColorAsState(
        targetValue = if (active) colors.ink else colors.inkMuted,
        label = "pillText"
    )
    val scale by animateFloatAsState(if (active) 1.08f else 1f, label = "pillScale")
    val spelling = string.note.spelled(flats)
    val description = spelling.displayName + TuningString.centsLabel(string.cents) + if (tuned) ", tuned" else ""
    val textScale = size / PILL_SIZE

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .semantics { contentDescription = description }
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
