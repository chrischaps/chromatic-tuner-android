package com.chrischappelear.tuner.tuning

import kotlin.math.PI
import kotlin.math.abs

/**
 * The 1€ filter (Casiez, Roussel & Vogel, CHI 2012): a low-pass whose cutoff rises
 * with the signal's speed. A held note reads steady; a string being turned
 * follows without lag.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.05,
    private val derivativeCutoff: Double = 1.0
) {
    private var lastValue = Double.NaN
    private var lastDerivative = 0.0
    private var lastTimeMs = 0L

    fun reset() {
        lastValue = Double.NaN
        lastDerivative = 0.0
    }

    fun filter(value: Double, timeMs: Long): Double {
        if (lastValue.isNaN()) {
            lastValue = value
            lastTimeMs = timeMs
            return value
        }
        val dt = ((timeMs - lastTimeMs).coerceAtLeast(1)) / 1000.0
        lastTimeMs = timeMs

        val derivative = (value - lastValue) / dt
        lastDerivative += alpha(derivativeCutoff, dt) * (derivative - lastDerivative)

        val cutoff = minCutoff + beta * abs(lastDerivative)
        lastValue += alpha(cutoff, dt) * (value - lastValue)
        return lastValue
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}
