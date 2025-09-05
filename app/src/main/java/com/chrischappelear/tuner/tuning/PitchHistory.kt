package com.chrischappelear.tuner.tuning

data class PitchHistoryPoint(
    val timestamp: Long,
    val frequency: Double,
    val note: Note,
    val centsOffset: Int,
    val isActive: Boolean
)

class PitchHistory {
    private val maxHistoryDuration = 10_000L // 10 seconds in milliseconds
    private val _history = mutableListOf<PitchHistoryPoint>()
    
    val history: List<PitchHistoryPoint>
        get() = _history.toList()
    
    fun addPoint(tuningResult: TuningResult) {
        val currentTime = System.currentTimeMillis()
        
        val point = PitchHistoryPoint(
            timestamp = currentTime,
            frequency = tuningResult.frequency,
            note = tuningResult.note,
            centsOffset = tuningResult.centsOffset,
            isActive = tuningResult.isActive
        )
        
        _history.add(point)
        
        // Remove old points beyond our time window
        cleanupOldPoints(currentTime)
    }
    
    private fun cleanupOldPoints(currentTime: Long) {
        val cutoffTime = currentTime - maxHistoryDuration
        _history.removeAll { it.timestamp < cutoffTime }
    }
    
    fun clear() {
        _history.clear()
    }
    
    fun getActivePointsInRange(startTime: Long, endTime: Long): List<PitchHistoryPoint> {
        return _history.filter { point ->
            point.timestamp in startTime..endTime && point.isActive && point.frequency > 0
        }
    }
    
    fun getLatestActivePoint(): PitchHistoryPoint? {
        return _history.lastOrNull { it.isActive && it.frequency > 0 }
    }
}