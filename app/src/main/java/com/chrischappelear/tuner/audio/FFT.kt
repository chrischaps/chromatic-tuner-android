package com.chrischappelear.tuner.audio

import kotlin.math.*

class FFT {
    
    fun fft(input: DoubleArray): Array<Complex> {
        val n = input.size
        if (n <= 1) return input.map { Complex(it, 0.0) }.toTypedArray()
        
        val even = DoubleArray(n / 2)
        val odd = DoubleArray(n / 2)
        
        for (i in 0 until n / 2) {
            even[i] = input[2 * i]
            odd[i] = input[2 * i + 1]
        }
        
        val evenFft = fft(even)
        val oddFft = fft(odd)
        
        val result = Array(n) { Complex(0.0, 0.0) }
        
        for (k in 0 until n / 2) {
            val angle = -2.0 * PI * k / n
            val twiddle = Complex(cos(angle), sin(angle))
            val t = twiddle * oddFft[k]
            
            result[k] = evenFft[k] + t
            result[k + n / 2] = evenFft[k] - t
        }
        
        return result
    }
    
    fun getMagnitudeSpectrum(fftResult: Array<Complex>): DoubleArray {
        return fftResult.map { it.magnitude() }.toDoubleArray()
    }
    
    fun findPeakFrequency(magnitudes: DoubleArray, sampleRate: Int): Double {
        val maxIndex = magnitudes.indices.maxByOrNull { magnitudes[it] } ?: 0
        return maxIndex * sampleRate.toDouble() / magnitudes.size
    }
}

data class Complex(val real: Double, val imaginary: Double) {
    operator fun plus(other: Complex) = Complex(real + other.real, imaginary + other.imaginary)
    operator fun minus(other: Complex) = Complex(real - other.real, imaginary - other.imaginary)
    operator fun times(other: Complex) = Complex(
        real * other.real - imaginary * other.imaginary,
        real * other.imaginary + imaginary * other.real
    )
    
    fun magnitude() = sqrt(real * real + imaginary * imaginary)
}