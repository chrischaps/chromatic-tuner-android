package com.chrischappelear.tuner.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2

/** The reference tone is only useful if the tuner itself agrees it's in tune. */
class PluckVoiceTest {
    private val sampleRate = 48_000

    private fun midi(m: Double) = 440.0 * exp((m - 69) / 12.0 * ln(2.0))

    private fun render(frequency: Double, seconds: Double, rate: Int = sampleRate): FloatArray {
        val out = FloatArray((seconds * rate).toInt())
        PluckVoice(frequency, rate).render(out)
        return out
    }

    private fun assertInTune(frequency: Double, profile: CaptureProfile, rate: Int = sampleRate, atSeconds: Double = 0.5) {
        val tone = render(frequency, atSeconds + 0.3, rate)
        val start = (atSeconds * rate).toInt()
        val window = tone.copyOfRange(start, start + profile.windowSize)
        val detector = PitchDetector(profile.windowSize, profile.minFrequency, profile.maxFrequency)
        val estimate = detector.detect(window, rate)
        assertNotNull("no pitch found for $frequency Hz", estimate)
        val error = 1200 * log2(estimate!!.frequency / frequency)
        assertTrue("%.2f Hz read %.2f cents off".format(frequency, error), abs(error) <= 0.5)
        assertTrue("clarity ${estimate.clarity} for $frequency Hz", estimate.clarity >= 0.95)
    }

    @Test
    fun everyPresetStringIsInTune() {
        // Guitar, ukulele, violin and banjo strings, plus the top of the custom range.
        for (m in listOf(36, 38, 40, 43, 45, 48, 50, 55, 59, 60, 62, 64, 67, 69, 76, 84)) {
            assertInTune(midi(m.toDouble()), CaptureProfile.Standard)
        }
    }

    @Test
    fun bassStringsAreInTune() {
        // B0, E1, A1, D2, G2 through the bass profile.
        for (m in listOf(23, 28, 33, 38, 43)) assertInTune(midi(m.toDouble()), CaptureProfile.Low)
    }

    @Test
    fun microtonalAndRetunedPitchesAreExact() {
        assertInTune(midi(64 - 0.14), CaptureProfile.Standard)
        assertInTune(midi(52 + 0.37), CaptureProfile.Standard)
        assertInTune(432.0, CaptureProfile.Standard)
        assertInTune(446.0 / 4, CaptureProfile.Standard)
    }

    @Test
    fun staysInTuneAsItDecaysAndAt44100() {
        assertInTune(midi(40.0), CaptureProfile.Standard, atSeconds = 2.5)
        assertInTune(midi(76.0), CaptureProfile.Standard, atSeconds = 2.0)
        assertInTune(midi(45.0), CaptureProfile.Standard, rate = 44_100)
        assertInTune(midi(28.0), CaptureProfile.Low, rate = 44_100)
    }

    @Test
    fun ringsForItsDurationThenFinishes() {
        val voice = PluckVoice(110.0, sampleRate)
        val out = FloatArray((PluckVoice.DURATION_SECONDS * sampleRate).toInt() + 1_000)
        voice.render(out)
        assertTrue(voice.finished)
        val end = (PluckVoice.DURATION_SECONDS * sampleRate).toInt()
        assertTrue(out.drop(end).all { it == 0f })
        assertTrue("audible in the middle", SignalGen.rms(out.copyOfRange(sampleRate, 2 * sampleRate)) > 0.02)
    }

    @Test
    fun staysWithinFullScaleWithoutOffset() {
        for (f in listOf(30.87, 82.41, 440.0, 1046.5)) {
            val tone = render(f, PluckVoice.DURATION_SECONDS)
            assertTrue("clips at $f Hz", tone.all { abs(it) <= 1f })
            assertEquals("offset at $f Hz", 0.0, tone.average(), 0.01)
        }
    }

    @Test
    fun releaseDampsWithinAFewMilliseconds() {
        val voice = PluckVoice(196.0, sampleRate)
        voice.render(FloatArray(sampleRate / 2))
        voice.release()
        val tail = FloatArray(sampleRate / 10)
        voice.render(tail)
        assertTrue(voice.finished)
        // 30 ms of fade, then silence.
        assertTrue(tail.drop(sampleRate * 31 / 1000).all { it == 0f })
    }

    @Test
    fun theSameNoteSoundsTheSameEachTime() {
        assertArrayEquals(render(146.83, 0.2), render(146.83, 0.2), 0f)
    }
}
