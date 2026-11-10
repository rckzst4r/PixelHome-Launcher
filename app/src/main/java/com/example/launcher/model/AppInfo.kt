package com.example.launcher.model

import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Immutable

@Immutable
data class AppShortcutItem(
    val id: String,
    val shortLabel: String,
    val longLabel: String,
    val packageName: String
)

enum class AppCategoryType(val displayName: String) {
    COMMUNICATION("Social & Chat"),
    PRODUCTIVITY("Productivity"),
    MEDIA("Media & Photo"),
    TOOLS("System & Tools"),
    GAMES("Games"),
    OTHER("Apps")
}

/**
 * Represents an installed launchable application across Personal or Work profiles.
 * Marked [@Immutable] and caches derived keys so Compose skips unnecessary recompositions.
 */
@Immutable
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val userSerial: Long = 0L,
    val userHandle: UserHandle = Process.myUserHandle(),
    val isWorkProfile: Boolean = false,
    val firstInstallTime: Long = 0L,
    val category: AppCategoryType = AppCategoryType.OTHER,
    val launchCount: Int = 0,
    val lastLaunchTimestamp: Long = 0L,
    val notificationCount: Int = 0,
    val shortcuts: List<AppShortcutItem> = emptyList(),
    val accentTintSeed: Int = 0xFF4285F4.toInt(),
    val isSynthesizedSample: Boolean = false,
    val isSystemApp: Boolean = false,
    val isUpdatedSystemApp: Boolean = false
) {
    val componentKey: String = "$packageName/$activityName#$userSerial"

    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val canSystemUninstall: Boolean
        get() = !isSynthesizedSample && (!isSystemApp || isUpdatedSystemApp)

    val initialLetter: String = run {
        val first = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
        if (first in 'A'..'Z') first.toString() else "#"
    }
}
