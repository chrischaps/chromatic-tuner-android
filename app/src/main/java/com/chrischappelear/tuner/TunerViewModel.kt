package com.chrischappelear.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrischappelear.tuner.tuning.TunerEngine
import com.chrischappelear.tuner.tuning.TuningResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TunerViewModel : ViewModel() {
    private val tunerEngine = TunerEngine()
    
    private val _tuningResult = MutableStateFlow(TuningResult())
    val tuningResult: StateFlow<TuningResult> = _tuningResult
    
    init {
        viewModelScope.launch {
            tunerEngine.tuningResult.collect { result ->
                _tuningResult.value = result
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