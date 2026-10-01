package com.chrischappelear.tuner.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chrischappelear.tuner.tuning.NoteMath
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.Tunings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TunerSettings(
    val tuning: Tuning = Tunings.Chromatic,
    val a4: Double = NoteMath.DEFAULT_A4
)

private val Context.dataStore by preferencesDataStore(name = "tuner_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<TunerSettings> = context.dataStore.data.map { prefs ->
        TunerSettings(
            tuning = Tunings.byId(prefs[TUNING]),
            a4 = (prefs[A4] ?: NoteMath.DEFAULT_A4).coerceIn(A4_RANGE)
        )
    }

    suspend fun setTuning(tuning: Tuning) {
        context.dataStore.edit { it[TUNING] = tuning.id }
    }

    suspend fun setA4(a4: Double) {
        context.dataStore.edit { it[A4] = a4.coerceIn(A4_RANGE) }
    }

    companion object {
        val A4_RANGE = 432.0..446.0
        private val TUNING = stringPreferencesKey("tuning")
        private val A4 = doublePreferencesKey("a4")
    }
}
