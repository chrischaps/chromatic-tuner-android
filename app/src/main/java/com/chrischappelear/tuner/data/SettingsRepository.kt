package com.chrischappelear.tuner.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chrischappelear.tuner.tuning.NoteMath
import com.chrischappelear.tuner.tuning.Tuning
import com.chrischappelear.tuner.tuning.TuningCodec
import com.chrischappelear.tuner.tuning.Tunings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TunerSettings(
    val tuning: Tuning = Tunings.Chromatic,
    val a4: Double = NoteMath.DEFAULT_A4,
    val customTunings: List<Tuning> = emptyList()
)

private val Context.dataStore by preferencesDataStore(name = "tuner_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<TunerSettings> = context.dataStore.data.map { prefs ->
        val custom = TuningCodec.decode(prefs[CUSTOM_TUNINGS].orEmpty())
        TunerSettings(
            tuning = Tunings.byId(prefs[TUNING], custom),
            a4 = (prefs[A4] ?: NoteMath.DEFAULT_A4).coerceIn(A4_RANGE),
            customTunings = custom
        )
    }

    suspend fun setTuning(tuning: Tuning) {
        context.dataStore.edit { it[TUNING] = tuning.id }
    }

    /** Adds [tuning], or replaces the custom tuning with its id, and selects it. */
    suspend fun saveCustomTuning(tuning: Tuning) {
        context.dataStore.edit { prefs ->
            val saved = TuningCodec.decode(prefs[CUSTOM_TUNINGS].orEmpty())
            val updated = if (saved.any { it.id == tuning.id }) saved.map { if (it.id == tuning.id) tuning else it }
            else saved + tuning
            prefs[CUSTOM_TUNINGS] = TuningCodec.encode(updated)
            prefs[TUNING] = tuning.id
        }
    }

    /** Removes a custom tuning; if it was selected, the tuner falls back to chromatic. */
    suspend fun deleteCustomTuning(id: String) {
        context.dataStore.edit { prefs ->
            val saved = TuningCodec.decode(prefs[CUSTOM_TUNINGS].orEmpty())
            prefs[CUSTOM_TUNINGS] = TuningCodec.encode(saved.filterNot { it.id == id })
            if (prefs[TUNING] == id) prefs.remove(TUNING)
        }
    }

    suspend fun setA4(a4: Double) {
        context.dataStore.edit { it[A4] = a4.coerceIn(A4_RANGE) }
    }

    companion object {
        val A4_RANGE = 432.0..446.0
        private val TUNING = stringPreferencesKey("tuning")
        private val A4 = doublePreferencesKey("a4")
        private val CUSTOM_TUNINGS = stringPreferencesKey("custom_tunings")
    }
}
