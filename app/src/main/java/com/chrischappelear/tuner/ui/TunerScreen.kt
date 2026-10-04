package com.chrischappelear.tuner.ui

import android.content.res.Configuration
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.TunerUiState
import com.chrischappelear.tuner.audio.ReferenceTone
import com.chrischappelear.tuner.data.TunerSettings
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TracePoint
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.TuningString
import com.chrischappelear.tuner.tuning.Tunings
import com.chrischappelear.tuner.ui.components.CentsTrace
import com.chrischappelear.tuner.ui.components.CustomTuningCard
import com.chrischappelear.tuner.ui.components.DronePicker
import com.chrischappelear.tuner.ui.components.NoteGlyph
import com.chrischappelear.tuner.ui.components.PermissionScreen
import com.chrischappelear.tuner.ui.components.PitchLine
import com.chrischappelear.tuner.ui.components.PracticeReadout
import com.chrischappelear.tuner.ui.components.REFERENCE_RANGE
import com.chrischappelear.tuner.ui.components.Readout
import com.chrischappelear.tuner.ui.components.ReferencePicker
import com.chrischappelear.tuner.ui.components.SettingsContent
import com.chrischappelear.tuner.ui.components.SettingsSheet
import com.chrischappelear.tuner.ui.components.StringRow
import com.chrischappelear.tuner.ui.components.TuningMeter
import com.chrischappelear.tuner.ui.components.meterHeightFor
import com.chrischappelear.tuner.ui.theme.ChromaticTunerTheme
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    uiState: TunerUiState,
    settings: TunerSettings,
    reference: ReferenceTone?,
    hasPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit,
    onSaveCustomTuning: (Tuning) -> Unit,
    onDeleteCustomTuning: (Tuning) -> Unit,
    onPlayReference: (TuningString, Int?) -> Unit,
    drone: Note? = null,
    onStartDrone: (Note) -> Unit = {},
    onStopDrone: () -> Unit = {},
    startInPractice: Boolean = false
) {
    val colors = TunerTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                // A soft lift of light behind the dial, like the icon's.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.backgroundGlow, colors.background),
                        center = Offset(size.width / 2, size.height * 0.32f),
                        radius = size.maxDimension * 0.6f
                    ),
                    radius = size.maxDimension,
                    center = Offset(size.width / 2, size.height * 0.32f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!hasPermission) {
            PermissionScreen(
                permanentlyDenied = permissionPermanentlyDenied,
                onRequest = onRequestPermission,
                onOpenSettings = onOpenAppSettings
            )
        } else {
            TunerContent(
                uiState, settings, reference, drone,
                onTuningSelected, onA4Changed, onSaveCustomTuning, onDeleteCustomTuning, onPlayReference,
                onStartDrone, onStopDrone, startInPractice
            )
        }
    }
}

