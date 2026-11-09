package com.example.launcher.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Optional [NotificationListenerService] that powers live notification dots and counts
 * on Home, Hotseat, Drawer, and Folder icons.
 *
 * Also supports simulated badge counts so users can preview DOT vs COUNT badge styles
 * before granting system notification listener access.
 */
class PixelNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        refreshActiveNotificationCounts()
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveNotificationCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveNotificationCounts()
    }

    private fun refreshActiveNotificationCounts() {
        runCatching {
            val active = activeNotifications ?: return
            val counts = mutableMapOf<String, Int>()
            for (sbn in active) {
                if (sbn.isOngoing) continue
                val pkg = sbn.packageName ?: continue
                val number = sbn.notification.number.coerceAtLeast(1)
                counts[pkg] = (counts[pkg] ?: 0) + number
            }
            _liveNotificationCounts.value = counts
        }
    }

    companion object {
        @Volatile
        var isListenerConnected: Boolean = false
            private set

        private val _liveNotificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        private val _simulatedCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

        val liveNotificationCounts: StateFlow<Map<String, Int>> = _liveNotificationCounts.asStateFlow()
        val simulatedCounts: StateFlow<Map<String, Int>> = _simulatedCounts.asStateFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            return runCatching {
                val cn = ComponentName(context, PixelNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                flat != null && flat.contains(cn.flattenToString())
            }.getOrDefault(false)
        }

        fun setSimulatedBadgesForPackages(packages: List<String>, enabled: Boolean) {
            if (!enabled) {
                _simulatedCounts.value = emptyMap()
            } else {
                val map = mutableMapOf<String, Int>()
                packages.take(6).forEachIndexed { index, pkg ->
                    map[pkg] = (index % 5) + 1
                }
                _simulatedCounts.value = map
            }
        }

        fun clearBadgeForPackage(packageName: String) {
            _simulatedCounts.update { it - packageName }
            _liveNotificationCounts.update { it - packageName }
        }
    }
}
