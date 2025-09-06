package com.chrischappelear.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.tuning.PitchHistoryPoint
import com.chrischappelear.tuner.tuning.NoteFrequencies
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun PitchHistoryGraph(
    pitchHistory: List<PitchHistoryPoint>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pitch History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "10s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            drawPitchHistoryWithLabels(pitchHistory, this)
        }
    }
}

private fun drawPitchHistoryWithLabels(
    pitchHistory: List<PitchHistoryPoint>,
    drawScope: DrawScope
) {
    val activePoints = pitchHistory.filter { it.isActive && it.frequency > 0 }
    
    if (activePoints.isEmpty()) {
        drawEmptyState(drawScope)
        return
    }
    
    val currentTime = System.currentTimeMillis()
    val timeRange = 10_000L // 10 seconds
    val startTime = currentTime - timeRange
    
    // Filter points within our time window
    val visiblePoints = activePoints.filter { 
        it.timestamp >= startTime && it.timestamp <= currentTime 
    }
    
    if (visiblePoints.isEmpty()) {
        drawEmptyState(drawScope)
        return
    }
    
    // Calculate frequency range for Y-axis scaling
    val minFreq = visiblePoints.minOf { it.frequency }
    val maxFreq = visiblePoints.maxOf { it.frequency }
    val freqRange = maxFreq - minFreq
    
    // Expand range if too narrow, with better guitar-specific scaling
    val expandedRange = max(freqRange, 50.0) // At least 50Hz range for better resolution
    val centerFreq = (minFreq + maxFreq) / 2.0
    val adjustedMinFreq = centerFreq - expandedRange / 2.0
    val adjustedMaxFreq = centerFreq + expandedRange / 2.0
    
    with(drawScope) {
        // Draw note labels first (background)
        drawNoteLabels(this, adjustedMinFreq, adjustedMaxFreq)
        
        // Draw grid lines
        drawGrid(this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
        
        // Draw smoothed pitch line
        drawSmoothPitchLine(visiblePoints, this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
        
        // Draw tuning accuracy indicators
        drawTuningIndicators(visiblePoints, this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
    }
}

private fun drawNoteLabels(
    drawScope: DrawScope,
    minFreq: Double,
    maxFreq: Double
) {
    with(drawScope) {
        val labelPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            textSize = 24f
            color = Color.Gray.copy(alpha = 0.7f).toArgb()
            typeface = Typeface.DEFAULT
        }
        
        // Find notes within the frequency range
        val freqStep = (maxFreq - minFreq) / 6 // Show about 6 note labels
        for (i in 0..6) {
            val freq = minFreq + (freqStep * i)
            if (freq > 0) {
                val note = NoteFrequencies.getClosestNote(freq)
                if (note.name.isNotEmpty()) {
                    val y = size.height - (freq - minFreq) * size.height / (maxFreq - minFreq)
                    
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            note.getDisplayName(),
                            8f,
                            y.toFloat() + 8f,
                            labelPaint
                        )
                    }
                }
            }
        }
    }
}

