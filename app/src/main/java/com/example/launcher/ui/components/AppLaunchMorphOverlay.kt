package com.example.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.launcher.animation.AppWindowMorphState
import com.example.launcher.animation.LauncherPhysics
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AppOpenAnimationStyle
import com.example.launcher.model.LauncherSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silky spring-driven App Open / App Close window morph overlay.
 *
 * How it works without mid-gesture/mid-animation recomposition:
 * - A single `morphProgress` [Animatable] (0f = exact icon bounds on the home grid/hotseat/drawer,
 *   1f = full-screen application window) is driven by [LauncherPhysics.springSpec].
 * - Position (`translationX`, `translationY`), dimensions (`scaleX`, `scaleY`), opacity (`alpha`),
 *   and squircle clipping are evaluated exclusively inside `Modifier.graphicsLayer` during the draw phase.
 * - When launching an app (`isClosingToIcon == false`), the icon expands from `0f -> 1f` into the
 *   app window; when returning to the home screen (`isClosingToIcon == true`), the app window shrinks
 *   from `1f -> 0f` back into its exact icon cell!
 */
@Composable
fun AppWindowMorphOverlay(
    morphState: AppWindowMorphState?,
    iconCache: IconCache,
    settings: LauncherSettings,
    onAnimationFinished: () -> Unit
) {
    if (morphState == null) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val app = morphState.app
    val iconRect = morphState.iconBoundsOnScreen

    var iconLayers by remember(app.componentKey) {
        mutableStateOf(iconCache.getCachedIfPresent(app.componentKey))
    }

    LaunchedEffect(app.componentKey) {
        if (iconLayers == null) {
            iconLayers = iconCache.getOrLoadIcon(app)
        }
    }

    // 0f = at icon cell bounds, 1f = full screen window
    val progress = remember(morphState.triggerId) {
        Animatable(if (morphState.isClosingToIcon) 1f else 0f)
    }

    LaunchedEffect(morphState.triggerId) {
        val target = if (morphState.isClosingToIcon) 0f else 1f
        progress.animateTo(
            targetValue = target,
            animationSpec = LauncherPhysics.springSpec(
                settings = settings,
                context = context,
                stiffnessScale = if (morphState.isClosingToIcon) 1.15f else 1.25f
            )
        )
        onAnimationFinished()
    }

    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(
            exponent = settings.squircleExponent,
            preset = settings.iconShape
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_window_morph_overlay")
    ) {
        val fullWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val fullHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        val iconWidthPx = iconRect.width().toFloat().coerceAtLeast(with(density) { 56.dp.toPx() })
        val iconHeightPx = iconRect.height().toFloat().coerceAtLeast(with(density) { 56.dp.toPx() })
        val iconCenterX = if (iconRect.width() > 0) iconRect.exactCenterX() else fullWidthPx / 2f
        val iconCenterY = if (iconRect.height() > 0) iconRect.exactCenterY() else fullHeightPx / 2f

        val screenCenterX = fullWidthPx / 2f
        val screenCenterY = fullHeightPx / 2f

        // Render full-screen surface and scale/translate it in graphicsLayer from icon bounds <-> screen bounds
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    when (settings.appOpenAnimationStyle) {
                        AppOpenAnimationStyle.ICON_ZOOM -> {
                            val currentScaleX = lerp(iconWidthPx / fullWidthPx, 1f, p)
                            val currentScaleY = lerp(iconHeightPx / fullHeightPx, 1f, p)
                            val currentCenterX = lerp(iconCenterX, screenCenterX, p)
                            val currentCenterY = lerp(iconCenterY, screenCenterY, p)

                            scaleX = currentScaleX
                            scaleY = currentScaleY
                            translationX = currentCenterX - screenCenterX
                            translationY = currentCenterY - screenCenterY
                            // Fade out smoothly at the very end of closing back into the icon
                            alpha = if (morphState.isClosingToIcon) {
                                (p * 3.2f).coerceIn(0f, 1f)
                            } else {
                                ((1f - p) * 2.5f).coerceIn(0.18f, 1f)
                            }
                            clip = true
                            shape = squircleShape
                        }

                        AppOpenAnimationStyle.FADE -> {
                            val s = lerp(0.88f, 1.0f, p)
                            scaleX = s
                            scaleY = s
                            alpha = if (morphState.isClosingToIcon) p else (1f - p * 0.6f)
                        }

                        AppOpenAnimationStyle.SLIDE -> {
                            translationY = (1f - p) * fullHeightPx * 0.35f
                            alpha = if (morphState.isClosingToIcon) p else (1f - p * 0.5f)
                        }
                    }
                }
                .background(Color(app.accentTintSeed))
        ) {
            // App foreground glyph centered during morph
            iconLayers?.foregroundBitmap?.let { fg ->
                Image(
                    bitmap = fg,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            val p = progress.value.coerceIn(0f, 1f)
                            // Counter-scale slightly so the glyph stays crisp as the window expands
                            val inv = lerp(2.2f, 1.0f, p)
                            scaleX = inv
                            scaleY = inv
                        }
                )
            }
        }
    }
}

/**
 * Interactive Pixel Always-On-Display (AOD) / Screen Lock overlay shown when the user double-taps
 * to lock the screen before enabling the optional system [com.example.launcher.service.PixelLockAccessibilityService].
 */
@Composable
fun SimulatedScreenLockOverlay(
    visible: Boolean,
    isAccessibilityConnected: Boolean,
    onWakeUp: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    if (!visible) return

    val timeString = remember {
        SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
    }
    val dateString = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(onClick = onWakeUp)
            .testTag("screen_lock_aod_overlay")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Screen locked",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = timeString,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateString,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.80f)
                )
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (!isAccessibilityConnected) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Always-On Lock Preview (Tap anywhere to wake)",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable PixelHome in Accessibility Settings to perform hardware screen lock (GLOBAL_ACTION_LOCK_SCREEN).",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enable Hardware Lock Service", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
