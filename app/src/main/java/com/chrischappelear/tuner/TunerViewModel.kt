package com.chrischappelear.tuner

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrischappelear.tuner.audio.AudioRecorder
import com.chrischappelear.tuner.audio.CaptureProfile
import com.chrischappelear.tuner.data.SettingsRepository
import com.chrischappelear.tuner.data.TunerSettings
import com.chrischappelear.tuner.tuning.TracePoint
import com.chrischappelear.tuner.tuning.TunerState
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.TuningProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TunerUiState(
    val tuner: TunerState = TunerState(),
    val history: List<TracePoint> = emptyList()
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = AudioRecorder(application)
    private val settingsRepository = SettingsRepository(application)
    private val processor = TuningProcessor()

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val settings: StateFlow<TunerSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, TunerSettings())

    /**
     * Read from the repository rather than [settings], whose chromatic placeholder would
     * open the microphone once with the wrong profile before a saved bass tuning loads.
     */
    private val captureProfile = settingsRepository.settings
        .map { CaptureProfile.forTuning(it.tuning) }
        .distinctUntilChanged()

    /**
     * Listens only while collected and permitted. The 2 s grace period keeps the
     * microphone open across a rotation but releases it soon after the app leaves
     * the screen. Moving between bass and other tunings reopens it with a new profile.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TunerUiState> = combine(_hasPermission, captureProfile, ::Pair)
        .flatMapLatest { (granted, profile) ->
            if (!granted) emptyFlow()
            else recorder.frames(profile)
                .onStart { processor.reset() }
                .map { estimate ->
                    val current = settings.value
                    processor.configure(current.tuning, current.a4)
                    val state = processor.process(estimate, SystemClock.elapsedRealtime())
                    TunerUiState(state, processor.history.snapshot())
                }
                .flowOn(Dispatchers.Default)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), TunerUiState())

    fun refreshPermission() {
        _hasPermission.value = checkPermission()
    }

    fun setTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.setTuning(tuning) }
    }

    /** Saves a new or edited custom tuning and selects it. */
    fun saveCustomTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.saveCustomTuning(tuning) }
    }

    fun deleteCustomTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.deleteCustomTuning(tuning.id) }
    }

    fun setA4(a4: Double) {
        viewModelScope.launch { settingsRepository.setA4(a4) }
    }

    private fun checkPermission() = ContextCompat.checkSelfPermission(
        getApplication(), Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
