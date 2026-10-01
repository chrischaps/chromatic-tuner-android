package com.chrischappelear.tuner.audio

import com.chrischappelear.tuner.audio.SignalGen.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2

class PitchDetectorTest {
    private val sampleRate = 48_000
    private val detector = PitchDetector()
    private val n = detector.windowSize

    private fun midi(m: Int) = 440.0 * exp((m - 69) / 12.0 * ln(2.0))
    private fun cents(measured: Double, expected: Double) = 1200 * log2(measured / expected)

    private fun assertWithinCents(expected: Double, signal: FloatArray, tolerance: Double, rate: Int = sampleRate) {
        val estimate = detector.detect(signal, rate)
        assertNotNull("no pitch found for $expected Hz", estimate)
        val error = cents(estimate!!.frequency, expected)
        assertTrue(
            "expected %.2f Hz, got %.3f Hz (%.2f cents)".format(expected, estimate.frequency, error),
            abs(error) <= tolerance
        )
    }

    // C2, D2, E2, A2, D3, G3, B3, E4 and the ukulele's G4 C4 E4 A4, plus high-fret notes.
    private val targets = listOf(36, 38, 40, 45, 50, 55, 59, 64, 60, 67, 69, 76, 84, 88).map(::midi) + 1000.0

    @Test
    fun pureSinesAreAccurateToOneCent() {
        for (f in targets) assertWithinCents(f, SignalGen.sine(f, sampleRate, n), 1.0)
    }

    @Test
    fun pureSinesAt44100AreAccurate() {
        for (f in targets) {
            assertWithinCents(f, SignalGen.sine(f, 44_100, n), 1.0, rate = 44_100)
        }
    }

    @Test
    fun harmonicRichPlucksAreAccurateToOneCent() {
        for (f in targets) assertWithinCents(f, SignalGen.pluck(f, sampleRate, n), 1.0)
    }

    @Test
    fun detunedNotesReadTheirTrueOffset() {
        val e2 = midi(40)
        for (offset in listOf(-37.0, -12.0, -3.0, 3.0, 12.0, 37.0)) {
            val f = e2 * exp(offset / 1200 * ln(2.0))
            assertWithinCents(f, SignalGen.pluck(f, sampleRate, n), 1.0)
        }
    }

    @Test
    fun missingFundamentalDoesNotJumpAnOctave() {
        val e2 = midi(40)
        val signal = SignalGen.pluck(e2, sampleRate, n, harmonics = 2..6)
        assertWithinCents(e2, signal, 1.0)
    }

    @Test
    fun strongSecondHarmonicDoesNotJumpAnOctave() {
        val a2 = midi(45)
        val signal = SignalGen.sine(a2, sampleRate, n, amplitude = 0.15) +
            SignalGen.sine(a2 * 2, sampleRate, n, amplitude = 0.5)
        assertWithinCents(a2, signal, 1.0)
    }

    @Test
    fun noisyTonesStayWithinThreeCents() {
        for (f in targets) {
            val tone = SignalGen.pluck(f, sampleRate, n)
            // 10 dB SNR: noise RMS is tone RMS / sqrt(10).
            val noise = SignalGen.noise(n, SignalGen.rms(tone) / Math.sqrt(10.0), seed = f.toLong())
            assertWithinCents(f, tone + noise, 3.0)
        }
    }

    @Test
    fun silenceReturnsNull() {
        assertEquals(null, detector.detect(FloatArray(n), sampleRate))
    }

    @Test
    fun whiteNoiseHasLowClarity() {
        val estimate = detector.detect(SignalGen.noise(n, 0.1), sampleRate)
        assertTrue("noise clarity was ${estimate?.clarity}", estimate == null || estimate.clarity < 0.6)
    }

    @Test
    fun cleanToneHasHighClarity() {
        val estimate = detector.detect(SignalGen.pluck(midi(45), sampleRate, n), sampleRate)
        assertTrue(estimate!!.clarity > 0.95)
    }

    @Test
    fun fftRoundTripsExactly() {
        val fft = FFT(64)
        val re = DoubleArray(64) { Math.sin(it * 0.3) + it % 5 }
        val original = re.copyOf()
        val im = DoubleArray(64)
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in re.indices) assertEquals(original[i], re[i], 1e-9)
    }
}
