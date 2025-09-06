package com.chrischappelear.tuner.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

class AudioRecorder {
    private val sampleRate = 44100
    private val bufferSize = 4096
    private val fft = FFT()
    
    // Volume threshold to ignore background noise
    private val volumeThreshold = 0.005 // Adjust this value as needed
    
    // Frequency smoothing parameters
    private val smoothingBufferSize = 5 // Number of recent frequencies to average
    private val maxFrequencyJump = 50.0 // Maximum Hz jump allowed per update
    private val smoothingFactor = 0.3 // Low-pass filter coefficient (0-1, lower = smoother)
    
    // Smoothing state
    private val frequencyHistory = mutableListOf<Double>()
    private var previousSmoothedFreq = 0.0
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    private val _frequency = MutableStateFlow(0.0)
    val frequency: StateFlow<Double> = _frequency
    
    private val _amplitude = MutableStateFlow(0.0)
    val amplitude: StateFlow<Double> = _amplitude
    
    fun startRecording() {
        if (isRecording) return
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            
            audioRecord?.startRecording()
            isRecording = true
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                processAudio()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }
    
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _frequency.value = 0.0
        _amplitude.value = 0.0
        
        // Clear smoothing state
        frequencyHistory.clear()
        previousSmoothedFreq = 0.0
    }
    
    private suspend fun processAudio() {
        val buffer = ShortArray(bufferSize)
        
        while (isRecording && audioRecord != null) {
            val readBytes = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            
            if (readBytes > 0) {
                val doubleBuffer = buffer.map { it.toDouble() / Short.MAX_VALUE }.toDoubleArray()
                
                // Calculate RMS amplitude to determine if signal is strong enough
                val rmsAmplitude = calculateRMSAmplitude(doubleBuffer)
                
                if (rmsAmplitude < volumeThreshold) {
                    // Signal too weak, clear previous results
                    _frequency.value = 0.0
                    _amplitude.value = 0.0
                } else {
                    val paddedSize = nextPowerOfTwo(doubleBuffer.size)
                    val paddedBuffer = DoubleArray(paddedSize)
                    doubleBuffer.copyInto(paddedBuffer)
                    
                    val fftResult = fft.fft(paddedBuffer)
                    val magnitudes = fft.getMagnitudeSpectrum(fftResult)
                    
                    val fundamentalFreq = findFundamentalFrequency(magnitudes)
                    val maxAmplitude = magnitudes.maxOrNull() ?: 0.0
                    
                    val smoothedFreq = applySmoothingFilter(fundamentalFreq)
                    
                    _frequency.value = smoothedFreq
                    _amplitude.value = rmsAmplitude // Use RMS amplitude instead of peak magnitude
                }
            }
            
            delay(50)
        }
    }
    
    private fun calculateRMSAmplitude(audioBuffer: DoubleArray): Double {
        var sumOfSquares = 0.0
        for (sample in audioBuffer) {
            sumOfSquares += sample * sample
        }
        return sqrt(sumOfSquares / audioBuffer.size)
    }
    
    private fun findFundamentalFrequency(magnitudes: DoubleArray): Double {
        val minFreq = 80.0 // E2
        val maxFreq = 1200.0 // D#6
        
        val minIndex = (minFreq * magnitudes.size / sampleRate).toInt()
        val maxIndex = (maxFreq * magnitudes.size / sampleRate).toInt().coerceAtMost(magnitudes.size - 1)
        
        var maxMagnitude = 0.0
        var peakIndex = 0
        
        for (i in minIndex..maxIndex) {
            if (magnitudes[i] > maxMagnitude) {
                maxMagnitude = magnitudes[i]
                peakIndex = i
            }
        }
        
        if (maxMagnitude < 0.01) return 0.0
        
        val freq = peakIndex * sampleRate.toDouble() / magnitudes.size
        return parabolicInterpolation(magnitudes, peakIndex, sampleRate, magnitudes.size)
    }
    
    private fun parabolicInterpolation(magnitudes: DoubleArray, peakIndex: Int, sampleRate: Int, fftSize: Int): Double {
        if (peakIndex <= 0 || peakIndex >= magnitudes.size - 1) {
            return peakIndex * sampleRate.toDouble() / fftSize
        }
        
        val y1 = magnitudes[peakIndex - 1]
        val y2 = magnitudes[peakIndex]
        val y3 = magnitudes[peakIndex + 1]
        
        val a = (y1 - 2 * y2 + y3) / 2
        val b = (y3 - y1) / 2
        
        val xp = if (a != 0.0) -b / (2 * a) else 0.0
        val interpolatedIndex = peakIndex + xp
        
        return interpolatedIndex * sampleRate / fftSize
    }
    
    private fun nextPowerOfTwo(n: Int): Int {
        return 2.0.pow(kotlin.math.ceil(log2(n.toDouble()))).toInt()
    }
    
    private fun applySmoothingFilter(currentFreq: Double): Double {
        if (currentFreq <= 0.0) {
            // Reset smoothing state when no signal
            frequencyHistory.clear()
            previousSmoothedFreq = 0.0
            return 0.0
        }
        
        // Add current frequency to history
        frequencyHistory.add(currentFreq)
        
        // Keep only recent frequencies
        if (frequencyHistory.size > smoothingBufferSize) {
            frequencyHistory.removeAt(0)
        }
        
        // Apply rate limiting - reject sudden large jumps
        if (previousSmoothedFreq > 0.0) {
            val frequencyDiff = kotlin.math.abs(currentFreq - previousSmoothedFreq)
            if (frequencyDiff > maxFrequencyJump) {
                // Use previous frequency if jump is too large
                return previousSmoothedFreq
            }
        }
        
        // Calculate moving average
        val movingAverage = frequencyHistory.average()
        
        // Apply low-pass filter (exponential smoothing)
        val smoothedFreq = if (previousSmoothedFreq > 0.0) {
            previousSmoothedFreq * (1 - smoothingFactor) + movingAverage * smoothingFactor
        } else {
            movingAverage
        }
        
        previousSmoothedFreq = smoothedFreq
        return smoothedFreq
    }
}