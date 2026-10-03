package com.chrischappelear.tuner.audio

import com.chrischappelear.tuner.tuning.Tuning

/**
 * How the microphone is analysed: the window and hop, and the pitch range searched.
 *
 * Every profile keeps windowSize / hopSize = 4. A transient taints at most that many
 * frames, and the frame counts in TuningProcessor are chosen to outlast it.
 */
data class CaptureProfile(
    val windowSize: Int,
    val hopSize: Int,
    val minFrequency: Double,
    val maxFrequency: Double
) {
    companion object {
        /** Guitar, ukulele, voice: C2 up. About 47 readings a second at 48 kHz. */
        val Standard = CaptureProfile(windowSize = 4096, hopSize = 1024, minFrequency = 60.0, maxFrequency = 1400.0)

        /**
         * Bass: down to B0 (30.9 Hz). The longer window holds five periods of B0 rather
         * than two and a half, which keeps the reading sure when a phone microphone barely
         * hears the fundamental. It halves the reading rate, which a bass doesn't mind.
         */
        val Low = CaptureProfile(windowSize = 8192, hopSize = 2048, minFrequency = 28.0, maxFrequency = 1400.0)

        /** At or below B1 (61.7 Hz), a string tuned a little flat would fall under Standard's floor. */
        private const val LOW_STRING_MIDI = 35

        fun forTuning(tuning: Tuning): CaptureProfile {
            val lowest = tuning.strings.minOfOrNull { it.note.midi } ?: return Standard
            return if (lowest <= LOW_STRING_MIDI) Low else Standard
        }
    }
}
