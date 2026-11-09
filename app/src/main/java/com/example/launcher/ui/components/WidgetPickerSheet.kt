package com.example.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.launcher.model.BadgeStyle
import com.example.launcher.model.LauncherSettings
import com.example.launcher.widget.BuiltInPixelWidgetType
import com.example.launcher.widget.SystemWidgetCandidate

/**
 * Bottom sheet for browsing and adding:
 * - Built-in Material You Pixel Companion Widgets (Clock, Weather, Battery)
 * - System installed AppWidgetProviders via [android.appwidget.AppWidgetHost]
 * - Notification Badge Settings (`DOT`, `COUNT`, `OFF`, Notification Listener Access, and Live Preview Badges)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetAndBadgePickerSheet(
    systemWidgets: List<SystemWidgetCandidate>,
    settings: LauncherSettings,
    isNotificationAccessGranted: Boolean,
    simulatedBadgesActive: Boolean,
    onAddBuiltInWidget: (BuiltInPixelWidgetType) -> Unit,
    onAddSystemWidget: (SystemWidgetCandidate) -> Unit,
    onUpdateBadgeStyle: (BadgeStyle) -> Unit,
    onToggleSimulatedBadges: (Boolean) -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("widget_picker_sheet")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Widgets & Notification Badges",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Add Material You Pixel widgets, system AppWidgets & configure dots",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // 1. Built-in Material You Pixel Widgets
            Text(
                text = "Pixel Material You Widgets",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            BuiltInPixelWidgetType.entries.forEach { widgetType ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAddBuiltInWidget(widgetType)
                            onDismiss()
                        }
                        .testTag("add_widget_${widgetType.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = widgetType.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "${widgetType.subtitle} · ${widgetType.defaultSpanX}×${widgetType.defaultSpanY} cells",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                onAddBuiltInWidget(widgetType)
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }

            HorizontalDivider()

            // 2. Notification Badges (DOT / COUNT / OFF + NotificationListenerService)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Notification Badges",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BadgeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.badgeStyle == style,
                        onClick = { onUpdateBadgeStyle(style) },
                        label = { Text(style.displayName) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSimulatedBadges(!simulatedBadgesActive) }
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Preview Live Notification Badges",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Populate active badge counts on top apps to test Dot vs Count styles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = simulatedBadgesActive,
                    onCheckedChange = onToggleSimulatedBadges,
                    modifier = Modifier.testTag("toggle_simulated_badges")
                )
            }

            if (!isNotificationAccessGranted) {
                OutlinedButton(
                    onClick = onOpenNotificationAccessSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant System Notification Listener Access")
                }
            }

            // 3. System AppWidgetHost Providers
            if (systemWidgets.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "System AppWidgets (${systemWidgets.size} available)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                systemWidgets.take(12).forEach { candidate ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAddSystemWidget(candidate)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.label,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "${candidate.packageName} · ${candidate.minSpanX}×${candidate.minSpanY}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onAddSystemWidget(candidate)
                                    onDismiss()
                                }
                            ) {
                                Text("Bind")
                            }
                        }
                    }
                }
            }
        }
    }
}
