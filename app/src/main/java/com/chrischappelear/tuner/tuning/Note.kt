package com.chrischappelear.tuner.tuning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.round

data class Note(
    val name: String,
    val frequency: Double,
    val octave: Int
) {
    fun getDisplayName(): String = "$name$octave"
}

object NoteFrequencies {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    
    private val A4_FREQUENCY = 440.0
    private val A4_MIDI_NUMBER = 69
    
    fun frequencyToMidiNumber(frequency: Double): Double {
        if (frequency <= 0) return 0.0
        return 12 * log2(frequency / A4_FREQUENCY) + A4_MIDI_NUMBER
    }
    
    fun midiNumberToFrequency(midiNumber: Double): Double {
        val exponent = (midiNumber - A4_MIDI_NUMBER) / 12.0
        return A4_FREQUENCY * exp(exponent * ln(2.0))
    }
    
    fun getClosestNote(frequency: Double): Note {
        if (frequency <= 0) return Note("", 0.0, 0)
        
        val midiNumber = frequencyToMidiNumber(frequency)
        val closestMidi = round(midiNumber).toInt()
        val closestFrequency = midiNumberToFrequency(closestMidi.toDouble())
        
        val noteIndex = (closestMidi - 12) % 12
        val octave = (closestMidi - 12) / 12
        val noteName = noteNames[noteIndex]
        
        return Note(noteName, closestFrequency, octave)
    }
    
    fun getCentsOffset(frequency: Double, targetNote: Note): Int {
        if (frequency <= 0 || targetNote.frequency <= 0) return 0
        
        val cents = 1200 * log2(frequency / targetNote.frequency)
        return cents.toInt()
    }
    
    fun isInTune(centsOffset: Int, tolerance: Int = 10): Boolean {
        return abs(centsOffset) <= tolerance
    }
}