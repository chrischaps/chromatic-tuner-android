package com.chrischappelear.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What the drone is playing, as far as the microphone is concerned. A change of
 * either means learning how loud it is all over again.
 */
data class DroneSound(val frequency: Double, val volume: Int = 0)

/**
 * Pitch detection that hears past the app's own drone.
 *
 * Through the phone speaker the microphone hears the drone, and a drone mixed with a
 * voice confuses any pitch detector: a voice a fifth above it reads as one note an
 * octave below the drone. But the drone repeats exactly once per period of its note
 * (see [Tanpura]), and so does everything the room does to it, so subtracting the
 * signal from itself one period ago, a comb filter, leaves almost nothing of it.
 * A voice at any other pitch passes through, so its reading comes from the cleaned
 * signal.
 *
 * The comb also cancels a voice on any harmonic of the drone: in unison, an octave
 * up, a twelfth up (three times the frequency), and so on, since such a voice repeats
 * once per drone period too. For those notes the reading comes from the raw signal
 * instead, but only while it's clearly louder than the drone alone; quieter than
 * that, a reading there would mostly be the drone.
 *
 * With no drone, this is just a [PitchDetector]. Call [detect] once per hop with the
 * whole window, newest samples last. Not thread-safe.
 */
class DroneCanceller(private val profile: CaptureProfile) {
    private val windowSize = profile.windowSize
    private val hopSize = profile.hopSize
    private val plain = detector()
    private val cleaned = detector()
    /**
     * A voice an octave above the drone, heard with it, repeats once per drone period
     * as well as once per its own; the voice's shorter period is to be preferred.
     */
    private val heard = PitchDetector(windowSize, profile.minFrequency, profile.maxFrequency, OVER_DRONE_PEAK)
    private val combed = FloatArray(windowSize)

    private var sound: DroneSound? = null
    private var droneHz = 0.0
    private var period = 0.0
    private var frames = 0
    /** How loud the drone alone is at the microphone, from the quietest recent windows. */
    private var droneLevel = 0.0

    fun detect(window: FloatArray, sampleRate: Int, drone: DroneSound?): PitchEstimate? {
        if (drone == null) {
            sound = null
            return plain.detect(window, sampleRate)
        }
        if (drone != sound) {
            sound = drone
            droneHz = drone.frequency
            period = sampleRate / droneHz
            frames = 0
            comb(window, 0)
        } else {
            System.arraycopy(combed, hopSize, combed, 0, windowSize - hopSize)
            comb(window, windowSize - hopSize)
        }
        frames++

        val level = bandLevel(window, sampleRate)
        val learning = frames * hopSize < LEARN_SECONDS * sampleRate
        // Before the drone has reached the microphone, its level is just the room's, so
        // take the loudest of the first moments; after that, follow the quietest. A voice
        // held in unison must not slowly become the drone, so the level waits it out.
        droneLevel = when {
            frames == 1 -> level
            learning -> maxOf(droneLevel, level)
            level < droneLevel -> level
            level >= PRESENCE * droneLevel -> droneLevel
            else -> droneLevel * exp(LEVEL_RISE_DB_PER_SECOND / 20 * ln(10.0) * hopSize / sampleRate)
        }
        val clean = cleaned.detect(combed, sampleRate)
        // Near a harmonic, the comb favours whichever part of a vibrato lies further from
        // it, and pulls the cleaned reading that way. A voice clearly louder than the drone
        // is better read as it is, then: on a harmonic, where the comb can't help, or
        // close to one if it agrees with the cleaned reading, so isn't a blend with the drone.
        if (!learning && level >= PRESENCE * droneLevel) {
            val raw = heard.detect(window, sampleRate)
            if (raw != null) {
                if (onHarmonic(raw.frequency)) return raw
                if (clean != null && onHarmonic(clean.frequency, SKEWED_HARMONIC) && agree(raw, clean)) return raw
            }
        }
        return clean?.takeUnless { onHarmonic(it.frequency) }
    }

    private fun agree(a: PitchEstimate, b: PitchEstimate) = abs(ln(a.frequency / b.frequency)) < AGREE

    /**
     * Close to a whole multiple of the drone, where the comb cancels a voice along with
     * it. The comb's leftover of the drone itself always reads here too.
     */
    private fun onHarmonic(frequency: Double, within: Double = NEAR_HARMONIC): Boolean {
        val ratio = frequency / droneHz
        val harmonic = ratio.roundToInt()
        return harmonic >= 1 && abs(ratio - harmonic) < within
    }

    /** combed[i] = window[i] − window[i − period], from [from] on; zero where there's no earlier period. */
    private fun comb(window: FloatArray, from: Int) {
        for (i in from until windowSize) {
            val t = i - period
            val k = floor(t).toInt()
            combed[i] = if (k < 1) 0f else (window[i] - cubic(window, k, (t - k).toFloat()))
        }
    }

    private fun detector() = PitchDetector(windowSize, profile.minFrequency, profile.maxFrequency)

    companion object {
        /**
         * How close to a harmonic, as a fraction of the drone's frequency, the comb takes
         * too much of a voice: 50¢ either side of unison, 25¢ of the octave, 17¢ of the twelfth.
         */
        const val NEAR_HARMONIC = 0.03

        /** Just past [NEAR_HARMONIC], where the comb still skews a reading with vibrato: 75¢ of unison, 38¢ of the octave. */
        private const val SKEWED_HARMONIC = 0.045

        private const val OVER_DRONE_PEAK = 0.8

        /** Half a semitone, as a natural log ratio. */
        private val AGREE = ln(2.0) / 24

        /** Above the drone alone by this much (8 dB) is a voice, not the drone's own swell. */
        const val PRESENCE = 2.5

        /**
         * Long enough for the drone to reach the microphone, even over Bluetooth, and for
         * its opening sweep to reach full strength.
         */
        const val LEARN_SECONDS = 1.5

        /** Lets the drone's level follow its own swell, or a phone moved a little closer. */
        private const val LEVEL_RISE_DB_PER_SECOND = 1.5

        /**
         * Level below about 1 kHz. A fresh pluck is bright, and its upper harmonics die
         * away within a second; counted, they'd make every pluck look like a voice arriving.
         */
        private fun bandLevel(samples: FloatArray, sampleRate: Int): Double {
            val a = 1 - exp(-2 * PI * LEVEL_BAND_HZ / sampleRate)
            var y1 = 0.0
            var y2 = 0.0
            var sum = 0.0
            for (s in samples) {
                y1 += a * (s - y1)
                y2 += a * (y1 - y2)
                sum += y2 * y2
            }
            return sqrt(sum / samples.size)
        }

        private const val LEVEL_BAND_HZ = 1_000.0

        /** Catmull-Rom between x[k] and x[k + 1]; [k] must have a sample on each side. */
        private fun cubic(x: FloatArray, k: Int, u: Float): Float {
            val p0 = x[k - 1]
            val p1 = x[k]
            val p2 = x[k + 1]
            val p3 = if (k + 2 < x.size) x[k + 2] else p2
            return p1 + 0.5f * u * (p2 - p0 + u * (2 * p0 - 5 * p1 + 4 * p2 - p3 + u * (3 * (p1 - p2) + p3 - p0)))
        }
    }
}
