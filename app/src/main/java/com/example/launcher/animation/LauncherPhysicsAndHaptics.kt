package com.example.launcher.animation

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.example.launcher.model.AnimationPreset
import com.example.launcher.model.AppInfo
import com.example.launcher.model.HapticStrength
import com.example.launcher.model.LauncherSettings

/**
 * Represents an in-flight App Open (icon -> full window) or App Close (full window -> icon)
 * spring morph animation.
 */
data class AppWindowMorphState(
    val app: AppInfo,
    val iconBoundsOnScreen: Rect,
    val isClosingToIcon: Boolean = false,
    val triggerId: Long = System.nanoTime()
)

object LauncherPhysics {

    /**
     * Checks whether the system "Remove animations" accessibility setting is active
     * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`).
     */
    fun isSystemAnimationDisabled(context: Context): Boolean {
        return runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale <= 0.01f
        }.getOrDefault(false)
    }

    /**
     * Builds a unified [SpringSpec] that honors:
     * - `settings.animationPreset` (Pixel, Snappy, Smooth, Minimal, Off)
     * - `settings.springDampingRatio` (~0.80f default)
     * - `settings.springStiffness` (~380f default, medium-low stiffness)
     * - `settings.animationSpeedMultiplier` (0.5x..2.0x — scales stiffness by speed^2 so natural frequency scales linearly)
     * - `settings.respectSystemAnimatorScale` (snaps near-instantly when system animations are disabled)
     */
    fun <T> springSpec(
        settings: LauncherSettings,
        context: Context? = null,
        visibilityThreshold: T? = null,
        stiffnessScale: Float = 1.0f
    ): SpringSpec<T> {
        val systemOff = context != null &&
            settings.respectSystemAnimatorScale &&
            isSystemAnimationDisabled(context)

        if (settings.animationPreset == AnimationPreset.OFF || systemOff) {
            return spring(
                dampingRatio = 1.0f,
                stiffness = 100_000f,
                visibilityThreshold = visibilityThreshold
            )
        }

        val damping = settings.springDampingRatio.coerceIn(0.45f, 1.0f)
        val speed = settings.animationSpeedMultiplier.coerceIn(0.5f, 2.0f)
        // Natural frequency w_n = sqrt(k / m); scaling stiffness k by speed^2 scales animation speed linearly
        val effectiveStiffness = (settings.springStiffness * speed * speed * stiffnessScale)
            .coerceIn(90f, 15_000f)

        return spring(
            dampingRatio = damping,
            stiffness = effectiveStiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

/**
 * Hardware haptic feedback controller that respects `settings.hapticStrength`
 * (`OFF`, `LIGHT`, `MEDIUM`, `STRONG`) across long-press, snap, drop, and gesture thresholds.
 */
object LauncherHaptics {

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun performLongPress(view: View?, settings: LauncherSettings) {
        if (settings.hapticStrength == HapticStrength.OFF || view == null) return
        val context = view.context
        val vibrator = resolveVibrator(context)
        if (vibrator?.hasVibrator() == true) {
            val (durationMs, amplitude) = when (settings.hapticStrength) {
                HapticStrength.LIGHT -> 12L to 75
                HapticStrength.MEDIUM -> 20L to 150
                HapticStrength.STRONG -> 30L to 255
                HapticStrength.OFF -> return
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    settings.hapticStrength == HapticStrength.MEDIUM
                ) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }.onFailure {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun performSnapOrTick(view: View?, settings: LauncherSettings) {
        if (settings.hapticStrength == HapticStrength.OFF || view == null) return
        val context = view.context
        val vibrator = resolveVibrator(context)
        if (vibrator?.hasVibrator() == true) {
            val (durationMs, amplitude) = when (settings.hapticStrength) {
                HapticStrength.LIGHT -> 6L to 55
                HapticStrength.MEDIUM -> 10L to 120
                HapticStrength.STRONG -> 16L to 210
                HapticStrength.OFF -> return
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    settings.hapticStrength != HapticStrength.STRONG
                ) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }.onFailure {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun performGestureConfirm(view: View?, settings: LauncherSettings) {
        if (settings.hapticStrength == HapticStrength.OFF || view == null) return
        val context = view.context
        val vibrator = resolveVibrator(context)
        if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
