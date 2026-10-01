package com.chrischappelear.tuner.ui

import android.content.res.Configuration
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.TunerUiState
import com.chrischappelear.tuner.data.TunerSettings
import com.chrischappelear.tuner.tuning.Note
import com.chrischappelear.tuner.tuning.TracePoint
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.Tunings
import com.chrischappelear.tuner.ui.components.CentsTrace
import com.chrischappelear.tuner.ui.components.NoteGlyph
import com.chrischappelear.tuner.ui.components.PermissionScreen
import com.chrischappelear.tuner.ui.components.Readout
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
    hasPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit
) {
    val colors = TunerTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                // The logo's halo: a soft lift of light behind the dial.
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
            TunerContent(uiState, settings, onTuningSelected, onA4Changed)
        }
    }
}

@Composable
private fun TunerContent(
    uiState: TunerUiState,
    settings: TunerSettings,
    onTuningSelected: (Tuning) -> Unit,
    onA4Changed: (Double) -> Unit
) {
    val state = uiState.tuner
    var showSettings by rememberSaveable { mutableStateOf(false) }

    KeepScreenOn()
    LockHaptics(state.lockCount)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar(settings, onClick = { showSettings = true })

            if (landscape) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Dial(state, Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        StringRow(state, Modifier.padding(bottom = 16.dp))
                        CentsTrace(uiState.history, height = 88.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.weight(1f))
                Dial(state)
                Spacer(Modifier.height(20.dp))
                StringRow(state)
                Spacer(Modifier.weight(1f))
                CentsTrace(uiState.history, Modifier.padding(bottom = 16.dp))
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onTuningSelected = onTuningSelected,
            onA4Changed = onA4Changed,
            onDismiss = { showSettings = false }
        )
    }
}

/** The arc, the note sitting beneath it, and the readout. */
@Composable
private fun Dial(state: TunerState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val radius = min(maxWidth * 0.44f, 210.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TuningMeter(
                cents = state.cents.toFloat().takeIf { state.status != TunerStatus.Idle },
                locked = state.locked,
                dimmed = state.status == TunerStatus.Fading,
                radius = radius,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(meterHeightFor(radius))
            )
            NoteGlyph(state, Modifier.offset(y = (-6).dp))
            Readout(state)
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
private fun Preview(uiState: TunerUiState, darkTheme: Boolean = true) {
    ChromaticTunerTheme(darkTheme = darkTheme) {
        TunerScreen(
            uiState = uiState,
            settings = TunerSettings(tuning = uiState.tuner.tuning),
            hasPermission = true,
            permissionPermanentlyDenied = false,
            onRequestPermission = {},
            onOpenAppSettings = {},
            onTuningSelected = {},
            onA4Changed = {}
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

@Preview(name = "Landscape", widthDp = 844, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LandscapePreview() = Preview(previewState(-7.0))

// endregion
