package com.example.launcher.ui.components

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.example.R
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.drag.DragSessionState
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AppInfo
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.PageIndicatorStyle
import com.example.launcher.model.SearchBarStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pixel At-a-Glance top-left widget displaying the formatted date, upcoming calendar event,
 * and current weather condition.
 */
@Composable
fun AtAGlanceWidget(
    settings: LauncherSettings,
    onDateClick: () -> Unit,
    onWeatherClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLocale = remember(settings.appLanguage) {
        if (settings.appLanguage.languageTag.isBlank()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(settings.appLanguage.languageTag)
        }
    }

    val formattedDate = remember(settings.atAGlanceDateFormat, activeLocale) {
        runCatching {
            SimpleDateFormat(settings.atAGlanceDateFormat, activeLocale).format(Date())
        }.getOrElse {
            SimpleDateFormat("EEE, MMM d", activeLocale).format(Date())
        }
    }

    val formattedTemp = remember(settings.temperatureUnit) {
        settings.temperatureUnit.formatTemp(72)
    }

    val hourOfDay = remember {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }
    val contextualSubtitle = when (hourOfDay) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..16 -> stringResource(R.string.greeting_afternoon)
        in 17..21 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.50f),
            offset = Offset(0f, 1.5f),
            blurRadius = 4f
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("at_a_glance_widget")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onDateClick)
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    shadow = textShadow
                )
            )

            if (settings.atAGlanceShowWeather) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.20f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier.clickable(onClick = onWeatherClick)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = "Sunny weather",
                            tint = Color(0xFFFDD663),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formattedTemp,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDateClick)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = contextualSubtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * First-run prompt card to set PixelHome as the system's default Home app via RoleManager.ROLE_HOME.
 */
