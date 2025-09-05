package com.chrischappelear.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.tuning.PitchHistoryPoint
import com.chrischappelear.tuner.tuning.TuningResult
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    tuningResult: TuningResult,
    pitchHistory: List<PitchHistoryPoint>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLandscape) {
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        if (!hasPermission) {
            PermissionRequest(onRequestPermission = onRequestPermission)
        } else {
            TunerContent(
                tuningResult = tuningResult,
                pitchHistory = pitchHistory,
                isLandscape = isLandscape
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Microphone Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app needs microphone access to analyze audio for tuning.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun TunerContent(
    tuningResult: TuningResult,
    pitchHistory: List<PitchHistoryPoint>,
    isLandscape: Boolean
) {
    // Simplified single column layout - no button to worry about
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Logo and Title Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // TODO: Replace with your raster logo
            // Save your logo as app/src/main/res/drawable-nodpi/tuner_logo.png
            // Then uncomment the Image composable below

            Image(
                painter = painterResource(id = R.drawable.tuner_logo),
                contentDescription = "Chromatic Tuner Logo",
                modifier = Modifier.size(if (isLandscape) 80.dp else 100.dp)
            )
            
            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
            
            Text(
                text = "CHROMATIC",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "TUNER",
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = if (isLandscape) 3.sp else 4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 48.dp))
        
        TunerMeter(tuningResult)
        
        Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))
        
        if (tuningResult.isActive && tuningResult.note.name.isNotEmpty()) {
            NoteDisplay(tuningResult)
        } else {
            Text(
                text = "Listening...",
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 24.dp))
        
        // Add pitch history graph
        PitchHistoryGraph(
            pitchHistory = pitchHistory,
            modifier = Modifier.padding(horizontal = if (isLandscape) 8.dp else 0.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NoteDisplay(tuningResult: TuningResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = tuningResult.note.getDisplayName(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (tuningResult.isInTune) Color.Green else MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = String.format("%.1f Hz", tuningResult.frequency),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val centsText = when {
            tuningResult.isInTune -> "In Tune"
            tuningResult.centsOffset > 0 -> "+${tuningResult.centsOffset} cents"
            else -> "${tuningResult.centsOffset} cents"
        }
        
        Text(
            text = centsText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = when {
                tuningResult.isInTune -> Color.Green
                tuningResult.centsOffset > 0 -> Color.Red
                else -> Color.Blue
            }
        )
    }
}

@Composable
private fun TunerMeter(tuningResult: TuningResult) {
    val meterWidth = 300.dp
    val meterHeight = 60.dp
    
    Canvas(
        modifier = Modifier
            .size(meterWidth, meterHeight)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.medium
            )
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        drawMeterBackground(centerX, centerY)
        
        if (tuningResult.isActive) {
            drawNeedle(tuningResult.centsOffset, centerX, centerY, tuningResult.isInTune)
        }
    }
}

private fun DrawScope.drawMeterBackground(centerX: Float, centerY: Float) {
    val strokeWidth = 2.dp.toPx()
    val tickLength = 15.dp.toPx()
    val meterRange = 120.dp.toPx()
    
    drawLine(
        Color.Gray,
        Offset(centerX - meterRange, centerY),
        Offset(centerX + meterRange, centerY),
        strokeWidth = strokeWidth
    )
    
    for (i in -50..50 step 10) {
        val x = centerX + (i * meterRange / 50)
        val tickHeight = if (i == 0) tickLength * 1.5f else tickLength
        drawLine(
            if (i == 0) Color.Green else Color.Gray,
            Offset(x, centerY - tickHeight / 2),
            Offset(x, centerY + tickHeight / 2),
            strokeWidth = if (i == 0) strokeWidth * 2 else strokeWidth
        )
    }
}

private fun DrawScope.drawNeedle(centsOffset: Int, centerX: Float, centerY: Float, isInTune: Boolean) {
    val meterRange = 120.dp.toPx()
    val maxCents = 50
    
    val clampedCents = centsOffset.coerceIn(-maxCents, maxCents)
    val needleX = centerX + (clampedCents * meterRange / maxCents)
    
    val needleColor = when {
        isInTune -> Color.Green
        centsOffset > 0 -> Color.Red
        else -> Color.Blue
    }
    
    drawCircle(
        needleColor,
        radius = 8.dp.toPx(),
        center = Offset(needleX, centerY)
    )
    
    drawLine(
        needleColor,
        Offset(needleX, centerY - 20.dp.toPx()),
        Offset(needleX, centerY + 20.dp.toPx()),
        strokeWidth = 4.dp.toPx()
    )
}