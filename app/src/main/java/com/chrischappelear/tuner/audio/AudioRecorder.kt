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
    private val bufferSize = 8192 // Increased for better frequency resolution
    private val fft = FFT()
    
    // Volume threshold to ignore background noise
    private val volumeThreshold = 0.005 // Adjust this value as needed
    
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
                    
                    // Apply Hann window to reduce spectral leakage
                    for (i in doubleBuffer.indices) {
                        val windowValue = 0.5 * (1 - kotlin.math.cos(2 * kotlin.math.PI * i / (doubleBuffer.size - 1)))
                        paddedBuffer[i] = doubleBuffer[i] * windowValue
                    }
                    
                    val fftResult = fft.fft(paddedBuffer)
                    val magnitudes = fft.getMagnitudeSpectrum(fftResult)
                    
                    val fundamentalFreq = findFundamentalFrequency(magnitudes)
                    val maxAmplitude = magnitudes.maxOrNull() ?: 0.0
                    
                    _frequency.value = fundamentalFreq
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
        
        // Find peaks above noise threshold
        val peaks = mutableListOf<Pair<Int, Double>>()
        val noiseThreshold = magnitudes.slice(0..minIndex/2).maxOrNull() ?: 0.0
        val dynamicThreshold = kotlin.math.max(noiseThreshold * 3.0, 0.01)
        
        // Collect significant peaks
        for (i in minIndex..maxIndex) {
            if (magnitudes[i] > dynamicThreshold) {
                // Check if it's a local maximum
                val isLocalMax = (i == minIndex || magnitudes[i] > magnitudes[i-1]) &&
                                (i == maxIndex || magnitudes[i] > magnitudes[i+1])
                if (isLocalMax) {
                    peaks.add(i to magnitudes[i])
                }
            }
        }
        
        if (peaks.isEmpty()) return 0.0
        
        // Use harmonic product spectrum (HPS) to find true fundamental
        val fundamentalCandidate = findFundamentalWithHPS(peaks, magnitudes)
        
        return parabolicInterpolation(magnitudes, fundamentalCandidate, sampleRate, magnitudes.size)
    }
    
    private fun findFundamentalWithHPS(peaks: List<Pair<Int, Double>>, magnitudes: DoubleArray): Int {
        var bestCandidate = 0
        var bestScore = 0.0
        
        // First, check if any peak could be a harmonic of a missing fundamental
        val potentialFundamentals = mutableMapOf<Int, Double>()
        
        for (peak in peaks) {
            val peakFreq = peak.first * sampleRate.toDouble() / magnitudes.size
            
            // Check if this peak could be the 2nd, 3rd, 4th, or 5th harmonic of a fundamental
            for (harmonicNum in 2..5) {
                val potentialFundamentalFreq = peakFreq / harmonicNum
                if (potentialFundamentalFreq >= 80.0 && potentialFundamentalFreq <= 400.0) {
                    val potentialFundamentalIndex = (potentialFundamentalFreq * magnitudes.size / sampleRate).toInt()
                    
                    // Look for this potential fundamental (even if weak)
                    var fundamentalMagnitude = 0.0
                    for (offset in -4..4) {
                        val testIndex = (potentialFundamentalIndex + offset).coerceIn(0, magnitudes.size - 1)
                        fundamentalMagnitude = kotlin.math.max(fundamentalMagnitude, magnitudes[testIndex])
                    }
                    
                    // Score based on harmonic strength even if fundamental is weak
                    val harmonicWeight = peak.second / harmonicNum // Weight by harmonic number
                    val totalScore = fundamentalMagnitude * 2.0 + harmonicWeight // Boost fundamental detection
                    
                    potentialFundamentals[potentialFundamentalIndex] = 
                        kotlin.math.max(potentialFundamentals[potentialFundamentalIndex] ?: 0.0, totalScore)
                }
            }
        }
        
        // Test both direct peaks and inferred fundamentals
        val allCandidates = peaks.map { it.first to it.second }.toMutableList()
        allCandidates.addAll(potentialFundamentals.toList())
        
        for ((candidateIndex, candidateScore) in allCandidates) {
            val fundamentalFreq = candidateIndex * sampleRate.toDouble() / magnitudes.size
            
            // Skip if too high to be a fundamental for guitar
            if (fundamentalFreq > 400.0) continue
            
            var harmonicScore = candidateScore
            
            // Check for harmonics at 2f, 3f, 4f, 5f
            for (harmonicNum in 2..5) {
                val harmonicIndex = (candidateIndex * harmonicNum).coerceAtMost(magnitudes.size - 1)
                
                // Look for peak within ±4 bins of expected harmonic (wider tolerance)
                var harmonicMagnitude = 0.0
                for (offset in -4..4) {
                    val testIndex = (harmonicIndex + offset).coerceIn(0, magnitudes.size - 1)
                    harmonicMagnitude = kotlin.math.max(harmonicMagnitude, magnitudes[testIndex])
                }
                
                // Weight lower harmonics more heavily
                val weight = 1.0 / harmonicNum
                harmonicScore += harmonicMagnitude * weight
            }
            
            // Strong boost for very low frequencies (guitar fundamentals)
            val frequencyBoost = when {
                fundamentalFreq < 100.0 -> 3.0  // Very strong boost for bass notes
                fundamentalFreq < 200.0 -> 2.0  // Strong boost for low notes
                else -> 1.0
            }
            harmonicScore *= frequencyBoost
            
            if (harmonicScore > bestScore) {
                bestScore = harmonicScore
                bestCandidate = candidateIndex
            }
        }
        
        // Fallback to lowest frequency peak if no good fundamental found
        return if (bestCandidate == 0) {
            peaks.minByOrNull { it.first * sampleRate.toDouble() / magnitudes.size }?.first ?: 0
        } else {
            bestCandidate
        }
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
}