@Composable
fun DefaultHomeRoleBanner(
    onRequestRoleHome: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("default_home_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(SquircleShape(4f))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.set_default_launcher_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.set_default_launcher_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onRequestRoleHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("set_default_home_button")
                ) {
                    Text(
                        text = stringResource(R.string.set_default_action),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_default_home_button")
                ) {
                    Text(
                        text = stringResource(R.string.dismiss_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Clean, minimal App Search Bar under the dock with Material You tonal elevation.
 */
@Composable
fun PixelSearchBar(
    settings: LauncherSettings,
    onSearchBarClick: () -> Unit,
    onVoiceSearchClick: () -> Unit = {},
    onLensClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = remember(settings.searchBarStyle, settings.searchBarCornerRadiusDp) {
        when (settings.searchBarStyle) {
            SearchBarStyle.PILL -> RoundedCornerShape(settings.searchBarCornerRadiusDp.dp)
            SearchBarStyle.ROUNDED -> RoundedCornerShape(16.dp)
            SearchBarStyle.FLAT -> RoundedCornerShape(8.dp)
        }
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = settings.searchBarOpacity.coerceIn(0.35f, 1.0f)
        ),
        tonalElevation = 6.dp,
        shadowElevation = if (settings.searchBarStyle == SearchBarStyle.FLAT) 0.dp else 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .clickable(onClick = onSearchBarClick)
            .testTag("pixel_search_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.search_apps_hint),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Pixel Tablet & Foldable Floating Taskbar Dock:
 * Renders a glassmorphic floating pill bar with:
 * - Left: All-Apps Drawer launcher button
 * - Center: Pinned Hotseat Apps (with full drag-and-drop & long-press support)
 * - Divider + Right: Recent / Suggested running apps with live activity indicator dot
 */
@Composable
fun PixelTabletTaskbar(
    hotseatItems: List<HomeItemEntity>,
    suggestedApps: List<AppInfo>,
    appsByKey: Map<String, AppInfo>,
    iconCache: IconCache,
    dragSession: DragSessionState?,
    settings: LauncherSettings,
    drawerProgressProvider: () -> Float,
    onHotseatTopMeasured: (Float) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Long?, Rect) -> Unit,
    onStartDragFromHotseat: (AppInfo, Long, Int, Rect, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(exponent = settings.squircleExponent, preset = settings.iconShape)
    }
    val pinnedKeys = remember(hotseatItems) { hotseatItems.map { it.componentKey }.toSet() }
    val recentApps = remember(suggestedApps, pinnedKeys) {
        suggestedApps.filter { it.componentKey !in pinnedKeys }.take(2)
    }

    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = settings.dockOpacity.coerceIn(0.65f, 0.96f)
        ),
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .onPlaced { coords ->
                if (coords.isAttached) {
                    onHotseatTopMeasured(coords.boundsInWindow().top)
                }
            }
            .graphicsLayer {
                val p = drawerProgressProvider().coerceIn(0f, 1f)
                alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                translationY = p * 32.dp.toPx()
            }
            .testTag("pixel_tablet_taskbar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // All-Apps Drawer Trigger Button inside Taskbar
            IconButton(
                onClick = onOpenDrawerClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(squircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag("taskbar_all_apps_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = "Open All Apps Drawer",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(34.dp)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Pinned Hotseat Icons
            hotseatItems.forEachIndexed { slotIndex, item ->
                val app = appsByKey[item.componentKey]
                if (app != null) {
                    val isDraggedOrigin = dragSession?.sourceHomeItemId == item.id
                    val isSlotHovered = dragSession?.hoveredHotseatIndex == slotIndex
                    SquircleAppIcon(
                        app = app,
                        iconCache = iconCache,
                        settings = settings,
                        showLabel = false,
                        useThemedIcon = settings.themedIconsEnabled,
                        baseIconSize = 50.dp,
                        isBeingDragged = isDraggedOrigin,
                        isFolderMergeHighlighted = isSlotHovered,
                        onClick = onAppClick,
                        onLongClick = { clickedApp, rect ->
                            onAppLongClick(clickedApp, item.id, rect)
                        },
                        onDragStartFromIcon = { draggedApp, rect, winOffset ->
                            onStartDragFromHotseat(draggedApp, item.id, item.cellX, rect, winOffset)
                        },
                        onDragMoveWindowOffset = onDragMove,
                        onDragFinish = onDragEnd
                    )
                }
            }

            // Recent / Suggested Taskbar Apps
            if (recentApps.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier
                        .height(34.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                recentApps.forEach { recentApp ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SquircleAppIcon(
                            app = recentApp,
                            iconCache = iconCache,
                            settings = settings,
                            showLabel = false,
                            useThemedIcon = settings.themedIconsEnabled,
                            baseIconSize = 48.dp,
                            onClick = onAppClick,
                            onLongClick = { clickedApp, rect ->
                                onAppLongClick(clickedApp, null, rect)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page dots indicator above the hotseat.
 */
@Composable
fun WorkspacePageIndicator(
    pageCount: Int,
    currentPage: Int,
    style: PageIndicatorStyle,
    modifier: Modifier = Modifier
) {
    if (style == PageIndicatorStyle.NONE || pageCount <= 1) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 6.dp)
    ) {
        for (i in 0 until pageCount) {
            val isSelected = i == currentPage
            val width by animateDpAsState(
                targetValue = when {
                    style == PageIndicatorStyle.LINE && isSelected -> 24.dp
                    style == PageIndicatorStyle.LINE -> 10.dp
                    isSelected -> 16.dp
                    else -> 7.dp
                },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                label = "PageDotWidth"
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White
                        else Color.White.copy(alpha = 0.38f)
                    )
            )
        }
    }
}

/**
 * Pixel M3 Workspace Long-Press Menu ("Wallpaper & style", "Widgets", "Home settings").
 * Pops up smoothly right from the user's touch location with silky spring physics
 * and polished Google Pixel M3 visual styling.
 */
@Composable
fun WorkspaceLongPressMenuPopup(
    visible: Boolean,
    anchorOffset: Offset? = null,
    onDismiss: () -> Unit,
    onWallpaperAndStyleClick: () -> Unit,
    onWidgetsClick: () -> Unit,
    onHomeSettingsClick: () -> Unit
) {
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val popupWidthDp = (configuration.screenWidthDp - 32).coerceIn(220, 268).dp
    val popupWidthPx = with(density) { popupWidthDp.toPx() }
    val popupHeightPx = with(density) { 224.dp.toPx() }

    val popupX = if (anchorOffset != null) {
        (anchorOffset.x - popupWidthPx / 2f)
            .coerceIn(16f, (screenWidthPx - popupWidthPx - 16f).coerceAtLeast(16f))
            .roundToInt()
    } else {
        ((screenWidthPx - popupWidthPx) / 2f).roundToInt()
    }

    val showAbove = anchorOffset != null && anchorOffset.y > (screenHeightPx - popupHeightPx - 70f)
    val popupY = if (anchorOffset != null) {
        if (showAbove) {
            (anchorOffset.y - popupHeightPx - 12f).coerceAtLeast(40f).roundToInt()
        } else {
            (anchorOffset.y + 12f).coerceAtMost(screenHeightPx - popupHeightPx - 40f).roundToInt()
        }
    } else {
        ((screenHeightPx - popupHeightPx) / 2f).roundToInt()
    }

    val pivotX = if (anchorOffset != null) {
        ((anchorOffset.x - popupX) / popupWidthPx).coerceIn(0.12f, 0.88f)
    } else 0.5f

    val pivotY = if (showAbove) 1.0f else 0.0f

    val menuEntrance = remember { Animatable(0f) }
    LaunchedEffect(anchorOffset) {
        menuEntrance.snapTo(0f)
        menuEntrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = 380f
            )
        )
    }

    Popup(
        offset = IntOffset(popupX, popupY),
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
            tonalElevation = 10.dp,
            shadowElevation = 20.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .width(popupWidthDp)
                .graphicsLayer {
                    val p = menuEntrance.value
                    val scale = 0.62f + 0.38f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = (p * 1.35f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                    translationY = (1f - p) * (if (showAbove) 20.dp.toPx() else -20.dp.toPx())
                }
                .testTag("workspace_long_press_menu")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                WorkspaceMenuActionTile(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.wallpaper_and_style),
                    subtitle = "Colors, squircle shape & grid",
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    entranceProgressProvider = { menuEntrance.value },
                    staggerIndex = 0,
                    onClick = {
                        onDismiss()
                        onWallpaperAndStyleClick()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                WorkspaceMenuActionTile(
                    icon = Icons.Filled.Widgets,
                    title = stringResource(R.string.widgets_menu),
                    subtitle = "At-a-Glance, clocks & shortcuts",
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    entranceProgressProvider = { menuEntrance.value },
                    staggerIndex = 1,
                    onClick = {
                        onDismiss()
                        onWidgetsClick()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                WorkspaceMenuActionTile(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.home_settings),
                    subtitle = "Grid, gestures, dock & physics",
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    entranceProgressProvider = { menuEntrance.value },
                    staggerIndex = 2,
                    onClick = {
                        onDismiss()
                        onHomeSettingsClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceMenuActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconContainerColor: Color,
    iconColor: Color,
    entranceProgressProvider: () -> Float = { 1f },
    staggerIndex: Int = 0,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val raw = entranceProgressProvider()
                val delayed = ((raw - staggerIndex * 0.12f) / (1f - staggerIndex * 0.12f)).coerceIn(0f, 1.15f)
                alpha = delayed.coerceIn(0f, 1f)
                translationX = (1f - delayed) * (-16.dp.toPx())
                val itemScale = 0.88f + 0.12f * delayed
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(SquircleShape(5.4f))
                    .background(iconContainerColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Anchored Long-Press App Shortcut Popup that scales in directly from the tapped icon's
 * screen coordinates (`anchorRect`), showing active notifications, shortcuts (`LauncherApps.getShortcuts`),
 * App Info, Remove, and Uninstall.
 */
@Composable
fun AppShortcutPopupDialog(
    app: AppInfo?,
    anchorRect: Rect?,
    onDismiss: () -> Unit,
    onShortcutClick: (AppInfo, String) -> Unit,
    onClearBadgeClick: (String) -> Unit,
    onAppInfoClick: (AppInfo) -> Unit,
    onAddToHomeClick: ((AppInfo) -> Unit)? = null,
    onRemoveFromHomeClick: (() -> Unit)? = null,
    onUninstallClick: (AppInfo) -> Unit
) {
    if (app == null) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val popupWidthDp = (configuration.screenWidthDp - 32).coerceIn(224, 276).dp
    val popupWidthPx = with(density) { popupWidthDp.toPx() }
    val estimatedPopupHeightPx = with(density) { 330.dp.toPx() }

    val rect = anchorRect ?: Rect(
        (screenWidthPx / 2f - 60f).roundToInt(),
        (screenHeightPx / 2f - 60f).roundToInt(),
        (screenWidthPx / 2f + 60f).roundToInt(),
        (screenHeightPx / 2f + 60f).roundToInt()
    )

    val showAboveIcon = rect.top > estimatedPopupHeightPx + 40f
    val popupX = (rect.centerX() - popupWidthPx / 2f)
        .coerceIn(16f, (screenWidthPx - popupWidthPx - 16f).coerceAtLeast(16f))
        .roundToInt()
    val popupY = if (showAboveIcon) {
        (rect.top - estimatedPopupHeightPx).coerceAtLeast(48f).roundToInt()
    } else {
        (rect.bottom + 12f).coerceAtMost(screenHeightPx - estimatedPopupHeightPx - 24f).roundToInt()
    }

    val pivotFractionX = ((rect.centerX() - popupX) / popupWidthPx).coerceIn(0.15f, 0.85f)
    val pivotFractionY = if (showAboveIcon) 1.0f else 0.0f

    val popupEntrance = remember { Animatable(0f) }
    LaunchedEffect(app.componentKey, anchorRect) {
        popupEntrance.snapTo(0f)
        popupEntrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)
        )
    }

    Popup(
        offset = IntOffset(popupX, popupY),
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .width(popupWidthDp)
                .graphicsLayer {
                    val p = popupEntrance.value
                    val scale = 0.65f + 0.35f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = (p * 1.35f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
                }
                .testTag("app_shortcut_popup")
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Hold & drag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Active Notification Banner inside Popup
                if (app.notificationCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${app.notificationCount} active notification${if (app.notificationCount > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            IconButton(
                                onClick = { onClearBadgeClick(app.packageName) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss notification badge",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                app.shortcuts.forEach { shortcut ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onShortcutClick(app, shortcut.id)
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = shortcut.shortLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                if (onAddToHomeClick != null) {
                    PopupMenuItemRow(
                        icon = Icons.Filled.Home,
                        title = "Add to Home screen",
                        onClick = {
                            onDismiss()
                            onAddToHomeClick(app)
                        }
                    )
                }

                if (onRemoveFromHomeClick != null) {
                    PopupMenuItemRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Remove from Home",
                        onClick = {
                            onDismiss()
                            onRemoveFromHomeClick()
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    if (onRemoveFromHomeClick != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onRemoveFromHomeClick()
                            },
                            modifier = Modifier.testTag("popup_quick_remove_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }

                    TextButton(
                        onClick = {
                            onDismiss()
                            onAppInfoClick(app)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "App info",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("App info")
                    }

                    TextButton(
                        onClick = {
                            onDismiss()
                            onUninstallClick(app)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Uninstall",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupMenuItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Dialog for resizing or removing a widget placed on the home grid.
 */
@Composable
fun WidgetResizeAndRemoveDialog(
    widgetItem: HomeItemEntity?,
    onResize: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    if (widgetItem == null) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = widgetItem.title.ifBlank { "Widget Options" },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select widget grid span (Columns × Rows):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val sizes = listOf(2 to 1, 2 to 2, 3 to 1, 4 to 2, 5 to 2)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    sizes.forEach { (sx, sy) ->
                        FilterChip(
                            selected = widgetItem.spanX == sx && widgetItem.spanY == sy,
                            onClick = {
                                onResize(sx, sy)
                                onDismiss()
                            },
                            label = { Text("${sx}×$sy") }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            onRemove()
                            onDismiss()
                        }
                    ) {
                        Text("Remove Widget", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
