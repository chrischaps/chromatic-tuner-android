package com.chrischappelear.tuner.tuning

/**
 * One sample of the cents trace. [cents] is null while nothing is sounding,
 * which leaves a gap in the line.
 */
data class TracePoint(
    val timeMs: Long,
    val cents: Float?,
    val note: Note?
)

/** A rolling window of recent trace points. */
class PitchHistory(val durationMs: Long = 8_000L) {
    private val points = ArrayDeque<TracePoint>()

    fun add(point: TracePoint) {
        points.addLast(point)
        val cutoff = point.timeMs - durationMs
        while (points.isNotEmpty() && points.first().timeMs < cutoff) points.removeFirst()
    }

    fun snapshot(): List<TracePoint> = points.toList()

    fun clear() = points.clear()
}