@Composable
private fun TunerContent(
    uiState: TunerUiState,
    settings: TunerSettings,
    reference: ReferenceTone?,
    drone: Note?,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit,
    onSaveCustomTuning: (Tuning) -> Unit,
    onDeleteCustomTuning: (Tuning) -> Unit,
    onPlayReference: (TuningString, Int?) -> Unit,
    onStartDrone: (Note) -> Unit,
    onStopDrone: () -> Unit,
    startInPractice: Boolean
) {
    val state = uiState.tuner
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // One note for the chromatic picker and the drone, so moving between them keeps it.
    var pickerMidi by rememberSaveable { mutableIntStateOf(Note.parse("A4").midi) }
    var practice by rememberSaveable { mutableStateOf(startInPractice) }
    val chromatic = settings.tuning.isChromatic
    val practicing = practice && chromatic

    // The drone belongs to the practice view and stops on leaving it, though not on a rotation.
    LaunchedEffect(practicing) {
        if (!practicing) onStopDrone()
    }

    // Strings to tap in a preset; in chromatic, a single note to pick and pluck.
    val strings: @Composable (Modifier) -> Unit = { modifier ->
        if (state.tuning.isChromatic) {
            ReferencePicker(
                note = Note(pickerMidi),
                reference = reference,
                onStep = { step ->
                    pickerMidi = (pickerMidi + step).coerceIn(REFERENCE_RANGE)
                    onPlayReference(TuningString(Note(pickerMidi)), null)
                },
                onPluck = { onPlayReference(TuningString(Note(pickerMidi)), null) },
                modifier = modifier
            )
        } else {
            StringRow(
                state = state,
                reference = reference,
                onPluck = { index -> onPlayReference(state.tuning.strings[index], index) },
                modifier = modifier
            )
        }
    }

    val dronePicker: @Composable (Modifier) -> Unit = { modifier ->
        DronePicker(
            note = Note(pickerMidi),
            droning = drone != null,
            reference = reference,
            onStep = { step ->
                pickerMidi = (pickerMidi + step).coerceIn(REFERENCE_RANGE)
                if (drone != null) onStartDrone(Note(pickerMidi))
                else onPlayReference(TuningString(Note(pickerMidi)), null)
            },
            onToggle = { if (drone != null) onStopDrone() else onStartDrone(Note(pickerMidi)) },
            modifier = modifier
        )
    }

    KeepScreenOn()
    LockHaptics(state.lockCount)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar(settings, onClick = { showSettings = true })
            if (chromatic) {
                ModeSwitch(
                    practice = practice,
                    onChange = { practice = it },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                )
            }

            if (practicing) {
                if (landscape) {
                    Row(Modifier.weight(1f).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PitchLine(uiState.history, drone, Modifier.weight(1.5f).fillMaxHeight(), flats = state.tuning.flats)
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            PracticeReadout(state, reference = reference)
                            Spacer(Modifier.height(20.dp))
                            dronePicker(Modifier)
                        }
                    }
                } else {
                    PracticeReadout(state, reference = reference)
                    Spacer(Modifier.height(8.dp))
                    PitchLine(uiState.history, drone, Modifier.weight(1f), flats = state.tuning.flats)
                    Spacer(Modifier.height(20.dp))
                    dronePicker(Modifier.padding(bottom = 20.dp))
                }
            } else if (landscape) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Dial(state, reference, Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        strings(Modifier.padding(bottom = 16.dp))
                        CentsTrace(uiState.history, flats = state.tuning.flats, height = 88.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.weight(1f))
                Dial(state, reference)
                Spacer(Modifier.height(20.dp))
                strings(Modifier)
                Spacer(Modifier.weight(1f))
                CentsTrace(uiState.history, Modifier.padding(bottom = 16.dp), flats = state.tuning.flats)
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onTuningSelected = onTuningSelected,
            onA4Changed = onA4Changed,
            onSaveCustomTuning = onSaveCustomTuning,
            onDeleteCustomTuning = onDeleteCustomTuning,
            onDismiss = { showSettings = false }
        )
    }
}

/** The arc, the note sitting beneath it, and the readout; or the reference tone ringing. */
@Composable
private fun Dial(state: TunerState, reference: ReferenceTone?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val radius = min(maxWidth * 0.44f, 210.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TuningMeter(
                cents = state.cents.toFloat().takeIf { state.status != TunerStatus.Idle && reference == null },
                locked = state.locked && reference == null,
                dimmed = state.status == TunerStatus.Fading,
                radius = radius,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(meterHeightFor(radius))
            )
            NoteGlyph(state, Modifier.offset(y = (-6).dp), reference)
            Readout(state, reference = reference)
        }
    }
}

