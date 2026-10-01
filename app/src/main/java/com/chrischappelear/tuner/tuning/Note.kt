package com.chrischappelear.tuner.tuning

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt

/** A note of the equal-tempered scale, identified by its MIDI number (A4 = 69). */
data class Note(val midi: Int) {
    val name: String get() = NAMES[Math.floorMod(midi, 12)]
    val octave: Int get() = Math.floorDiv(midi, 12) - 1

    /** The letter alone, e.g. "C" for C♯. */
    val letter: String get() = name.substring(0, 1)

    /** "♯" or "". */
    val accidental: String get() = name.substring(1)

    val displayName: String get() = "$name$octave"

    fun frequency(a4: Double = NoteMath.DEFAULT_A4): Double = NoteMath.midiToFrequency(midi.toDouble(), a4)

    companion object {
        private val NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

        /** Parses names like "E2", "C#4" or "C♯4". */
        fun parse(text: String): Note {
            val normalized = text.replace('#', '♯')
            val split = normalized.indexOfFirst { it.isDigit() || it == '-' }
            val name = normalized.substring(0, split)
            val octave = normalized.substring(split).toInt()
            val index = NAMES.indexOf(name)
            require(index >= 0) { "Unknown note name: $text" }
            return Note((octave + 1) * 12 + index)
        }
    }
}

object NoteMath {
    const val DEFAULT_A4 = 440.0
    private const val A4_MIDI = 69

    /** Fractional MIDI number of a frequency. */
    fun frequencyToMidi(frequency: Double, a4: Double = DEFAULT_A4): Double =
        12 * log2(frequency / a4) + A4_MIDI

    fun midiToFrequency(midi: Double, a4: Double = DEFAULT_A4): Double =
        a4 * exp((midi - A4_MIDI) / 12.0 * ln(2.0))

    fun nearestNote(frequency: Double, a4: Double = DEFAULT_A4): Note =
        Note(frequencyToMidi(frequency, a4).roundToInt())

    /** Signed distance in cents from [target] to [frequency]; positive is sharp. */
    fun cents(frequency: Double, target: Note, a4: Double = DEFAULT_A4): Double =
        (frequencyToMidi(frequency, a4) - target.midi) * 100
}
