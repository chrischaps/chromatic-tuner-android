package com.chrischappelear.tuner.audio

import com.chrischappelear.tuner.audio.SignalGen.plus
import com.chrischappelear.tuner.tuning.TunerStatus
import com.chrischappelear.tuner.tuning.TuningProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin

/**
 * The drone through the speaker reaches the microphone along with the voice. These
 * mix the two as the microphone would and check that what's read is the voice.
 */
class DroneCancellerTest {
    private val sampleRate = 48_000
    private val profile = CaptureProfile.Standard

    private fun midi(m: Double) = 440.0 * exp((m - 69) / 12.0 * ln(2.0))

    private fun tanpura(frequency: Double, seconds: Double): FloatArray {
        val out = FloatArray((seconds * sampleRate).toInt())
        Tanpura(frequency, sampleRate).render(out)
        return out
    }

    /** A held, sung-like note: eight harmonics and a slow, shallow vibrato. */
    private fun voice(frequency: Double, seconds: Double, rms: Double, vibratoCents: Double = 0.0): FloatArray {
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            phase += 2 * PI * frequency * Math.pow(2.0, vibratoCents * sin(2 * PI * 5.0 * t) / 1200) / sampleRate
            var v = 0.0
            for (h in 1..8) v += sin(h * phase + h * 0.7) / h
            out[i] = v.toFloat()
        }
        return scaleTo(out, rms)
    }

    private fun scaleTo(signal: FloatArray, rms: Double): FloatArray {
        val gain = (rms / SignalGen.rms(signal)).toFloat()
        return FloatArray(signal.size) { signal[it] * gain }
    }

    /** Slides a window along [signal] a hop at a time, as AudioRecorder does. */
    private fun readings(signal: FloatArray, drone: Double?): List<PitchEstimate?> {
        val sound = drone?.let { DroneSound(it) }
        val canceller = DroneCanceller(profile)
        val window = FloatArray(profile.windowSize)
        val out = mutableListOf<PitchEstimate?>()
        var filled = 0
        var at = 0
        while (at + profile.hopSize <= signal.size) {
            System.arraycopy(window, profile.hopSize, window, 0, profile.windowSize - profile.hopSize)
            System.arraycopy(signal, at, window, profile.windowSize - profile.hopSize, profile.hopSize)
            at += profile.hopSize
            filled = minOf(filled + profile.hopSize, profile.windowSize)
            out += if (filled == profile.windowSize) canceller.detect(window, sampleRate, sound) else null
        }
        return out
    }

    /** Readings from [seconds] on. */
    private fun from(seconds: Double, readings: List<PitchEstimate?>) =
        readings.drop((seconds * sampleRate / profile.hopSize).toInt())

    /** Silence, then [signal]. */
    private fun after(seconds: Double, signal: FloatArray): FloatArray {
        val start = (seconds * sampleRate).toInt()
        return FloatArray(start + signal.size).also { signal.copyInto(it, start) }
    }

    /** What the detector hears of [signal]: it ignores everything above 2 kHz. */
    private fun belowTwoKilohertz(signal: FloatArray): FloatArray {
        val a = (1 - exp(-2 * PI * 2000.0 / sampleRate)).toFloat()
        var y1 = 0f
        var y2 = 0f
        return FloatArray(signal.size) {
            y1 += a * (signal[it] - y1)
            y2 += a * (y1 - y2)
            y2
        }
    }

    private fun cents(frequency: Double, target: Double) = 1200 * log2(frequency / target)

    /**
     * The drone starts, and the voice comes in [entry] seconds later, as someone would
     * start a drone and then sing against it. Readings are checked from a second after.
     */
    private fun assertReadsVoice(
        droneHz: Double,
        voiceHz: Double,
        voiceToDrone: Double,
        label: String,
        entry: Double = 2.0,
        toleranceCents: Double = 3.0
    ) {
        val drone = tanpura(droneHz, entry + 4.0)
        val droneRms = SignalGen.rms(drone)
        val sung = after(entry, voice(voiceHz, 4.0, droneRms * voiceToDrone, vibratoCents = 15.0))
        val frames = from(entry + 1.0, readings(drone + sung, droneHz))
        val read = frames.filterNotNull().filter { it.clarity >= TuningProcessor.SUSTAIN_CLARITY }
        assertTrue("$label: read only ${read.size} of ${frames.size} frames", read.size >= frames.size * 0.85)
        val errors = read.map { cents(it.frequency, voiceHz) }.sorted()
        val median = errors[errors.size / 2]
        // The vibrato swings ±15¢, so individual frames wander; the middle must not.
        assertTrue("$label: median %.1f¢ off".format(median), abs(median) <= toleranceCents)
        val wrong = errors.count { abs(it) > 60 }
        assertTrue("$label: $wrong frames read another note", wrong <= frames.size / 50)
    }

    @Test
    fun tanpuraIsInTuneAndRepeatsEveryPeriod() {
        for (m in listOf(40, 50, 57, 62, 69, 76)) {
            val f = midi(m.toDouble())
            val drone = tanpura(f, 5.0)
            val detector = PitchDetector(profile.windowSize, profile.minFrequency, profile.maxFrequency)
            val start = (2.5 * sampleRate).toInt()
            val estimate = detector.detect(drone.copyOfRange(start, start + profile.windowSize), sampleRate)!!
            assertEquals("tanpura on MIDI $m", 0.0, cents(estimate.frequency, f), 1.0)

            // Subtracting the sound one period ago leaves little of it, in the band that matters.
            val heard = belowTwoKilohertz(drone)
            val period = sampleRate / f
            val residual = FloatArray(heard.size) { i ->
                val t = i - period
                val k = t.toInt()
                if (k < 1) 0f else heard[i] - (heard[k] + (heard[k + 1] - heard[k]) * (t - k).toFloat())
            }
            val ratio = SignalGen.rms(residual.copyOfRange(sampleRate, heard.size)) /
                SignalGen.rms(heard.copyOfRange(sampleRate, heard.size))
            assertTrue("MIDI $m leaves %.1f dB".format(20 * kotlin.math.log10(ratio)), ratio < 0.1)
        }
    }

    @Test
    fun droneAloneIsSilence() {
        for (m in listOf(38, 50, 57, 62, 69, 79)) {
            val f = midi(m.toDouble())
            val processor = TuningProcessor()
            var now = 0L
            val frames = readings(tanpura(f, 10.0), f)
            val active = frames.withIndex().count { (i, estimate) ->
                now += 21
                val state = processor.process(estimate, now)
                i > 30 && state.status == TunerStatus.Active
            }
            assertEquals("drone on MIDI $m was read as a note", 0, active)
        }
    }

    @Test
    fun voiceAtEveryIntervalIsRead() {
        for (root in listOf(50, 45)) { // D3, A2
            for (interval in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 16, -5, -7, -9)) {
                val voice = midi(root + interval.toDouble())
                for (level in listOf(0.5, 1.0, 4.0)) {
                    assertReadsVoice(midi(root.toDouble()), voice, level, "MIDI $root + $interval at $level×")
                }
            }
        }
    }

    @Test
    fun voiceOnAHarmonicIsReadWhenLouderThanTheDrone() {
        val drone = midi(57.0) // A3
        for ((semitones, label) in listOf(0 to "unison", 12 to "octave up", 19 to "twelfth", -12 to "octave down")) {
            for (detune in listOf(0.0, 8.0, -20.0)) {
                // 20¢ off a twelfth, the vibrato swings in and out of where the comb takes over,
                // and the reading can come out several cents further off than the voice.
                val tolerance = if (semitones == 19 && detune < 0) 8.0 else 3.0
                assertReadsVoice(drone, midi(57.0 + semitones + detune / 100), 4.0, "$label $detune¢", toleranceCents = tolerance)
            }
        }
    }

    @Test
    fun withoutCancellingTheFifthReadsWrong() {
        // Why this exists: drone and fifth together repeat an octave below the drone.
        val f = midi(50.0)
        val drone = tanpura(f, 5.0)
        val mix = drone + voice(f * 1.5, 5.0, SignalGen.rms(drone))
        val frames = from(2.0, readings(mix, drone = null)).filterNotNull()
        val right = frames.count { abs(cents(it.frequency, f * 1.5)) < 50 }
        assertTrue("plain detection read the fifth in $right of ${frames.size}", right < frames.size / 2)
    }

    @Test
    fun withoutADroneItIsThePlainDetector() {
        val tone = SignalGen.pluck(196.0, sampleRate, sampleRate, decayPerSecond = 0.5)
        val frames = readings(tone, drone = null).filterNotNull()
        assertTrue(frames.isNotEmpty())
        frames.forEach { assertEquals(0.0, cents(it.frequency, 196.0), 1.0) }
    }
}
