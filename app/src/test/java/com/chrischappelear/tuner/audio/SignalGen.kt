package com.chrischappelear.tuner.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic test signals. */
object SignalGen {
    fun sine(freq: Double, sampleRate: Int, length: Int, amplitude: Double = 0.5, phase: Double = 0.0) =
        FloatArray(length) { (amplitude * sin(2 * PI * freq * it / sampleRate + phase)).toFloat() }

    /**
     * Plucked-string-like tone: the given harmonics with 1/n amplitude rolloff
     * and an exponential decay over the frame.
     */
    fun pluck(
        freq: Double,
        sampleRate: Int,
        length: Int,
        harmonics: IntRange = 1..8,
        decayPerSecond: Double = 3.0,
        amplitude: Double = 0.4
    ): FloatArray {
        val out = FloatArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (n in harmonics) {
                v += sin(2 * PI * freq * n * t + n * 0.7) / n
            }
            out[i] = (amplitude * v * exp(-decayPerSecond * t) / harmonics.count()).toFloat()
        }
        return out
    }

    fun noise(length: Int, rms: Double, seed: Long = 42): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (random.nextGaussian() * rms).toFloat() }
    }

    fun rms(signal: FloatArray) = sqrt(signal.sumOf { it.toDouble() * it } / signal.size)

    operator fun FloatArray.plus(other: FloatArray) = FloatArray(size) { this[it] + other[it] }
}
