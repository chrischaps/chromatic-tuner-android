package com.chrischappelear.tuner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.NoteMath
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.TuningCodec
import com.chrischappelear.tuner.tuning.TuningString
import com.chrischappelear.tuner.tuning.Tunings
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import java.util.Locale
import kotlin.math.roundToInt

/** Keeps an unsaved draft through rotation, using the same text form custom tunings are stored in. */
internal val DraftSaver: Saver<Tuning?, String> = Saver(
    save = { draft -> draft?.let { TuningCodec.encode(listOf(it)) } },
    restore = { TuningCodec.decode(it).firstOrNull() }
)

/**
 * Builds a custom tuning string by string. Each string steps a semitone at a time,
 * the way a peg turns, rather than asking for a typed note name.
 */
@Composable
fun CustomTuningEditor(
    draft: Tuning,
    isNew: Boolean,
    a4: Double,
    onDraftChanged: (Tuning) -> Unit,
    onSave: (Tuning) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CustomTuningCard(draft, isNew, a4, onDraftChanged, onSave, onDelete, onDismiss)
    }
}

@Composable
internal fun CustomTuningCard(
    draft: Tuning,
    isNew: Boolean,
    a4: Double,
    onDraftChanged: (Tuning) -> Unit,
    onSave: (Tuning) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TunerTheme.colors
    val defaultName = stringResource(R.string.editor_default_name)
    val range = Tunings.CUSTOM_NOTE_RANGE

    // The string whose cents offset is open for fine-tuning, if any.
    var fineTuning by rememberSaveable { mutableStateOf<Int?>(null) }

    fun setString(index: Int, string: TuningString) =
        onDraftChanged(draft.copy(strings = draft.strings.toMutableList().also { it[index] = string }))

    Column(
        Modifier
            .padding(20.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
    ) {
        SectionLabel(stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit))

        NameField(
            name = draft.variant,
            placeholder = defaultName,
            onNameChanged = { onDraftChanged(draft.copy(variant = it)) }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.editor_strings), Modifier.weight(1f))
            SpellingToggle(flats = draft.flats, onChanged = { onDraftChanged(draft.copy(flats = it)) })
        }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            draft.strings.forEachIndexed { index, string ->
                StringStepper(
                    number = index + 1,
                    string = string,
                    flats = draft.flats,
                    a4 = a4,
                    canLower = string.note.midi > range.first,
                    canRaise = string.note.midi < range.last,
                    canRemove = draft.strings.size > 1,
                    fineTuning = fineTuning == index,
                    onStep = { setString(index, string.copy(note = Note(string.note.midi + it))) },
                    onToggleFine = { fineTuning = if (fineTuning == index) null else index },
                    onCentsChanged = { setString(index, string.copy(cents = it)) },
                    onRemove = {
                        fineTuning = null
                        onDraftChanged(draft.copy(strings = draft.strings.filterIndexed { i, _ -> i != index }))
                    }
                )
            }
            if (draft.strings.size < Tunings.MAX_CUSTOM_STRINGS) {
                // A new string starts a fourth above the last, the most common interval between strings.
                val next = ((draft.strings.lastOrNull()?.note?.midi ?: 40) + 5).coerceIn(range)
                Text(
                    "+  " + stringResource(R.string.editor_add_string),
                    style = TunerType.detail,
                    color = colors.inTune,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onDraftChanged(draft.copy(strings = draft.strings + TuningString(Note(next)))) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isNew) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.editor_delete), color = colors.drift)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel), color = colors.inkMuted)
            }
            TextButton(onClick = { onSave(draft.copy(variant = draft.variant.trim().ifEmpty { defaultName })) }) {
                Text(stringResource(R.string.editor_save), color = colors.inTune)
            }
        }
    }
}

@Composable
private fun NameField(name: String, placeholder: String, onNameChanged: (String) -> Unit) {
    val colors = TunerTheme.colors
    val style = TunerType.cents.copy(fontSize = 24.sp, color = colors.ink)
    BasicTextField(
        value = name,
        onValueChange = { onNameChanged(it.take(40)) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(colors.inTune),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        decorationBox = { field ->
            Column {
                Box {
                    if (name.isEmpty()) Text(placeholder, style = style.copy(color = colors.inkFaint))
                    field()
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.track)
                )
            }
        }
    )
}

