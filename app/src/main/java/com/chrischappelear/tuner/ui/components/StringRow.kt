package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType

/**
 * One pill per string. The one being played lights up; each string that has come
 * into tune keeps a small sage mark, so the whole instrument fills in as you go.
 */
@Composable
fun StringRow(state: TunerState, modifier: Modifier = Modifier) {
    val strings = state.tuning.strings
    if (strings.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        strings.forEachIndexed { index, note ->
            StringPill(
                note = note,
                active = state.status == TunerStatus.Active && state.stringIndex == index,
                tuned = index in state.tunedStrings,
                tint = if (state.locked) TunerTheme.colors.inTune
                else TunerTheme.colors.forCents(state.cents.toFloat())
            )
        }
    }
}

@Composable
private fun StringPill(note: Note, active: Boolean, tuned: Boolean, tint: Color) {
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

    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .semantics { contentDescription = note.displayName + if (tuned) ", tuned" else "" }
            .background(fill, CircleShape)
            .border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                append(note.name)
                withStyle(SpanStyle(fontSize = 10.sp, color = colors.inkFaint)) { append(note.octave.toString()) }
            },
            style = TunerType.detail.copy(fontSize = 16.sp),
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
