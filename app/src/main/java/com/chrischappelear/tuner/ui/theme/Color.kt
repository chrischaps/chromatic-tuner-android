package com.chrischappelear.tuner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

/**
 * Slate-green dusk, an off-white glow, and sage for
 * the moment a string comes true. Drift is a warm amber on *both* sides of
 * center — direction is carried by position and the ♭/♯ marks, never by
 * a red/green pair.
 */
@Immutable
data class TunerColors(
    val background: Color,
    val backgroundGlow: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val track: Color,
    val inTune: Color,
    val glow: Color,
    val drift: Color,
    val isDark: Boolean
) {
    /** Sage when close, easing to amber as the reading strays past ~25 cents. */
    fun forCents(cents: Float): Color {
        val t = ((abs(cents) - 3f) / 22f).coerceIn(0f, 1f)
        return lerp(inTune, drift, t * t * (3 - 2 * t))
    }
}

val DuskColors = TunerColors(
    background = Color(0xFF16201D),
    backgroundGlow = Color(0xFF253731),
    surface = Color(0xFF1E2B27),
    ink = Color(0xFFF3F5EE),
    inkMuted = Color(0xFFA7B6AE),
    inkFaint = Color(0xFF5B6C65),
    track = Color(0xFF34463F),
    inTune = Color(0xFF9CC5B0),
    glow = Color(0xFFD4EDDF),
    drift = Color(0xFFE3A65B),
    isDark = true
)

val PaperColors = TunerColors(
    background = Color(0xFFEDF1EB),
    backgroundGlow = Color(0xFFF8FAF5),
    surface = Color(0xFFE2E9E2),
    ink = Color(0xFF1B2622),
    inkMuted = Color(0xFF55655E),
    inkFaint = Color(0xFFA3B1AA),
    track = Color(0xFFC9D4CD),
    inTune = Color(0xFF3D7A62),
    glow = Color(0xFF8CC4A9),
    drift = Color(0xFFB8732A),
    isDark = false
)

val LocalTunerColors = staticCompositionLocalOf { DuskColors }