@Composable
private fun SpellingToggle(flats: Boolean, onChanged: (Boolean) -> Unit) {
    val colors = TunerTheme.colors
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .clip(CircleShape)
            .border(1.dp, colors.track, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((value, glyph, label) in listOf(
            Triple(false, "♯", R.string.editor_sharps),
            Triple(true, "♭", R.string.editor_flats)
        )) {
            val active = value == flats
            val description = stringResource(label)
            Text(
                glyph,
                style = TunerType.detail.copy(fontSize = 16.sp),
                color = if (active) colors.ink else colors.inkMuted,
                modifier = Modifier
                    .semantics { contentDescription = description }
                    .clip(CircleShape)
                    .background(if (active) colors.inTune.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onChanged(value) }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StringStepper(
    number: Int,
    string: TuningString,
    flats: Boolean,
    a4: Double,
    canLower: Boolean,
    canRaise: Boolean,
    canRemove: Boolean,
    fineTuning: Boolean,
    onStep: (Int) -> Unit,
    onToggleFine: () -> Unit,
    onCentsChanged: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val colors = TunerTheme.colors
    val spelling = string.note.spelled(flats)
    Column(
        Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (fineTuning) colors.inTune.copy(alpha = 0.08f) else Color.Transparent)
            .padding(vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number.toString(),
                style = TunerType.label,
                color = colors.inkFaint,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(16.dp)
            )
            StepButton("−", stringResource(R.string.editor_step_down, number), enabled = canLower) { onStep(-1) }
            // The whole note area opens fine-tuning; the cents chip is what says so.
            val fineDescription = stringResource(R.string.editor_fine_tune, number)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { contentDescription = fineDescription }
                    .clickable(role = Role.Button, onClick = onToggleFine)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    buildAnnotatedString {
                        append(spelling.name)
                        withStyle(SpanStyle(fontSize = 13.sp, color = colors.inkFaint)) { append(spelling.octave.toString()) }
                    },
                    style = TunerType.cents.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = colors.ink
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(Locale.getDefault(), "%.1f Hz", NoteMath.midiToFrequency(string.midi, a4)),
                        style = TunerType.detail.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.inkMuted
                    )
                    Spacer(Modifier.width(6.dp))
                    CentsChip(string.cents, open = fineTuning)
                }
            }
            StepButton("+", stringResource(R.string.editor_step_up, number), enabled = canRaise) { onStep(1) }
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
                if (canRemove) {
                    val description = stringResource(R.string.editor_remove_string, number)
                    Text(
                        "×",
                        style = TunerType.detail.copy(fontSize = 18.sp),
                        color = colors.inkFaint,
                        modifier = Modifier
                            .semantics { contentDescription = description }
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onRemove)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        AnimatedVisibility(visible = fineTuning) {
            CentsAdjuster(number, string.cents, onCentsChanged)
        }
    }
}

/** The string's offset, outlined like a control, with a chevron that turns when open. */
@Composable
private fun CentsChip(cents: Int, open: Boolean) {
    val colors = TunerTheme.colors
    val accent = cents != 0 || open
    Text(
        (if (cents == 0) "0¢" else TuningString.centsLabel(cents)) + if (open) "  ▴" else "  ▾",
        style = TunerType.detail.copy(fontSize = 11.sp, lineHeight = 14.sp),
        color = if (cents != 0) colors.inTune else colors.inkMuted,
        modifier = Modifier
            .border(1.dp, if (accent) colors.inTune.copy(alpha = 0.6f) else colors.track, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Nudges a string off equal temperament, a cent at a time or by slider, within half a semitone. */
@Composable
private fun CentsAdjuster(number: Int, cents: Int, onCentsChanged: (Int) -> Unit) {
    val colors = TunerTheme.colors
    val limit = TuningString.MAX_CENTS
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = cents.toFloat(),
            onValueChange = { onCentsChanged(it.roundToInt()) },
            valueRange = -limit.toFloat()..limit.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = colors.inTune,
                activeTrackColor = colors.inTune,
                inactiveTrackColor = colors.track
            )
        )
        Spacer(Modifier.width(8.dp))
        StepButton("−", stringResource(R.string.editor_cent_down, number), enabled = cents > -limit, size = 32.dp) {
            onCentsChanged(cents - 1)
        }
        // Tapping the value snaps back to equal temperament.
        Text(
            if (cents == 0) "0¢" else TuningString.centsLabel(cents),
            style = TunerType.detail,
            color = if (cents == 0) colors.inkMuted else colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(52.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onCentsChanged(0) }
                .padding(vertical = 6.dp)
        )
        StepButton("+", stringResource(R.string.editor_cent_up, number), enabled = cents < limit, size = 32.dp) {
            onCentsChanged(cents + 1)
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, size: Dp = 40.dp, onClick: () -> Unit) {
    val colors = TunerTheme.colors
    Box(
        Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .border(1.dp, colors.track, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = TunerType.detail.copy(fontSize = 18.sp), color = colors.ink)
    }
}
