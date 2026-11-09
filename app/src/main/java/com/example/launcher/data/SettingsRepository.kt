package com.example.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.launcher.model.LauncherSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pixel_home_settings"
)

class SettingsRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val settingsAdapter = moshi.adapter(LauncherSettings::class.java)
    private val keySettingsJson = stringPreferencesKey("launcher_settings_json_v1")

    val settingsFlow: Flow<LauncherSettings> = context.launcherDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val rawJson = prefs[keySettingsJson]
            if (rawJson.isNullOrBlank()) {
                LauncherSettings()
            } else {
                runCatching { settingsAdapter.fromJson(rawJson) }.getOrNull() ?: LauncherSettings()
            }
        }

    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        withContext(ioDispatcher) {
            context.launcherDataStore.edit { prefs ->
                val current = prefs[keySettingsJson]?.let {
                    runCatching { settingsAdapter.fromJson(it) }.getOrNull()
                } ?: LauncherSettings()
                val updated = transform(current)
                prefs[keySettingsJson] = settingsAdapter.toJson(updated)
            }
        }
    }

    suspend fun resetToDefaults() {
        withContext(ioDispatcher) {
            context.launcherDataStore.edit { prefs ->
                prefs[keySettingsJson] = settingsAdapter.toJson(LauncherSettings())
            }
        }
    }

    fun exportToJson(settings: LauncherSettings): String {
        return settingsAdapter.indent("  ").toJson(settings)
    }

    suspend fun importFromJson(json: String): Boolean {
        return withContext(ioDispatcher) {
            val parsed = runCatching { settingsAdapter.fromJson(json) }.getOrNull()
                ?: return@withContext false
            context.launcherDataStore.edit { prefs ->
                prefs[keySettingsJson] = settingsAdapter.toJson(parsed)
            }
            true
        }
    }
}
