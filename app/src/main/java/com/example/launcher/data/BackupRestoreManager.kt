package com.example.launcher.data

import com.example.launcher.data.db.AppUsageEntity
import com.example.launcher.data.db.HomeDao
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.model.LauncherSettings
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class LauncherFullBackupBundle(
    val version: Int = 1,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val settings: LauncherSettings = LauncherSettings(),
    val homeItems: List<HomeItemEntity> = emptyList(),
    val appUsageStats: List<AppUsageEntity> = emptyList()
)

/**
 * Exports and restores the complete launcher state (DataStore [LauncherSettings] + Room [HomeItemEntity]
 * layout + [AppUsageEntity] launch history) as a single human-readable JSON document.
 */
class BackupRestoreManager(
    private val settingsRepository: SettingsRepository,
    private val homeDao: HomeDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(LauncherFullBackupBundle::class.java).indent("  ")

    suspend fun exportFullBackupJson(currentSettings: LauncherSettings): String = withContext(ioDispatcher) {
        val items = homeDao.getAllHomeItemsOnce()
        val bundle = LauncherFullBackupBundle(
            version = 1,
            exportedAtEpochMs = System.currentTimeMillis(),
            settings = currentSettings,
            homeItems = items
        )
        backupAdapter.toJson(bundle)
    }

    suspend fun importFullBackupJson(json: String): Boolean = withContext(ioDispatcher) {
        val parsed = runCatching { backupAdapter.fromJson(json) }.getOrNull()
        if (parsed != null) {
            settingsRepository.updateSettings { parsed.settings }
            if (parsed.homeItems.isNotEmpty()) {
                homeDao.clearAllHomeItems()
                homeDao.insertHomeItems(parsed.homeItems)
            }
            return@withContext true
        }
        // Fallback: check if the user pasted a standalone LauncherSettings JSON object
        settingsRepository.importFromJson(json)
    }
}
