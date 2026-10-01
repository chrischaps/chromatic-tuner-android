package com.chrischappelear.tuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iterative, in-place radix-2 FFT over separate real/imaginary arrays.
 *
 * Twiddle factors and the bit-reversal table are precomputed for a fixed [size],
 * so repeated transforms allocate nothing.
 */
class FFT(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val bitReversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) {
            table[i] = Integer.reverse(i) ushr (32 - bits)
        }
    }

    /** Forward transform in place. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** Inverse transform in place, including the 1/N scale. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == size && im.size == size)

        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sign * sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = wr * re[b] - wi * im[b]
                    val ti = wr * im[b] + wi * re[b]
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
