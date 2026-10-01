package com.chrischappelear.tuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tabular figures, so changing digits don't make readouts shimmy sideways. */
private const val TABULAR = "tnum"

object TunerType {
    val noteLetter = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 132.sp,
        lineHeight = 132.sp
    )
    val noteAccidental = TextStyle(fontWeight = FontWeight.Light, fontSize = 46.sp, lineHeight = 46.sp)
    val noteOctave = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 24.sp)

    val cents = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = TABULAR
    )
    val detail = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.6.sp,
        fontFeatureSettings = TABULAR
    )
    val label = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp,
        fontFeatureSettings = TABULAR
    )
    val wordmark = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 15.sp,
        letterSpacing = 5.sp
    )
}

val TunerTypography = Typography()
