package com.chrischappelear.tuner.tuning

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt

/** A note of the equal-tempered scale, identified by its MIDI number (A4 = 69). */
data class Note(val midi: Int) {
    val octave: Int get() = Math.floorDiv(midi, 12) - 1

    /** Spelled with sharps, e.g. "C♯4". */
    val displayName: String get() = spelled(flats = false).displayName

    /** How this note is written, with sharps (C♯) or flats (D♭). */
    fun spelled(flats: Boolean): Spelling {
        val name = (if (flats) FLAT_NAMES else SHARP_NAMES)[Math.floorMod(midi, 12)]
        return Spelling(letter = name.substring(0, 1), accidental = name.substring(1), octave = octave)
    }

    fun frequency(a4: Double = NoteMath.DEFAULT_A4): Double = NoteMath.midiToFrequency(midi.toDouble(), a4)

    /** A written note: [letter] alone, [accidental] "♯", "♭" or "". */
    data class Spelling(val letter: String, val accidental: String, val octave: Int) {
        val name: String get() = letter + accidental
        val displayName: String get() = "$name$octave"
    }

    companion object {
        private val SHARP_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        private val FLAT_NAMES = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
        private const val LETTERS = "C_D_EF_G_A_B"

        /** Parses names like "E2", "C#4", "C♯4", "Bb3" or "E♭2". */
        fun parse(text: String): Note {
            require(text.isNotEmpty()) { "Empty note name" }
            val semitone = LETTERS.indexOf(text[0].uppercaseChar())
            require(semitone >= 0 && text[0] != '_') { "Unknown note name: $text" }
            val accidental = when (text.getOrNull(1)) {
                '#', '♯' -> 1
                'b', '♭' -> -1
                else -> 0
            }
            val octave = text.substring(if (accidental == 0) 1 else 2).toIntOrNull()
                ?: throw IllegalArgumentException("Unknown note name: $text")
            return Note((octave + 1) * 12 + semitone + accidental)
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
