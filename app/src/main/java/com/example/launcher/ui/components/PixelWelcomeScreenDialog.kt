package com.example.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.IconShapePreset
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.TemperatureUnit

/**
 * First-launch Welcome & Quick Setup Dialog for PixelHome:
 * Walks the user through basic customization options on first launch:
 * 1. Weather Temperature Unit: Celsius (°C, selected by default), Fahrenheit (°F), or Kelvin (K)
 * 2. Homepage-to-Homepage Swipe Vibration: Ask if the user wants haptic vibration when moving between homepages
 * 3. Icon Shape Quick Pick & Default Home App setup
 *
 * Designed with BoxWithConstraints & 2×2 responsive grids so it fits any screen resolution
 * (compact 320dp phones, tall 20:9 displays, landscape mode, foldables, and tablets) without text stretching.
 */
@Composable
fun PixelWelcomeScreenDialog(
    visible: Boolean,
    isDefaultHome: Boolean,
    settings: LauncherSettings = LauncherSettings(),
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit = {},
    onRequestSetDefaultHome: () -> Unit,
    onGetStarted: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val infiniteTransition = rememberInfiniteTransition(label = "WelcomeMascotPulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MascotFloatY"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            val isCompactWidth = maxWidth < 360.dp
            val isCompactHeight = maxHeight < 640.dp
            val horizontalPad = if (isCompactWidth) 14.dp else 20.dp
            val verticalPad = if (isCompactHeight) 14.dp else 20.dp

            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                    initialScale = 0.84f
                ) + fadeIn(),
                exit = scaleOut(targetScale = 0.88f) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 12.dp,
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(0.96f)
                        .heightIn(max = maxHeight * 0.94f)
                        .testTag("pixel_welcome_dialog")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPad, vertical = verticalPad)
                    ) {
                        // Animated Hero Mascot Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompactHeight) 82.dp else 98.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF19243B),
                                            Color(0xFF283A5E),
                                            Color(0xFF1A2A44)
                                        )
                                    )
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (isCompactHeight) 56.dp else 64.dp)
                                    .graphicsLayer {
                                        translationY = floatOffset
                                    }
                                    .clip(SquircleShape(exponent = settings.squircleExponent, preset = settings.iconShape))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            )
                                        )
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(if (isCompactHeight) 28.dp else 32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Welcome to PixelHome ✨",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (isCompactWidth) 20.sp else 23.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Let's customize your basic home screen preferences before you begin:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // =========================================================================
                        // STEP 1: Weather Temperature Unit (Celsius selected by default, Fahrenheit, Kelvin)
                        // =========================================================================
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("welcome_temperature_unit_card")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(SquircleShape(5.4f))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WbSunny,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "1. Weather Temperature Unit",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Choose Celsius (°C), Fahrenheit (°F), or Kelvin (K)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TemperatureUnit.entries.forEach { unit ->
                                        val isSelected = settings.temperatureUnit == unit
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                                            },
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    onUpdateSettings { it.copy(temperatureUnit = unit) }
                                                }
                                                .testTag("welcome_temp_${unit.name.lowercase()}")
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = unit.formatTemp(72),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.ExtraBold
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = when (unit) {
                                                        TemperatureUnit.CELSIUS -> "Celsius"
                                                        TemperatureUnit.FAHRENHEIT -> "Fahrenheit"
                                                        TemperatureUnit.KELVIN -> "Kelvin"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // =========================================================================
                        // STEP 2: Homepage-to-Homepage Vibration Option
                        // =========================================================================
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("welcome_page_vibration_card")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onUpdateSettings {
                                                it.copy(pageSwipeVibrationEnabled = !it.pageSwipeVibrationEnabled)
                                            }
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(SquircleShape(5.4f))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Vibration,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "2. Homepage Swipe Vibration",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Do you want tactile vibration whenever moving from homepage to homepage?",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.pageSwipeVibrationEnabled,
                                        onCheckedChange = { enabled ->
                                            onUpdateSettings { it.copy(pageSwipeVibrationEnabled = enabled) }
                                        },
                                        modifier = Modifier.testTag("welcome_page_vibration_switch")
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = settings.pageSwipeVibrationEnabled,
                                        onClick = {
                                            onUpdateSettings { it.copy(pageSwipeVibrationEnabled = true) }
                                        },
                                        leadingIcon = if (settings.pageSwipeVibrationEnabled) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        label = {
                                            Text(
                                                text = "Vibrate on swipe",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = !settings.pageSwipeVibrationEnabled,
                                        onClick = {
                                            onUpdateSettings { it.copy(pageSwipeVibrationEnabled = false) }
                                        },
                                        leadingIcon = if (!settings.pageSwipeVibrationEnabled) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        label = {
                                            Text(
                                                text = "No vibration",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // =========================================================================
                        // STEP 3: Squarer Icon Style Quick Pick (Responsive 2x2 Grid so labels never stretch)
                        // =========================================================================
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "3. Icon Shape Style",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val shapeRows = listOf(
                                    listOf(
                                        IconShapePreset.SQUIRCLE to "Squircle",
                                        IconShapePreset.ROUNDED_SQUARE to "Square Circle"
                                    ),
                                    listOf(
                                        IconShapePreset.CIRCLE to "Circle",
                                        IconShapePreset.TEARDROP to "Teardrop"
                                    )
                                )

                                shapeRows.forEach { rowPairs ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowPairs.forEach { (preset, label) ->
                                            val selected = settings.iconShape == preset
                                            val previewShape = SquircleShape(
                                                exponent = settings.squircleExponent,
                                                preset = preset
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                                                },
                                                border = BorderStroke(
                                                    width = if (selected) 2.dp else 1.dp,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable {
                                                        onUpdateSettings { it.copy(iconShape = preset) }
                                                    }
                                                    .testTag("welcome_shape_${preset.name.lowercase()}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(previewShape)
                                                            .background(
                                                                if (selected) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                }
                                                            )
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isDefaultHome) {
                            OutlinedButton(
                                onClick = onRequestSetDefaultHome,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("welcome_set_default_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Set PixelHome as Default Home App",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = onGetStarted,
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("welcome_get_started_button")
                        ) {
                            Text(
                                text = "Start Using PixelHome 🚀",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
