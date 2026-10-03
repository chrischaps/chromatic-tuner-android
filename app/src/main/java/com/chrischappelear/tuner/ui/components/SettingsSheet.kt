package com.chrischappelear.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.data.SettingsRepository
import com.chrischappelear.tuner.data.TunerSettings
import com.chrischappelear.tuner.tuning.NoteMath
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.TuningGroup
import com.chrischappelear.tuner.tuning.Tunings
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: TunerSettings,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit,
    onSaveCustomTuning: (Tuning) -> Unit,
    onDeleteCustomTuning: (Tuning) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TunerTheme.colors
    var editing by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<Tuning?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink
    ) {
        SettingsContent(
            settings = settings,
            onTuningSelected = onTuningSelected,
            onA4Changed = onA4Changed,
            onEditTuning = { editing = it },
            onNewTuning = { editing = newDraft(settings.tuning) }
        )
    }

    editing?.let { draft ->
        CustomTuningEditor(
            draft = draft,
            isNew = settings.customTunings.none { it.id == draft.id },
            a4 = settings.a4,
            onDraftChanged = { editing = it },
            onSave = {
                onSaveCustomTuning(it)
                editing = null
            },
            onDelete = {
                onDeleteCustomTuning(draft)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/**
 * The sheet's body: chromatic and your own tunings, then one instrument group at a
 * time, then reference pitch. Custom tunings sit above the instrument lists, whose
 * length changes with each group, so they are always in the same place.
 */
@Composable
internal fun SettingsContent(
    settings: TunerSettings,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit,
    onEditTuning: (Tuning) -> Unit,
    onNewTuning: () -> Unit
) {
    var group by rememberSaveable {
        mutableStateOf(settings.tuning.group.takeIf { it in INSTRUMENT_GROUPS } ?: TuningGroup.Guitar)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        SectionLabel(stringResource(R.string.settings_tuning))
        TuningOption(
            tuning = Tunings.Chromatic,
            selected = settings.tuning == Tunings.Chromatic,
            onClick = { onTuningSelected(Tunings.Chromatic) }
        )
        settings.customTunings.forEach { tuning ->
            TuningOption(
                tuning = tuning,
                selected = tuning.id == settings.tuning.id,
                onClick = { onTuningSelected(tuning) },
                onEdit = { onEditTuning(tuning) }
            )
        }
        NewTuningRow(onNewTuning)

        GroupChips(
            selected = group,
            onSelected = { group = it },
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
        )
        Tunings.all.filter { it.group == group }.forEach { tuning ->
            TuningOption(
                tuning = tuning,
                selected = tuning == settings.tuning,
                onClick = { onTuningSelected(tuning) }
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_reference))
        ReferencePitch(settings.a4, onA4Changed)
    }
}

/** A new custom tuning starts as a copy of whatever is selected, so small changes are quick. */
private fun newDraft(current: Tuning): Tuning {
    val base = if (current.isChromatic) Tunings.GuitarStandard else current
    return Tunings.custom(
        id = "custom_${System.currentTimeMillis()}",
        name = "",
        strings = base.strings,
        flats = base.flats
    )
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = TunerType.label,
        color = TunerTheme.colors.inkMuted,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun GroupChips(selected: TuningGroup, onSelected: (TuningGroup) -> Unit, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        INSTRUMENT_GROUPS.forEach { group ->
            val active = group == selected
            Text(
                text = group.label,
                style = TunerType.detail,
                color = if (active) colors.ink else colors.inkMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.inTune.copy(alpha = 0.14f) else Color.Transparent)
                    .border(1.dp, if (active) colors.inTune.copy(alpha = 0.6f) else colors.track, CircleShape)
                    .clickable(role = Role.Tab) { onSelected(group) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun TuningOption(
    tuning: Tuning,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val colors = TunerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.inTune.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // Within a group, the group's name would only repeat; the chips already say it.
            Text(tuning.variant, style = TunerType.detail.copy(fontSize = 16.sp), color = colors.ink)
            Text(
                text = if (tuning.isChromatic) stringResource(R.string.chromatic_description)
                else tuning.strings.joinToString("  ") { it.label(tuning.flats) },
                style = TunerType.detail,
                color = colors.inkMuted
            )
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.edit), style = TunerType.detail, color = colors.inTune)
            }
            Spacer(Modifier.width(4.dp))
        }
        Spacer(
            Modifier
                .size(10.dp)
                .background(if (selected) colors.inTune else Color.Transparent, CircleShape)
        )
    }
}

@Composable
private fun NewTuningRow(onClick: () -> Unit) {
    val colors = TunerTheme.colors
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.track, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            "+  " + stringResource(R.string.new_tuning),
            style = TunerType.detail.copy(fontSize = 16.sp),
            color = colors.inTune
        )
        Text(stringResource(R.string.new_tuning_description), style = TunerType.detail, color = colors.inkMuted)
    }
}

/** The chips: every group that holds instrument presets. */
private val INSTRUMENT_GROUPS = TuningGroup.entries - TuningGroup.Chromatic - TuningGroup.Custom

@Composable
private fun ReferencePitch(a4: Double, onA4Changed: (Double) -> Unit) {
    val colors = TunerTheme.colors
    val range = SettingsRepository.A4_RANGE
    // Track the drag locally so the label follows the thumb; persist on release.
    var dragging by remember(a4) { mutableFloatStateOf(a4.toFloat()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.a4_value, dragging.roundToInt()),
            style = TunerType.cents.copy(fontSize = 26.sp),
            color = colors.ink
        )
        if (dragging.roundToInt() != NoteMath.DEFAULT_A4.toInt()) {
            TextButton(onClick = { onA4Changed(NoteMath.DEFAULT_A4) }) {
                Text(stringResource(R.string.a4_reset), color = colors.inTune)
            }
        }
    }
    Slider(
        value = dragging,
        onValueChange = { dragging = it.roundToInt().toFloat() },
        onValueChangeFinished = { onA4Changed(dragging.toDouble()) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        steps = (range.endInclusive - range.start).toInt() - 1,
        colors = SliderDefaults.colors(
            thumbColor = colors.inTune,
            activeTrackColor = colors.inTune,
            inactiveTrackColor = colors.track,
            activeTickColor = colors.background.copy(alpha = 0.4f),
            inactiveTickColor = colors.inkFaint.copy(alpha = 0.5f)
        )
    )
    Text(
        stringResource(R.string.a4_description),
        style = TunerType.detail,
        color = colors.inkMuted
    )
}
