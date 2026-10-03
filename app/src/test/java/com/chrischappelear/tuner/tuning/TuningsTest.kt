package com.chrischappelear.tuner.tuning

import com.chrischappelear.tuner.audio.CaptureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningsTest {
    @Test
    fun idsAreUnique() {
        val ids = Tunings.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun byIdFindsPresetsThenCustomsThenFallsBack() {
        assertEquals(Tunings.BassStandard, Tunings.byId("bass_standard"))
        val custom = Tunings.custom("custom_1", "Mine", listOf(TuningString(Note(40))), flats = false)
        assertEquals(custom, Tunings.byId("custom_1", listOf(custom)))
        assertEquals(Tunings.Chromatic, Tunings.byId("custom_1"))
        assertEquals(Tunings.Chromatic, Tunings.byId(null))
    }

    @Test
    fun namesArePrefixedByGroup() {
        assertEquals("Guitar · DADGAD", Tunings.GuitarDadgad.name)
        assertEquals("Violin", Tunings.Violin.name)
        assertEquals("Chromatic", Tunings.Chromatic.name)
    }

    @Test
    fun presetStringsFitTheEditorRange() {
        for (tuning in Tunings.all) {
            assertTrue(tuning.name, tuning.strings.all { it.note.midi in Tunings.CUSTOM_NOTE_RANGE })
        }
    }

    @Test
    fun lowGUkuleleTargetsItsOwnGString() {
        val g3 = Note.parse("G3").midi.toDouble()
        assertEquals(0, Tunings.UkuleleLowG.nearestString(g3))
        // The re-entrant tuning has no G3, so the same note lands on its C string.
        assertEquals(1, Tunings.UkuleleStandard.nearestString(g3))
    }

    @Test
    fun halfStepDownIsSpelledWithFlats() {
        val names = Tunings.GuitarHalfStepDown.strings.map { it.label(Tunings.GuitarHalfStepDown.flats) }
        assertEquals(listOf("E♭", "A♭", "D♭", "G♭", "B♭", "E♭"), names)
    }

    @Test
    fun microtonalStringsTargetTheirOffsetPitch() {
        val string = TuningString(Note.parse("E4"), cents = -14)
        assertEquals(63.86, string.midi, 1e-9)
        assertEquals("E−14¢", string.label(flats = false))
        assertEquals("G+7¢", TuningString(Note.parse("G3"), cents = 7).label(flats = false))
        assertEquals("E", TuningString(Note.parse("E4")).label(flats = false))
    }

    @Test
    fun nearestStringHonoursOffsets() {
        // Two strings a quarter-tone apart: each claims the pitches nearest its own target.
        val quarterTones = Tunings.custom(
            "custom_q", "Quarter", listOf(TuningString(Note(60)), TuningString(Note(60), cents = 50)), flats = false
        )
        assertEquals(0, quarterTones.nearestString(60.1))
        assertEquals(1, quarterTones.nearestString(60.4))
    }

    @Test
    fun bassTuningsUseTheLowProfile() {
        assertEquals(CaptureProfile.Low, CaptureProfile.forTuning(Tunings.BassStandard))
        assertEquals(CaptureProfile.Low, CaptureProfile.forTuning(Tunings.BassFiveString))
        val sevenString = Tunings.custom("custom_7", "7-string", listOf(TuningString(Note.parse("B1")), TuningString(Note.parse("E2"))), flats = false)
        assertEquals(CaptureProfile.Low, CaptureProfile.forTuning(sevenString))
    }

    @Test
    fun everythingElseUsesTheStandardProfile() {
        for (tuning in listOf(Tunings.Chromatic, Tunings.GuitarStandard, Tunings.GuitarDropD, Tunings.Cello)) {
            assertEquals(tuning.name, CaptureProfile.Standard, CaptureProfile.forTuning(tuning))
        }
    }

    @Test
    fun everyProfileKeepsTheTransientRatio() {
        for (profile in listOf(CaptureProfile.Standard, CaptureProfile.Low)) {
            assertEquals(4, profile.windowSize / profile.hopSize)
        }
    }
}