private fun drawPitchHistory(
    pitchHistory: List<PitchHistoryPoint>,
    drawScope: DrawScope
) {
    val activePoints = pitchHistory.filter { it.isActive && it.frequency > 0 }
    
    if (activePoints.isEmpty()) {
        drawEmptyState(drawScope)
        return
    }
    
    val currentTime = System.currentTimeMillis()
    val timeRange = 10_000L // 10 seconds
    val startTime = currentTime - timeRange
    
    // Filter points within our time window
    val visiblePoints = activePoints.filter { 
        it.timestamp >= startTime && it.timestamp <= currentTime 
    }
    
    if (visiblePoints.isEmpty()) {
        drawEmptyState(drawScope)
        return
    }
    
    // Calculate frequency range for Y-axis scaling
    val minFreq = visiblePoints.minOf { it.frequency }
    val maxFreq = visiblePoints.maxOf { it.frequency }
    val freqRange = maxFreq - minFreq
    
    // Expand range if too narrow
    val expandedRange = max(freqRange, 20.0) // At least 20Hz range
    val centerFreq = (minFreq + maxFreq) / 2.0
    val adjustedMinFreq = centerFreq - expandedRange / 2.0
    val adjustedMaxFreq = centerFreq + expandedRange / 2.0
    
    with(drawScope) {
        // Draw grid lines
        drawGrid(this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
        
        // Draw pitch line
        drawPitchLine(visiblePoints, this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
        
        // Draw tuning accuracy indicators
        drawTuningIndicators(visiblePoints, this, adjustedMinFreq, adjustedMaxFreq, startTime, currentTime)
    }
}

private fun drawEmptyState(drawScope: DrawScope) {
    with(drawScope) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        
        // Draw placeholder grid
        val gridColor = Color.Gray.copy(alpha = 0.2f)
        
        // Horizontal lines
        for (i in 0..4) {
            val y = (i * size.height / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Vertical lines
        for (i in 0..10) {
            val x = (i * size.width / 10f)
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun drawGrid(
    drawScope: DrawScope,
    minFreq: Double,
    maxFreq: Double,
    startTime: Long,
    endTime: Long
) {
    with(drawScope) {
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        
        // Horizontal frequency grid lines
        val freqSteps = 5
        for (i in 0..freqSteps) {
            val freq = minFreq + (maxFreq - minFreq) * i / freqSteps
            val y = size.height - (freq - minFreq) * size.height / (maxFreq - minFreq)
            
            drawLine(
                color = gridColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Vertical time grid lines
        val timeSteps = 10
        for (i in 0..timeSteps) {
            val x = i * size.width / timeSteps
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun drawSmoothPitchLine(
    points: List<PitchHistoryPoint>,
    drawScope: DrawScope,
    minFreq: Double,
    maxFreq: Double,
    startTime: Long,
    endTime: Long
) {
    if (points.size < 2) return
    
    with(drawScope) {
        val path = Path()
        var isFirst = true
        
        // Create smoothed points for visual display only
        val smoothedPoints = mutableListOf<Pair<Float, Float>>()
        
        points.forEach { point ->
            val x = ((point.timestamp - startTime).toFloat() / (endTime - startTime)) * size.width
            val y = size.height - ((point.frequency - minFreq) / (maxFreq - minFreq)).toFloat() * size.height
            smoothedPoints.add(x to y)
        }
        
        // Apply simple smoothing for visual appeal
        if (smoothedPoints.size > 2) {
            for (i in smoothedPoints.indices) {
                val (x, y) = smoothedPoints[i]
                
                // Simple 3-point averaging for middle points
                val smoothedY = if (i > 0 && i < smoothedPoints.size - 1) {
                    val prevY = smoothedPoints[i-1].second
                    val nextY = smoothedPoints[i+1].second
                    (prevY + y + nextY) / 3f
                } else {
                    y
                }
                
                if (isFirst) {
                    path.moveTo(x, smoothedY)
                    isFirst = false
                } else {
                    path.lineTo(x, smoothedY)
                }
            }
        } else {
            // Fallback for few points
            smoothedPoints.forEach { (x, y) ->
                if (isFirst) {
                    path.moveTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        
        // Draw the main pitch line
        drawPath(
            path = path,
            color = Color(0xFF2196F3), // Blue
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

private fun drawPitchLine(
    points: List<PitchHistoryPoint>,
    drawScope: DrawScope,
    minFreq: Double,
    maxFreq: Double,
    startTime: Long,
    endTime: Long
) {
    if (points.size < 2) return
    
    with(drawScope) {
        val path = Path()
        var isFirst = true
        
        points.forEach { point ->
            val x = ((point.timestamp - startTime).toFloat() / (endTime - startTime)) * size.width
            val y = size.height - ((point.frequency - minFreq) / (maxFreq - minFreq)).toFloat() * size.height
            
            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw the main pitch line
        drawPath(
            path = path,
            color = Color(0xFF2196F3), // Blue
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

private fun drawTuningIndicators(
    points: List<PitchHistoryPoint>,
    drawScope: DrawScope,
    minFreq: Double,
    maxFreq: Double,
    startTime: Long,
    endTime: Long
) {
    with(drawScope) {
        points.forEach { point ->
            val x = ((point.timestamp - startTime).toFloat() / (endTime - startTime)) * size.width
            val y = size.height - ((point.frequency - minFreq) / (maxFreq - minFreq)).toFloat() * size.height
            
            // Color based on tuning accuracy
            val color = when {
                abs(point.centsOffset) <= 10 -> Color(0xFF4CAF50) // Green - in tune
                point.centsOffset > 0 -> Color(0xFFFF5722) // Red - sharp
                else -> Color(0xFF2196F3) // Blue - flat
            }
            
            // Draw small circles for data points
            drawCircle(
                color = color,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}