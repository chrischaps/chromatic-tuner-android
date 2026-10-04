package com.chrischappelear.tuner.audio

import java.util.Random
import kotlin.math.roundToLong

/**
 * A drone played the way a tanpura is: four strings plucked in turn, over and over,
 * each still ringing when the next is plucked, so the note never stops sounding.
 * Every pluck is a little different in strength and timing, as by hand. It opens with
 * a quicker sweep across the strings, so it reaches its full shimmer straight away.
 *
 * A real tanpura's first string is a fifth below and its last an octave below. Here
 * every string is the drone note or the octave above it, so the sound repeats exactly
 * once per period of the drone note, which is what lets [DroneCanceller] take it back
 * out of what the microphone hears. With a fifth or a low octave it would repeat only
 * every two or three periods, and cancelling it would also cancel a singer's fifth.
 *
 * Strings of exactly the same pitch interfere: plucked at random moments they can
 * reinforce or nearly cancel, and the level would lurch with each pluck. So each pitch
 * is always plucked the same way and only on a whole number of periods, and every new
 * pluck lands in phase with the ones still ringing, as re-plucking one string would.
 *
 * Pure and single-threaded; [render] fills buffers until [finished].
 */
class Tanpura(
    val frequency: Double,
    private val sampleRate: Int,
    seed: Long = frequency.toBits()
) {
    private class Pluck(val multiple: Int, val level: Double, val gapSeconds: Double)

    private val random = Random(seed)
    private val period = sampleRate / frequency
    /** One excitation per pitch, so plucks of it line up in phase. */
    private val shapes = CYCLE.map { it.multiple }.distinct().associateWith { random.nextLong() }
    private val voices = mutableListOf<PluckVoice>()
    private var position = 0L
    private var nextPluckAt = 0L
    private var string = 0
    private var plucks = 0
    private var releasing = false

    var finished = false
        private set

    /** Fills [out] from [offset] for [count] samples, mixing onto what's there. */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset) {
        var start = offset
        val end = offset + count
        while (start < end) {
            if (!releasing && position >= nextPluckAt) pluck()
            val until = if (releasing) end else minOf(end.toLong(), start + nextPluckAt - position).toInt()
            for (voice in voices) voice.render(out, start, until - start)
            position += until - start
            start = until
        }
        voices.removeAll { it.finished }
        if (releasing && voices.isEmpty()) finished = true
    }

    /** Damps every string, quickly but without a click. */
    fun release() {
        releasing = true
        voices.forEach(PluckVoice::release)
    }

    private fun pluck() {
        val next = CYCLE[string]
        val touch = 1 - TOUCH_VARIATION * random.nextDouble()
        voices += PluckVoice(
            frequency * next.multiple, sampleRate, shapes.getValue(next.multiple), LEVEL * next.level * touch,
            ringSeconds = RING_SECONDS, durationSeconds = DURATION_SECONDS
        )
        string = (string + 1) % CYCLE.size
        val gap = if (plucks++ < CYCLE.size) OPENING_GAP_SECONDS
        else next.gapSeconds + TIMING_VARIATION * (random.nextDouble() * 2 - 1)
        val periods = ((position + gap * sampleRate) / period).roundToLong()
        nextPluckAt = (periods * period).roundToLong()
    }

    private companion object {
        /** The octave first, then the note three times, the last held a little longer. */
        val CYCLE = listOf(
            Pluck(multiple = 2, level = 0.7, gapSeconds = 1.05),
            Pluck(multiple = 1, level = 0.9, gapSeconds = 1.05),
            Pluck(multiple = 1, level = 0.85, gapSeconds = 1.05),
            Pluck(multiple = 1, level = 1.0, gapSeconds = 1.6)
        )

        /** Below a reference tone's level: a drone sits under whatever is played over it. */
        const val LEVEL = 0.3

        /**
         * Tanpura strings ring long, and each is still sounding through the next three
         * plucks. That keeps the drone's level steady, not swelling with every pluck,
         * which matters to [DroneCanceller]: it tells a voice from the drone by level.
         */
        const val RING_SECONDS = 30.0
        const val DURATION_SECONDS = 6.0
        const val OPENING_GAP_SECONDS = 0.3
        const val TOUCH_VARIATION = 0.15
        const val TIMING_VARIATION = 0.04
    }
}
