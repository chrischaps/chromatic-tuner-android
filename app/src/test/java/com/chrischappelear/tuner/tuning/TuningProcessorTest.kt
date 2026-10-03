package com.chrischappelear.tuner.tuning

import com.chrischappelear.tuner.audio.PitchEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TuningProcessorTest {
    private val frameMs = 21L
    private var now = 0L

    private fun detuned(note: Note, cents: Double, a4: Double = 440.0) =
        note.frequency(a4) * Math.pow(2.0, cents / 1200)

    private fun TuningProcessor.feed(frequency: Double?, frames: Int = 1, clarity: Double = 0.97): TunerState {
        var state = TunerState()
        repeat(frames) {
            now += frameMs
            val estimate = frequency?.let { PitchEstimate(it, clarity = clarity, rms = 0.05) }
            state = process(estimate, now)
        }
        return state
    }

    private val a2 = Note.parse("A2")

    @Test
    fun microtonalStringReadsInTuneAtItsOffset() {
        val e4 = Note.parse("E4")
        val justThird = Tunings.custom(
            "custom_j", "Just", listOf(TuningString(Note.parse("C4")), TuningString(e4, cents = -14)), flats = false
        )
        val p = TuningProcessor(justThird)
        // Exactly 14 cents below equal-tempered E4 is in tune with this string.
        val atTarget = p.feed(detuned(e4, -14.0), 40)
        assertEquals(1, atTarget.stringIndex)
        assertEquals(-14, atTarget.stringCents)
        assertEquals(0.0, atTarget.cents, 0.2)
        assertTrue(atTarget.locked)
        // Equal-tempered E4 is then 14 cents sharp of it.
        assertEquals(14.0, p.feed(e4.frequency(), 60).cents, 0.3)
    }

    @Test
    fun bassTuningTracksItsLowString() {
        // The Low profile reads about every 43 ms.
        val p = TuningProcessor(Tunings.BassStandard)
        val e1 = Note.parse("E1")
        var state = TunerState()
        repeat(30) {
            now += 43
            state = p.process(PitchEstimate(detuned(e1, -2.0), clarity = 0.95, rms = 0.02), now)
        }
        assertEquals(TunerStatus.Active, state.status)
        assertEquals(0, state.stringIndex)
        assertEquals(e1, state.note)
        assertTrue(state.locked)
        assertEquals(setOf(0), state.tunedStrings)
    }

    @Test
    fun startsIdleAndWaitsForOnset() {
        val p = TuningProcessor()
        assertEquals(TunerStatus.Idle, p.feed(null, 5).status)
        assertEquals(TunerStatus.Idle, p.feed(110.0, 2).status)
        val state = p.feed(110.0)
        assertEquals(TunerStatus.Active, state.status)
        assertEquals(a2, state.note)
    }

    @Test
    fun erraticTransientDoesNotStartANote() {
        val p = TuningProcessor()
        for (f in listOf(1250.0, 900.0, 1400.0, 700.0, 1100.0, 640.0)) {
            assertEquals(TunerStatus.Idle, p.feed(f).status)
        }
        assertEquals(TunerStatus.Active, p.feed(110.0, 3).status)
    }

    @Test
    fun settlesOnTheTrueOffset() {
        val p = TuningProcessor()
        val state = p.feed(detuned(a2, 12.0), 60)
        assertEquals(12.0, state.cents, 0.2)
    }

    @Test
    fun singleOctaveBlipIsIgnored() {
        val p = TuningProcessor()
        p.feed(110.0, 20)
        val blip = p.feed(220.0)
        assertEquals(a2, blip.note)
        assertTrue(abs(blip.cents) < 1.0)
        assertEquals(a2, p.feed(110.0, 3).note)
    }

    @Test
    fun murkyStrayRunDoesNotBendTheReading() {
        val p = TuningProcessor()
        p.feed(110.0, 30)
        // A run of low-clarity sharp frames, longer than the median can absorb.
        val during = p.feed(detuned(a2, 40.0), 3, clarity = 0.65)
        assertEquals(TunerStatus.Active, during.status)
        assertEquals(0.0, during.cents, 0.5)
        assertEquals(0.0, p.feed(110.0, 5).cents, 0.5)
    }

    @Test
    fun transientOctaveSlipIsHeld() {
        val p = TuningProcessor()
        val a4 = Note(69)
        p.feed(440.0, 30)
        // Exactly what a click does on the device: four windows read an octave low.
        val during = p.feed(220.0, 4, clarity = 0.85)
        assertEquals(TunerStatus.Active, during.status)
        assertEquals(a4, during.note)
        assertEquals(0.0, during.cents, 0.5)
        val after = p.feed(440.0, 10)
        assertEquals(a4, after.note)
        assertTrue(p.history.snapshot().all { it.cents == null || abs(it.cents!!) < 1f })
    }

    @Test
    fun sustainedOctaveChangeIsFollowed() {
        val p = TuningProcessor()
        p.feed(440.0, 30)
        assertEquals(Note(57), p.feed(220.0, 20).note)
    }

    @Test
    fun clearRetuningIsFollowed() {
        val p = TuningProcessor()
        p.feed(110.0, 30)
        assertEquals(20.0, p.feed(detuned(a2, 20.0), 40, clarity = 0.9).cents, 0.5)
    }

    @Test
    fun hysteresisHoldsNoteNearTheBoundary() {
        val p = TuningProcessor()
        p.feed(detuned(a2, 40.0), 20)
        // 55 cents sharp of A2 rounds to A♯2, but is inside the hysteresis band.
        val state = p.feed(detuned(a2, 55.0), 40)
        assertEquals(a2, state.note)
        assertEquals(55.0, state.cents, 1.0)
    }

    @Test
    fun switchesNotesOncePitchClearlyMoves() {
        val p = TuningProcessor()
        p.feed(110.0, 20)
        val d3 = Note.parse("D3")
        assertEquals(d3, p.feed(d3.frequency(), 8).note)
    }

    @Test
    fun noteSwitchNeverShowsAnOffsetAgainstTheOldNote() {
        for (tuning in listOf(Tunings.Chromatic, Tunings.GuitarStandard)) {
            val p = TuningProcessor(tuning)
            p.feed(110.0, 20)
            repeat(12) {
                val state = p.feed(Note.parse("D3").frequency())
                assertTrue("${tuning.id}: ${state.note} ${state.cents}", abs(state.cents) < 5)
            }
            assertTrue(p.history.snapshot().all { it.cents == null || abs(it.cents!!) < 5f })
        }
    }

    @Test
    fun locksAfterHoldingInTune() {
        val p = TuningProcessor()
        val early = p.feed(detuned(a2, 1.0), 10)
        assertFalse(early.locked)
        val held = p.feed(detuned(a2, 1.0), 25)
        assertTrue(held.locked)
        assertEquals(1, held.lockCount)
        // Staying locked does not re-trigger.
        assertEquals(1, p.feed(detuned(a2, 1.0), 25).lockCount)
    }

    @Test
    fun driftingAwayReleasesTheLock() {
        val p = TuningProcessor()
        p.feed(110.0, 40)
        assertFalse(p.feed(detuned(a2, 20.0), 40).locked)
    }

    @Test
    fun briefDropoutDoesNotFlicker() {
        val p = TuningProcessor()
        p.feed(110.0, 20)
        assertEquals(TunerStatus.Active, p.feed(null, 2).status)
    }

    @Test
    fun silenceFadesThenGoesIdle() {
        val p = TuningProcessor()
        p.feed(110.0, 20)
        val fading = p.feed(null, 10)
        assertEquals(TunerStatus.Fading, fading.status)
        assertEquals(a2, fading.note)
        val idle = p.feed(null, (TuningProcessor.FADE_MS / frameMs).toInt() + 1)
        assertEquals(TunerStatus.Idle, idle.status)
        assertNull(idle.note)
    }

    @Test
    fun presetTargetsStringsAndRemembersTunedOnes() {
        val p = TuningProcessor(Tunings.GuitarStandard)
        // 30 cents flat of A2 is still the A string, not a chromatic G♯.
        val flat = p.feed(detuned(a2, -30.0), 30)
        assertEquals(1, flat.stringIndex)
        assertEquals(a2, flat.note)
        assertEquals(-30.0, flat.cents, 1.0)

        val tuned = p.feed(110.0, 60)
        assertTrue(tuned.locked)
        assertEquals(setOf(1), tuned.tunedStrings)
    }

    @Test
    fun presetReportsLargeOffsetsWhenFarOff() {
        val p = TuningProcessor(Tunings.GuitarStandard)
        val e2 = Note.parse("E2")
        // A low E string tuned down a whole step still targets the E string.
        val state = p.feed(detuned(e2, -200.0), 40)
        assertEquals(0, state.stringIndex)
        assertEquals(-200.0, state.cents, 1.0)
    }

    @Test
    fun a4CalibrationShiftsTheReference() {
        val p = TuningProcessor(a4 = 432.0)
        val state = p.feed(432.0, 40)
        assertEquals(Note(69), state.note)
        assertEquals(0.0, state.cents, 0.2)
    }

    @Test
    fun historyRecordsGapsDuringSilence() {
        val p = TuningProcessor()
        p.feed(110.0, 10)
        p.feed(null, 10)
        val points = p.history.snapshot()
        assertEquals(20, points.size)
        assertTrue(points.last().cents == null)
        assertTrue(points[5].cents != null)
    }
}
