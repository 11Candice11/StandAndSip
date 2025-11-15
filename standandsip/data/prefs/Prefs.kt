package com.standandsip.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("stand_sip_prefs")

object Prefs {
    private val KEY_START_MIN = intPreferencesKey("start_minutes")   // minutes from midnight
    private val KEY_END_MIN   = intPreferencesKey("end_minutes")
    private val KEY_INTERVAL  = intPreferencesKey("interval_minutes")

    // Defaults: 08:00 → 20:00 every 60 minutes
    const val DEFAULT_START_MIN = 8 * 60
    const val DEFAULT_END_MIN   = 20 * 60
    const val DEFAULT_INTERVAL  = 60

    data class Settings(
        val startMin: Int = DEFAULT_START_MIN,
        val endMin: Int   = DEFAULT_END_MIN,
        val intervalMin: Int = DEFAULT_INTERVAL
    )

    fun flow(context: Context) = context.dataStore.data.map { p ->
        Settings(
            startMin = p[KEY_START_MIN] ?: DEFAULT_START_MIN,
            endMin   = p[KEY_END_MIN]   ?: DEFAULT_END_MIN,
            intervalMin = p[KEY_INTERVAL] ?: DEFAULT_INTERVAL
        )
    }

    suspend fun save(context: Context, s: Settings) {
        context.dataStore.edit { p ->
            p[KEY_START_MIN] = s.startMin
            p[KEY_END_MIN]   = s.endMin
            p[KEY_INTERVAL]  = s.intervalMin
        }
    }
}