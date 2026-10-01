package com.chrischappelear.tuner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private fun TunerColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = inTune,
        onPrimary = background,
        secondary = drift,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = track,
        onSurfaceVariant = inkMuted,
        surfaceContainerLow = surface,
        outline = inkFaint,
        outlineVariant = track
    )
} else {
    lightColorScheme(
        primary = inTune,
        onPrimary = backgroundGlow,
        secondary = drift,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = track,
        onSurfaceVariant = inkMuted,
        surfaceContainerLow = backgroundGlow,
        outline = inkFaint,
        outlineVariant = track
    )
}

@Composable
fun ChromaticTunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DuskColors else PaperColors
    CompositionLocalProvider(LocalTunerColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = TunerTypography,
            content = content
        )
    }
}

object TunerTheme {
    val colors: TunerColors
        @Composable @ReadOnlyComposable get() = LocalTunerColors.current
}
