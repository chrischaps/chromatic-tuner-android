package com.chrischappelear.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrischappelear.tuner.tuning.PitchHistoryPoint
import com.chrischappelear.tuner.tuning.TunerEngine
import com.chrischappelear.tuner.tuning.TuningResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TunerViewModel : ViewModel() {
    private val tunerEngine = TunerEngine()
    
    private val _tuningResult = MutableStateFlow(TuningResult())
    val tuningResult: StateFlow<TuningResult> = _tuningResult
    
    private val _pitchHistory = MutableStateFlow(emptyList<PitchHistoryPoint>())
    val pitchHistory: StateFlow<List<PitchHistoryPoint>> = _pitchHistory
    
    init {
        viewModelScope.launch {
            tunerEngine.tuningResult.collect { result ->
                _tuningResult.value = result
            }
        }
        
        viewModelScope.launch {
            tunerEngine.pitchHistoryData.collect { history ->
                _pitchHistory.value = history
            }
        }
        
        // Auto-start tuning when ViewModel is created
        tunerEngine.startTuning()
    }
    
    override fun onCleared() {
        super.onCleared()
        tunerEngine.stopTuning()
    }
}