/** Chromatic's two views: the tuner, and practice with the pitch line and drone. */
@Composable
private fun ModeSwitch(practice: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, colors.track, CircleShape)
            .padding(3.dp)
    ) {
        for ((value, label) in listOf(false to R.string.mode_tune, true to R.string.mode_practice)) {
            val selected = practice == value
            val fill by animateColorAsState(if (selected) colors.track else Color.Transparent, label = "modeFill")
            val ink by animateColorAsState(if (selected) colors.ink else colors.inkMuted, label = "modeInk")
            Text(
                text = stringResource(label),
                style = TunerType.detail,
                color = ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(fill, CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(value) })
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TopBar(settings: TunerSettings, onClick: () -> Unit) {
    val colors = TunerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.wordmark), style = TunerType.wordmark, color = colors.inkMuted)
        Spacer(Modifier.weight(1f))
        Chip(settings.tuning.name, onClick)
        Spacer(Modifier.width(8.dp))
        Chip(stringResource(R.string.a4_chip, settings.a4.roundToInt()), onClick)
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val colors = TunerTheme.colors
    Text(
        text = text,
        style = TunerType.detail,
        color = colors.ink,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, colors.track, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** A single soft tick each time a note settles in tune. */
@Composable
private fun LockHaptics(lockCount: Int) {
    val view = LocalView.current
    var seen by remember { mutableIntStateOf(lockCount) }
    LaunchedEffect(lockCount) {
        if (lockCount > seen) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.KEYBOARD_TAP
            )
        }
        seen = lockCount
    }
}

// region Previews

private fun previewHistory(): List<TracePoint> {
    val a2 = Note.parse("A2")
    val e2 = Note.parse("E2")
    return (0 until 360).map { i ->
        val t = i * 21L
        when {
            i < 120 -> TracePoint(t, (-38f + i * 0.28f), e2)
            i < 150 -> TracePoint(t, null, null)
            else -> TracePoint(t, (30f * kotlin.math.exp(-(i - 150) / 60f)).toFloat(), a2)
        }
    }
}

private fun previewState(cents: Double, locked: Boolean = false, status: TunerStatus = TunerStatus.Active) =
    TunerUiState(
        tuner = TunerState(
            status = status,
            note = Note.parse("A2"),
            frequency = 110.0 * Math.pow(2.0, cents / 1200),
            cents = cents,
            locked = locked,
            stringIndex = 1,
            tunedStrings = setOf(0),
            tuning = Tunings.GuitarStandard
        ),
        history = previewHistory()
    )

@Composable
private fun Preview(
    uiState: TunerUiState,
    darkTheme: Boolean = true,
    reference: ReferenceTone? = null,
    drone: Note? = null,
    practice: Boolean = false
) {
    ChromaticTunerTheme(darkTheme = darkTheme) {
        TunerScreen(
            uiState = uiState,
            settings = TunerSettings(tuning = uiState.tuner.tuning),
            reference = reference,
            hasPermission = true,
            permissionPermanentlyDenied = false,
            onRequestPermission = {},
            onOpenAppSettings = {},
            onTuningSelected = {},
            onA4Changed = {},
            onSaveCustomTuning = {},
            onDeleteCustomTuning = {},
            onPlayReference = { _, _ -> },
            drone = drone,
            startInPractice = practice
        )
    }
}

@Preview(name = "Flat", widthDp = 390, heightDp = 844)
@Composable
private fun FlatPreview() = Preview(previewState(-18.0))

@Preview(name = "Locked", widthDp = 390, heightDp = 844)
@Composable
private fun LockedPreview() = Preview(previewState(0.4, locked = true))

@Preview(name = "Sharp · light", widthDp = 390, heightDp = 844)
@Composable
private fun SharpLightPreview() = Preview(previewState(31.0), darkTheme = false)

@Preview(name = "Idle", widthDp = 390, heightDp = 844)
@Composable
private fun IdlePreview() = Preview(TunerUiState())

@Preview(name = "Reference", widthDp = 390, heightDp = 844)
@Composable
private fun ReferencePreview() = Preview(
    TunerUiState(tuner = TunerState(tuning = Tunings.GuitarStandard)),
    reference = ReferenceTone(Note.parse("D3"), cents = 0, frequency = 146.83, stringIndex = 2, id = 0)
)

@Preview(name = "Chromatic · light", widthDp = 390, heightDp = 844)
@Composable
private fun ChromaticPreview() = Preview(TunerUiState(), darkTheme = false)

