package com.example.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class BuiltInPixelWidgetType(
    val key: String,
    val title: String,
    val subtitle: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int
) {
    PIXEL_CLOCK("builtin:pixel_clock", "Pixel Analog & Digital Clock", "Scalloped Material You timepiece", 2, 2),
    PIXEL_WEATHER("builtin:pixel_weather", "Pixel Weather Card", "Current temperature & high/low conditions", 2, 2),
    PIXEL_BATTERY("builtin:pixel_battery", "Device Battery & Memory", "Live battery level & system status", 3, 1)
}

data class SystemWidgetCandidate(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val packageName: String,
    val minSpanX: Int,
    val minSpanY: Int
) {
    val providerComponent: ComponentName
        get() = providerInfo.provider
}

/**
 * Manages [AppWidgetHost] (`HOST_ID = 1024`) and [AppWidgetManager] lifecycle,
 * widget ID allocation, binding, configuration intents, and host view creation.
 */
class WidgetHostController(private val context: Context) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(context.applicationContext, HOST_ID)

    fun startListening() {
        runCatching { appWidgetHost.startListening() }
    }

    fun stopListening() {
        runCatching { appWidgetHost.stopListening() }
    }

    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId > 0) {
            runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
        }
    }

    fun getInstalledSystemWidgets(): List<SystemWidgetCandidate> {
        return runCatching {
            val pm = context.packageManager
            appWidgetManager.installedProviders.map { info ->
                val label = info.loadLabel(pm)?.toString() ?: info.provider.shortClassName
                val spanX = ((info.minWidth + 70) / 74).coerceIn(1, 5)
                val spanY = ((info.minHeight + 70) / 74).coerceIn(1, 4)
                SystemWidgetCandidate(
                    providerInfo = info,
                    label = label,
                    packageName = info.provider.packageName,
                    minSpanX = spanX,
                    minSpanY = spanY
                )
            }.sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun tryBindWidgetSilently(appWidgetId: Int, provider: ComponentName): Boolean {
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        }.getOrDefault(false)
    }

    fun createBindPermissionIntent(appWidgetId: Int, provider: ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
    }

    fun getProviderInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()
    }

    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        return runCatching {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
            appWidgetHost.createView(context, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
            }
        }.getOrNull()
    }

    companion object {
        const val HOST_ID = 1024
    }
}

/**
 * Renders either a bound Android system [AppWidgetHostView] or one of the built-in Material You
 * Pixel widgets (`PIXEL_CLOCK`, `PIXEL_WEATHER`, `PIXEL_BATTERY`) on the workspace grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceWidgetTile(
    componentKey: String,
    appWidgetId: Int,
    widgetHostController: WidgetHostController,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val builtIn = BuiltInPixelWidgetType.entries.firstOrNull { it.key == componentKey }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (builtIn != null) {
            when (builtIn) {
                BuiltInPixelWidgetType.PIXEL_CLOCK -> BuiltInPixelClockWidget()
                BuiltInPixelWidgetType.PIXEL_WEATHER -> BuiltInPixelWeatherWidget()
                BuiltInPixelWidgetType.PIXEL_BATTERY -> BuiltInPixelBatteryWidget()
            }
        } else if (appWidgetId > 0) {
            val hostView = remember(appWidgetId) {
                widgetHostController.createHostView(appWidgetId)
            }
            if (hostView != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    AndroidView(
                        factory = { hostView },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            } else {
                BuiltInPixelClockWidget()
            }
        } else {
            BuiltInPixelClockWidget()
        }
    }
}

@Composable
private fun BuiltInPixelClockWidget() {
    val calendar = remember { Calendar.getInstance() }
    val timeText = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val dateText = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Canvas(modifier = Modifier.size(54.dp)) {
                val r = min(size.width, size.height) / 2f
                drawCircle(
                    color = primary.copy(alpha = 0.22f),
                    radius = r
                )
                drawCircle(
                    color = primary,
                    radius = r,
                    style = Stroke(width = 3.dp.toPx())
                )
                val hour = calendar.get(Calendar.HOUR)
                val minute = calendar.get(Calendar.MINUTE)
                val hourAngle = ((hour + minute / 60f) * 30f - 90f) * (PI / 180f)
                val minAngle = (minute * 6f - 90f) * (PI / 180f)

                drawLine(
                    color = onContainer,
                    start = center,
                    end = Offset(
                        center.x + (r * 0.5f * cos(hourAngle)).toFloat(),
                        center.y + (r * 0.5f * sin(hourAngle)).toFloat()
                    ),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tertiary,
                    start = center,
                    end = Offset(
                        center.x + (r * 0.72f * cos(minAngle)).toFloat(),
                        center.y + (r * 0.72f * sin(minAngle)).toFloat()
                    ),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = onContainer
                    )
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = onContainer.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
private fun BuiltInPixelWeatherWidget() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "72°",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = "Sunny",
                    tint = Color(0xFFFDD663),
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = "Mostly Sunny",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                Text(
                    text = "H: 76° · L: 58°",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                )
            }
        }
    }
}

@Composable
private fun BuiltInPixelBatteryWidget() {
    val context = LocalContext.current
    val batteryPct = remember {
        runCatching {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else 86
        }.getOrDefault(86)
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = "Battery",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pixel Device Battery",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$batteryPct%",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (batteryPct / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        }
    }
}
