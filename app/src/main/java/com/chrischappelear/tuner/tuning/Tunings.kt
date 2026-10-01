package com.chrischappelear.tuner.tuning

import kotlin.math.abs

/**
 * An instrument tuning. With strings, the tuner targets the nearest string;
 * without (chromatic), it targets the nearest semitone.
 */
data class Tuning(
    val id: String,
    val name: String,
    val strings: List<Note>
) {
    val isChromatic: Boolean get() = strings.isEmpty()

    /** Index of the string closest to [midi] (a fractional MIDI number). */
    fun nearestString(midi: Double): Int =
        strings.indices.minBy { abs(strings[it].midi - midi) }
}

object Tunings {
    private fun notes(vararg names: String) = names.map(Note::parse)

    val Chromatic = Tuning("chromatic", "Chromatic", emptyList())
    val GuitarStandard = Tuning("guitar_standard", "Guitar · Standard", notes("E2", "A2", "D3", "G3", "B3", "E4"))
    val GuitarDropD = Tuning("guitar_drop_d", "Guitar · Drop D", notes("D2", "A2", "D3", "G3", "B3", "E4"))
    val Ukulele = Tuning("ukulele", "Ukulele · GCEA", notes("G4", "C4", "E4", "A4"))

    val all = listOf(Chromatic, GuitarStandard, GuitarDropD, Ukulele)

    fun byId(id: String?): Tuning = all.firstOrNull { it.id == id } ?: Chromatic
}