/** A scale sung over a D drone: up from D4, a little flat on the third, sliding between steps. */
private fun previewScale(): TunerUiState {
    val steps = listOf(62.0, 64.0, 65.85, 67.0, 69.0, 67.0, 65.9, 64.0, 62.05)
    val points = mutableListOf<TracePoint>()
    var t = 0L
    var midi = steps.first()
    for (step in steps) {
        val from = midi
        repeat(70) { frame ->
            midi = if (frame < 5) from + (step - from) * (frame + 1) / 5 else step + 0.06 * kotlin.math.sin(frame / 4.0)
            points += TracePoint(t, 0f, Note(midi.roundToInt()), midi.toFloat())
            t += 21
        }
        repeat(8) {
            points += TracePoint(t, null, null)
            t += 21
        }
    }
    val now = android.os.SystemClock.elapsedRealtime()
    return TunerUiState(
        tuner = TunerState(
            status = TunerStatus.Active,
            note = Note(62),
            frequency = 294.5,
            cents = 4.0,
            tuning = Tunings.Chromatic
        ),
        history = points.dropLast(8).map { it.copy(timeMs = it.timeMs - t + 8 * 21 + now) }
    )
}

@Preview(name = "Practice", widthDp = 390, heightDp = 844)
@Composable
private fun PracticePreview() = Preview(previewScale(), drone = Note.parse("D3"), practice = true)

@Preview(name = "Practice · light", widthDp = 390, heightDp = 844)
@Composable
private fun PracticeLightPreview() = Preview(previewScale(), darkTheme = false, drone = Note.parse("D3"), practice = true)

@Preview(name = "Practice · landscape", widthDp = 844, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PracticeLandscapePreview() = Preview(previewScale(), practice = true)

@Preview(name = "Landscape", widthDp = 844, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LandscapePreview() = Preview(previewState(-7.0))

@Preview(name = "Half-step down", widthDp = 390, heightDp = 844)
@Composable
private fun FlatsPreview() = Preview(
    previewState(-9.0).let { ui ->
        ui.copy(tuner = ui.tuner.copy(note = Note.parse("Ab2"), tuning = Tunings.GuitarHalfStepDown))
    }
)

// Open C with its third lowered toward just intonation.
private val previewCustom = Tunings.custom(
    "custom_1", "Open C, just third",
    "C2 G2 C3 G3 C4 E4".split(' ').map { TuningString(Note.parse(it), if (it == "E4") -14 else 0) },
    flats = false
)

@Composable
private fun SheetPreview(settings: TunerSettings, darkTheme: Boolean) {
    ChromaticTunerTheme(darkTheme = darkTheme) {
        Box(Modifier.background(TunerTheme.colors.surface).padding(top = 16.dp)) {
            SettingsContent(settings, onTuningSelected = {}, onA4Changed = {}, onEditTuning = {}, onNewTuning = {})
        }
    }
}

@Preview(name = "Sheet · bass", widthDp = 390, heightDp = 700)
@Composable
private fun SheetBassPreview() = SheetPreview(TunerSettings(tuning = Tunings.BassFiveString), darkTheme = true)

@Preview(name = "Sheet · custom · light", widthDp = 390, heightDp = 700)
@Composable
private fun SheetCustomPreview() = SheetPreview(
    TunerSettings(tuning = previewCustom, customTunings = listOf(previewCustom)),
    darkTheme = false
)

@Composable
private fun EditorPreview(darkTheme: Boolean) {
    ChromaticTunerTheme(darkTheme = darkTheme) {
        Box(Modifier.background(TunerTheme.colors.background)) {
            CustomTuningCard(
                draft = previewCustom,
                isNew = true,
                a4 = 440.0,
                onDraftChanged = {},
                onSave = {},
                onDelete = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Editor", widthDp = 390, heightDp = 760)
@Composable
private fun EditorDarkPreview() = EditorPreview(darkTheme = true)

@Preview(name = "Editor · light", widthDp = 390, heightDp = 760)
@Composable
private fun EditorLightPreview() = EditorPreview(darkTheme = false)

// endregion
