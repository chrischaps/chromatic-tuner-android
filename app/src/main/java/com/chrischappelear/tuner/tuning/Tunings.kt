package com.chrischappelear.tuner.tuning

import kotlin.math.abs

/** Sections of the tuning picker. A [prefixed] group names its tunings "Group · Variant". */
enum class TuningGroup(val label: String, val prefixed: Boolean) {
    Chromatic("Chromatic", prefixed = false),
    Guitar("Guitar", prefixed = true),
    Bass("Bass", prefixed = true),
    Ukulele("Ukulele", prefixed = true),
    Strings("Strings", prefixed = false),
    Banjo("Banjo", prefixed = true),
    Custom("Custom", prefixed = false)
}

/**
 * One string's target: an equal-tempered [note], optionally nudged by [cents] for
 * microtonal tunings. The note is what's displayed; [midi] is the pitch tuned to.
 */
data class TuningString(val note: Note, val cents: Int = 0) {
    val midi: Double get() = note.midi + cents / 100.0

    /** The note's name, with any offset, e.g. "E" or "G−14¢". */
    fun label(flats: Boolean): String = note.spelled(flats).name + centsLabel(cents)

    companion object {
        /** Offsets stay within half a semitone; beyond that, it's the next note. */
        const val MAX_CENTS = 50

        /** "" for zero, otherwise a signed offset like "+7¢" or "−14¢". */
        fun centsLabel(cents: Int): String = when {
            cents > 0 -> "+$cents¢"
            cents < 0 -> "−${-cents}¢"
            else -> ""
        }
    }
}

/**
 * An instrument tuning. With strings, the tuner targets the nearest string;
 * without (chromatic), it targets the nearest semitone.
 *
 * @property flats spell notes with flats (E♭) rather than sharps (D♯)
 */
data class Tuning(
    val id: String,
    val group: TuningGroup,
    val variant: String,
    val strings: List<TuningString>,
    val flats: Boolean = false
) {
    val isChromatic: Boolean get() = strings.isEmpty()

    /** Full label, e.g. "Guitar · DADGAD" or "Violin". */
    val name: String get() = if (group.prefixed) "${group.label} · $variant" else variant

    /** Index of the string closest to [midi] (a fractional MIDI number). */
    fun nearestString(midi: Double): Int =
        strings.indices.minBy { abs(strings[it].midi - midi) }
}

object Tunings {
    private fun preset(group: TuningGroup, key: String, variant: String, notes: String, flats: Boolean = false) =
        Tuning(
            id = "${group.name.lowercase()}_$key",
            group = group,
            variant = variant,
            strings = notes.split(' ').map { TuningString(Note.parse(it)) },
            flats = flats
        )

    val Chromatic = Tuning("chromatic", TuningGroup.Chromatic, "Chromatic", emptyList())

    val GuitarStandard = preset(TuningGroup.Guitar, "standard", "Standard", "E2 A2 D3 G3 B3 E4")
    val GuitarDropD = preset(TuningGroup.Guitar, "drop_d", "Drop D", "D2 A2 D3 G3 B3 E4")
    val GuitarHalfStepDown = preset(TuningGroup.Guitar, "half_step_down", "Half-step down", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4", flats = true)
    val GuitarDadgad = preset(TuningGroup.Guitar, "dadgad", "DADGAD", "D2 A2 D3 G3 A3 D4")
    val GuitarOpenG = preset(TuningGroup.Guitar, "open_g", "Open G", "D2 G2 D3 G3 B3 D4")
    val GuitarOpenD = preset(TuningGroup.Guitar, "open_d", "Open D", "D2 A2 D3 F#3 A3 D4")

    val BassStandard = preset(TuningGroup.Bass, "standard", "Standard", "E1 A1 D2 G2")
    val BassFiveString = preset(TuningGroup.Bass, "five_string", "5-string", "B0 E1 A1 D2 G2")

    val UkuleleStandard = preset(TuningGroup.Ukulele, "standard", "Standard", "G4 C4 E4 A4")
    val UkuleleLowG = preset(TuningGroup.Ukulele, "low_g", "Low G", "G3 C4 E4 A4")
    val UkuleleBaritone = preset(TuningGroup.Ukulele, "baritone", "Baritone", "D3 G3 B3 E4")

    val Violin = preset(TuningGroup.Strings, "violin", "Violin", "G3 D4 A4 E5")
    val Viola = preset(TuningGroup.Strings, "viola", "Viola", "C3 G3 D4 A4")
    val Cello = preset(TuningGroup.Strings, "cello", "Cello", "C2 G2 D3 A3")
    val Mandolin = preset(TuningGroup.Strings, "mandolin", "Mandolin", "G3 D4 A4 E5")

    val BanjoOpenG = preset(TuningGroup.Banjo, "open_g", "Open G", "G4 D3 G3 B3 D4")

    val all = listOf(
        Chromatic,
        GuitarStandard, GuitarDropD, GuitarHalfStepDown, GuitarDadgad, GuitarOpenG, GuitarOpenD,
        BassStandard, BassFiveString,
        UkuleleStandard, UkuleleLowG, UkuleleBaritone,
        Violin, Viola, Cello, Mandolin,
        BanjoOpenG
    )

    /** Custom tunings hold 1 to [MAX_CUSTOM_STRINGS] strings within [CUSTOM_NOTE_RANGE]. */
    const val MAX_CUSTOM_STRINGS = 8

    /** B0 to C6: inside what the detector can hear, with room to tune either side. */
    val CUSTOM_NOTE_RANGE = 23..84

    fun custom(id: String, name: String, strings: List<TuningString>, flats: Boolean) =
        Tuning(id, TuningGroup.Custom, name, strings, flats)

    fun byId(id: String?, custom: List<Tuning> = emptyList()): Tuning =
        all.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id } ?: Chromatic
}
