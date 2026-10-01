package com.chrischappelear.tuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One frame's pitch reading.
 *
 * @property frequency fundamental in Hz
 * @property clarity   height of the chosen NSDF peak, 0..1 — how periodic the frame is
 * @property rms       root-mean-square level of the frame, 0..1 full scale
 */
data class PitchEstimate(
    val frequency: Double,
    val clarity: Double,
    val rms: Double
)

/**
 * McLeod Pitch Method (McLeod & Wyvill, "A Smarter Way to Find Pitch", 2005).
 *
 * Computes the normalized square difference function (NSDF) of a frame via an
 * FFT autocorrelation, then picks the *first* key maximum that reaches
 * [peakThreshold] of the tallest one. Taking the first sufficiently-tall peak
 * rather than the tallest is what keeps it from jumping an octave down, and
 * because it works on lag rather than frequency bins, resolution stays fine
 * even for a low E string.
 *
 * Not thread-safe: scratch buffers are reused between calls.
 */
class PitchDetector(
    val windowSize: Int = 4096,
    val minFrequency: Double = 60.0,
    val maxFrequency: Double = 1400.0,
    private val peakThreshold: Double = 0.9
) {
    private val fft = FFT(Integer.highestOneBit(windowSize - 1) shl 2)
    private val re = DoubleArray(fft.size)
    private val im = DoubleArray(fft.size)
    private val frame = DoubleArray(windowSize)
    private val nsdf = DoubleArray(windowSize)
    private var lowPass = LowPass(LOW_PASS_HZ, 48_000)

    /**
     * Estimate the pitch of [samples] (the first [windowSize] are used).
     * Returns null for silence or when no period is found in range.
     */
    fun detect(samples: FloatArray, sampleRate: Int): PitchEstimate? {
        require(samples.size >= windowSize)

        var mean = 0.0
        for (i in 0 until windowSize) mean += samples[i]
        mean /= windowSize

        var energy = 0.0
        for (i in 0 until windowSize) {
            val v = samples[i] - mean
            frame[i] = v
            energy += v * v
        }
        val rms = sqrt(energy / windowSize)
        if (rms < 1e-5) return null

        // Hiss above the musical range only blurs the NSDF peaks; filtering it out
        // leaves the period untouched.
        if (lowPass.sampleRate != sampleRate) lowPass = LowPass(LOW_PASS_HZ, sampleRate)
        lowPass.apply(frame)

        val maxLag = minOf((sampleRate / minFrequency).toInt() + 2, windowSize - 3)
        val minLag = maxOf((sampleRate / maxFrequency).toInt() - 1, 1)

        computeNsdf(maxLag + 2) // +1 so interpolation can look one past maxLag

        val peak = pickPeak(minLag, maxLag) ?: return null
        val frequency = sampleRate / peak.first
        if (frequency < minFrequency || frequency > maxFrequency) return null

        return PitchEstimate(frequency, peak.second.coerceAtMost(1.0), rms)
    }

    /** Fills nsdf[0 until lags] using r(τ) from an FFT autocorrelation. */
    private fun computeNsdf(lags: Int) {
        frame.copyInto(re)
        re.fill(0.0, windowSize, re.size)
        im.fill(0.0)

        fft.forward(re, im)
        for (i in re.indices) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }
        fft.inverse(re, im)
        // re[τ] is now the autocorrelation r(τ).

        var m = 2.0 * re[0]
        for (tau in 0 until lags) {
            if (tau > 0) {
                val a = frame[tau - 1]
                val b = frame[windowSize - tau]
                m -= a * a + b * b
            }
            nsdf[tau] = if (m > 0.0) 2.0 * re[tau] / m else 0.0
        }
    }

    /** Returns (interpolated lag, interpolated clarity) or null. */
    private fun pickPeak(minLag: Int, maxLag: Int): Pair<Double, Double>? {
        // Skip the lobe around τ = 0: start after the first negative-going zero crossing.
        var tau = 1
        while (tau < maxLag && nsdf[tau] > 0.0) tau++

        val keyMaxima = ArrayList<Int>(16)
        var best = -1
        while (tau < maxLag) {
            // Wait for a positive-going crossing.
            while (tau < maxLag && nsdf[tau] <= 0.0) tau++
            if (tau >= maxLag) break
            // Track the highest point of this positive lobe.
            best = tau
            while (tau < maxLag && nsdf[tau] > 0.0) {
                if (nsdf[tau] > nsdf[best]) best = tau
                tau++
            }
            if (best in minLag..maxLag) keyMaxima.add(best)
        }
        if (keyMaxima.isEmpty()) return null

        val highest = keyMaxima.maxOf { nsdf[it] }
        if (highest <= 0.0) return null
        val cutoff = peakThreshold * highest
        val chosen = keyMaxima.first { nsdf[it] >= cutoff }

        return interpolate(chosen)
    }

    private fun interpolate(i: Int): Pair<Double, Double> {
        if (i <= 0 || i >= nsdf.size - 1) return i.toDouble() to nsdf[i]
        val a = nsdf[i - 1]
        val b = nsdf[i]
        val c = nsdf[i + 1]
        val denominator = a - 2 * b + c
        if (denominator == 0.0) return i.toDouble() to b
        val delta = 0.5 * (a - c) / denominator
        return (i + delta) to (b - 0.25 * (a - c) * delta)
    }

    /** Second-order Butterworth low-pass (RBJ cookbook), run once over a frame from rest. */
    private class LowPass(cutoff: Double, val sampleRate: Int) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        init {
            val w = 2 * PI * cutoff / sampleRate
            val alpha = sin(w) / (2 * 0.7071)
            val a0 = 1 + alpha
            b1 = (1 - cos(w)) / a0
            b0 = b1 / 2
            b2 = b0
            a1 = -2 * cos(w) / a0
            a2 = (1 - alpha) / a0
        }

        fun apply(x: DoubleArray) {
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            for (i in x.indices) {
                val x0 = x[i]
                val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x0
                y2 = y1; y1 = y0
                x[i] = y0
            }
        }
    }

    private companion object {
        const val LOW_PASS_HZ = 2_000.0
    }
}
