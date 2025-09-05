package com.chrischappelear.tuner.tuning

import com.chrischappelear.tuner.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TuningResult(
    val frequency: Double = 0.0,
    val note: Note = Note("", 0.0, 0),
    val centsOffset: Int = 0,
    val isInTune: Boolean = false,
    val amplitude: Double = 0.0,
    val isActive: Boolean = false
)

class TunerEngine {
    private val audioRecorder = AudioRecorder()
    private val pitchHistory = PitchHistory()
    
    private val _tuningResult = MutableStateFlow(TuningResult())
    val tuningResult: StateFlow<TuningResult> = _tuningResult
    
    private val _pitchHistoryData = MutableStateFlow(emptyList<PitchHistoryPoint>())
    val pitchHistoryData: StateFlow<List<PitchHistoryPoint>> = _pitchHistoryData
    
    init {
        CoroutineScope(Dispatchers.Main).launch {
            combine(
                audioRecorder.frequency,
                audioRecorder.amplitude
            ) { frequency, amplitude ->
                processTuningData(frequency, amplitude)
            }.collect { result ->
                _tuningResult.value = result
                // Add to pitch history and update history data flow
                pitchHistory.addPoint(result)
                _pitchHistoryData.value = pitchHistory.history
            }
        }
    }
    
    private fun processTuningData(frequency: Double, amplitude: Double): TuningResult {
        val minAmplitude = 0.001
        val minFrequency = 70.0
        val maxFrequency = 1200.0
        
        if (amplitude < minAmplitude || frequency < minFrequency || frequency > maxFrequency) {
            return TuningResult(
                frequency = frequency,
                amplitude = amplitude,
                isActive = false
            )
        }
        
        val closestNote = NoteFrequencies.getClosestNote(frequency)
        val centsOffset = NoteFrequencies.getCentsOffset(frequency, closestNote)
        val isInTune = NoteFrequencies.isInTune(centsOffset)
        
        return TuningResult(
            frequency = frequency,
            note = closestNote,
            centsOffset = centsOffset,
            isInTune = isInTune,
            amplitude = amplitude,
            isActive = true
        )
    }
    
    fun startTuning() {
        audioRecorder.startRecording()
    }
    
    fun stopTuning() {
        audioRecorder.stopRecording()
        _tuningResult.value = TuningResult()
        pitchHistory.clear()
        _pitchHistoryData.value = emptyList()
    }
}