package com.chrischappelear.tuner.tuning

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {
    @Test
    fun namesAndOctaves() {
        assertEquals("A4", Note(69).displayName)
        assertEquals("C4", Note(60).displayName)
        assertEquals("E2", Note(40).displayName)
        assertEquals("C♯-1", Note(1).displayName)
        assertEquals("B-2", Note(-1).displayName)
    }

    @Test
    fun parseRoundTrips() {
        for (midi in 0..127) assertEquals(midi, Note.parse(Note(midi).displayName).midi)
        assertEquals(61, Note.parse("C#4").midi)
    }

    @Test
    fun flatsParseToTheSameNoteAsSharps() {
        assertEquals(Note.parse("A#3"), Note.parse("Bb3"))
        assertEquals(Note.parse("D♯2"), Note.parse("E♭2"))
        assertEquals(Note.parse("B3"), Note.parse("Cb4"))
        assertEquals(Note.parse("F4"), Note.parse("E#4"))
    }

    @Test
    fun spellingFollowsThePreference() {
        val note = Note.parse("Eb2")
        assertEquals("D♯2", note.spelled(flats = false).displayName)
        assertEquals("E♭2", note.spelled(flats = true).displayName)
        assertEquals("E", note.spelled(flats = true).letter)
        assertEquals("♭", note.spelled(flats = true).accidental)
        assertEquals("A2", Note.parse("A2").spelled(flats = true).displayName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownLetterIsRejected() {
        Note.parse("H2")
    }

    @Test
    fun frequencies() {
        assertEquals(440.0, Note(69).frequency(), 1e-9)
        assertEquals(82.4069, Note(40).frequency(), 1e-3)
        assertEquals(432.0, Note(69).frequency(432.0), 1e-9)
    }

    @Test
    fun nearestNoteHonoursA4() {
        assertEquals(69, NoteMath.nearestNote(440.0).midi)
        assertEquals(69, NoteMath.nearestNote(432.0, a4 = 432.0).midi)
        // 432 Hz is about 32 cents flat of A4 at standard pitch.
        assertEquals(-31.77, NoteMath.cents(432.0, Note(69)), 0.01)
        assertEquals(0.0, NoteMath.cents(432.0, Note(69), a4 = 432.0), 1e-9)
    }

    @Test
    fun centsAreSignedAndUnTruncated() {
        val sharp = Note(69).frequency() * Math.pow(2.0, 0.6 / 1200)
        assertEquals(0.6, NoteMath.cents(sharp, Note(69)), 1e-6)
    }

    @Test
    fun presetsTargetNearestString() {
        val guitar = Tunings.GuitarStandard
        assertEquals(0, guitar.nearestString(NoteMath.frequencyToMidi(80.0)))
        assertEquals(1, guitar.nearestString(NoteMath.frequencyToMidi(112.0)))
        assertEquals(5, guitar.nearestString(NoteMath.frequencyToMidi(330.0)))
        assertEquals(0, Tunings.GuitarDropD.nearestString(NoteMath.frequencyToMidi(73.0)))
    }
}
