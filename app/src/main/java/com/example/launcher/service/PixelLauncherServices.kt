package com.example.launcher.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Optional AccessibilityService used exclusively for performing global system launcher actions
 * that require privileged access on public Android APIs:
 * - Locking the screen on double-tap (`GLOBAL_ACTION_LOCK_SCREEN`, API 28+)
 * - Expanding the notification shade (`GLOBAL_ACTION_NOTIFICATIONS`)
 * - Expanding quick settings (`GLOBAL_ACTION_QUICK_SETTINGS`)
 */
class PixelLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No window content inspection is performed; used solely for global launcher gesture actions.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: PixelLockAccessibilityService? = null

        val isConnected: Boolean
            get() = instance != null

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }

        fun expandNotifications(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }

        fun expandQuickSettings(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }
}
