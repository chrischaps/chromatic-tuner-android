package com.chrischappelear.tuner.tuning

/**
 * One sample of the traces. [cents] and [midi] are null while nothing is sounding,
 * which leaves a gap in the line.
 *
 * @property cents offset from [note], the target the tuner settled on
 * @property midi  the pitch itself as a fractional MIDI number, smoothed but never
 *                 snapped to a note, so a sung scale glides from one step to the next
 */
data class TracePoint(
    val timeMs: Long,
    val cents: Float?,
    val note: Note?,
    val midi: Float? = null
)

/** A rolling window of recent trace points: long enough for the practice view. */
class PitchHistory(val durationMs: Long = 30_000L) {
    private val points = ArrayDeque<TracePoint>()

    fun add(point: TracePoint) {
        points.addLast(point)
        val cutoff = point.timeMs - durationMs
        while (points.isNotEmpty() && points.first().timeMs < cutoff) points.removeFirst()
    }

    fun snapshot(): List<TracePoint> = points.toList()

    fun clear() = points.clear()
}
