package com.chrischappelear.tuner.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One plucked string, synthesized by Karplus-Strong: a burst of noise circulating
 * through a delay line one period long, softened a little on every pass, so the
 * upper harmonics fade first the way a real string's do.
 *
 * A tuner's reference has to be in tune, and a whole-sample delay line is not: at
 * 1 kHz it can be 18¢ off. So the loop is trimmed to the exact period with a
 * first-order allpass, solved for the delay at the fundamental itself. Loss is
 * scaled by pitch, and high notes soften less on each pass, so a bass string and a
 * high E ring for about the same time.
 *
 * Pure and single-threaded; [render] fills buffers until [finished].
 */
class PluckVoice(
    frequency: Double,
    sampleRate: Int,
    seed: Long = frequency.toBits()
) {
    private val delay: FloatArray
    private var index = 0
    private val allpassCoefficient: Double
    private var allpassIn = 0.0
    private var allpassOut = 0.0
    private var lastLoop = 0.0
    /** Weight of the previous sample in the loop's softening; 0.5 is a plain average. */
    private val stretch: Double
    private val loss: Double

    private var dcIn = 0.0
    private var dcOut = 0.0
    private val dcPole = 1 - 2 * PI * DC_CUTOFF_HZ / sampleRate

    private var position = 0
    private val attackSamples = (ATTACK_SECONDS * sampleRate).roundToInt()
    private val fadeStart = ((DURATION_SECONDS - FADE_SECONDS) * sampleRate).roundToInt()
    private val fadeSamples = (FADE_SECONDS * sampleRate).roundToInt()
    private val releaseSamples = (RELEASE_SECONDS * sampleRate).roundToInt()
    private var releaseAt = -1
    private var releaseFrom = 1.0

    var finished = false
        private set

    init {
        val omega = 2 * PI * frequency / sampleRate
        val perPass = exp(ln(SILENCE) / (frequency * RING_SECONDS))

        // A plain average costs cos(ω/2) of the fundamental on every pass, which would
        // silence high notes in a second or two; weigh it more lightly where needed.
        val averageLoss = cos(omega / 2)
        stretch = if (averageLoss >= perPass) 0.5 else {
            val product = (1 - perPass * perPass) / (2 * (1 - cos(omega)))
            ((1 - sqrt((1 - 4 * product).coerceAtLeast(0.0))) / 2).coerceAtLeast(MIN_STRETCH)
        }
        val gain = sqrt(1 - 2 * stretch * (1 - stretch) * (1 - cos(omega)))
        loss = (perPass / gain).coerceAtMost(MAX_LOSS)

        // The delay line, the softening and the allpass add up to exactly one period.
        val period = sampleRate / frequency
        val softeningDelay = atan2(stretch * sin(omega), 1 - stretch + stretch * cos(omega)) / omega
        val length = floor(period - softeningDelay - 0.1).toInt().coerceAtLeast(2)
        val fraction = period - softeningDelay - length
        allpassCoefficient = sin((1 - fraction) * omega / 2) / sin((1 + fraction) * omega / 2)
        delay = excitation(length, seed)
    }

    /** Fills [out] from [offset] for [count] samples, mixing onto what's there. */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset) {
        for (i in offset until offset + count) {
            if (finished) return
            out[i] += (next() * envelope()).toFloat()
            position++
        }
    }

    /** Damps the string quickly, as a fingertip would, without a click. */
    fun release() {
        if (releaseAt >= 0 || finished) return
        releaseFrom = envelope()
        releaseAt = position
    }

    private fun next(): Double {
        val current = delay[index].toDouble()
        val averaged = loss * ((1 - stretch) * current + stretch * lastLoop)
        lastLoop = current
        val trimmed = allpassCoefficient * averaged + allpassIn - allpassCoefficient * allpassOut
        allpassIn = averaged
        allpassOut = trimmed
        delay[index] = trimmed.toFloat()
        index = (index + 1) % delay.size

        // A gentle high-pass keeps any leftover offset out of the speaker.
        dcOut = current - dcIn + dcPole * dcOut
        dcIn = current
        return dcOut * GAIN
    }

    private fun envelope(): Double {
        val attack = if (position < attackSamples) position.toDouble() / attackSamples else 1.0
        if (releaseAt >= 0) {
            val t = (position - releaseAt).toDouble() / releaseSamples
            if (t >= 1) finished = true
            return releaseFrom * (1 - t).coerceAtLeast(0.0)
        }
        if (position < fadeStart) return attack
        val t = (position - fadeStart).toDouble() / fadeSamples
        if (t >= 1) finished = true
        return attack * 0.5 * (1 + cos(PI * t.coerceAtMost(1.0)))
    }

    companion object {
        /** How long a pluck sounds, fade included. */
        const val DURATION_SECONDS = 3.5
        private const val FADE_SECONDS = 0.8
        private const val ATTACK_SECONDS = 0.002
        private const val RELEASE_SECONDS = 0.03

        /** Time for the string to die away by 60 dB, were it left to ring. */
        private const val RING_SECONDS = 10.0
        private const val SILENCE = 0.001
        private const val MAX_LOSS = 0.99995
        private const val MIN_STRETCH = 0.05
        private const val GAIN = 0.9
        private const val DC_CUTOFF_HZ = 20.0

        /** Where along the string it's plucked; that harmonic and its multiples go quiet. */
        private const val PICK_POSITION = 0.18
        private const val SOFTNESS = 0.45

        /**
         * The pluck itself: noise, softened like a fingertip rather than a pick, with
         * the comb notch of a pluck partway along the string. Seeded, so each note
         * sounds the same every time it's tapped.
         */
        private fun excitation(length: Int, seed: Long): FloatArray {
            val random = Random(seed)
            val noise = DoubleArray(length) { random.nextDouble() * 2 - 1 }
            var smooth = 0.0
            for (i in noise.indices) {
                smooth += (1 - SOFTNESS) * (noise[i] - smooth)
                noise[i] = smooth
            }
            val pick = (PICK_POSITION * length).roundToInt().coerceAtLeast(1)
            val combed = DoubleArray(length) { noise[it] - noise[Math.floorMod(it - pick, length)] }
            val mean = combed.average()
            val peak = combed.maxOf { abs(it - mean) }.coerceAtLeast(1e-9)
            return FloatArray(length) { ((combed[it] - mean) / peak).toFloat() }
        }
    }
}
