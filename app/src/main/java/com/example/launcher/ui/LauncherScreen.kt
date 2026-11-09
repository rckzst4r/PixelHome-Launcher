package com.example.launcher.ui

import android.content.res.Configuration
import android.graphics.Rect
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.launcher.animation.AppWindowMorphState
import com.example.launcher.animation.LauncherHaptics
import com.example.launcher.animation.LauncherPhysics
import com.example.launcher.data.db.HomeCellItemType
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.drag.DragSessionState
import com.example.launcher.drag.FloatingDraggedIconOverlay
import com.example.launcher.drag.TopDragDropTargetBar
import com.example.launcher.drag.WorkspaceHoverMode
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AppInfo
import com.example.launcher.model.DockBackgroundStyle
import com.example.launcher.model.LauncherGestureAction
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.SearchBarPosition
import com.example.launcher.service.PixelLockAccessibilityService
import com.example.launcher.service.PixelNotificationListenerService
import com.example.launcher.ui.components.AppDrawerSheet
import com.example.launcher.ui.components.AppShortcutPopupDialog
import com.example.launcher.ui.components.AppWindowMorphOverlay
import com.example.launcher.ui.components.AtAGlanceWidget
import com.example.launcher.ui.components.DefaultHomeRoleBanner
import com.example.launcher.ui.components.PixelSearchBar
import com.example.launcher.ui.components.PixelTabletTaskbar
import com.example.launcher.ui.components.PixelWelcomeScreenDialog
import com.example.launcher.ui.components.QuickStyleAndSettingsSheet
import com.example.launcher.ui.components.SimulatedScreenLockOverlay
import com.example.launcher.ui.components.SquircleAppIcon
import com.example.launcher.ui.components.SquircleFolderTile
import com.example.launcher.ui.components.WidgetAndBadgePickerSheet
import com.example.launcher.ui.components.WidgetResizeAndRemoveDialog
import com.example.launcher.ui.components.WorkspaceLongPressMenuPopup
import com.example.launcher.ui.components.WorkspacePageIndicator
import com.example.launcher.viewmodel.LauncherUiState
import com.example.launcher.widget.BuiltInPixelWidgetType
import com.example.launcher.widget.SystemWidgetCandidate
import com.example.launcher.widget.WidgetHostController
import com.example.launcher.widget.WorkspaceWidgetTile
import com.example.ui.theme.parseHexColorSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Plain non-observable holder for workspace & hotseat bounds so `.onGloballyPositioned`
 * never triggers Compose Snapshot writes or recompositions when paging horizontally.
 */
private class WorkspaceLayoutMetrics(
    var gridTopPx: Float = 240f,
    var gridBottomPx: Float = 1400f,
    var hotseatTopPx: Float = 1500f
)

private class GridCoordsHolder(var coords: LayoutCoordinates? = null) {
    fun resolveWindowPosition(): Offset {
        val c = coords
        if (c == null || !c.isAttached) return Offset.Zero
        return runCatching { c.positionInWindow() }.getOrDefault(Offset.Zero)
    }
}

/**
 * Primary single-activity Pixel Home Screen Composable:
 * - Adaptive Phone + Tablet/Foldable Dual-Pane Workspace & Floating Taskbar Dock.
 * - Global & Per-App Color Studio with 1-tap palette switcher and per-icon long-press color customizer.
 * - Universal Android 7.0–16+ (API 24–36) compatibility.
 * - Full Drag & Drop engine with lifted squircle icon, spring neighbor reflow, folder merge ring,
 *   and sliding Top Drop Target Bar (`Remove`, `App info`, `Uninstall`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    uiState: LauncherUiState,
    iconCache: IconCache,
    widgetHostController: WidgetHostController,
    dragSession: DragSessionState?,
    activeMorphState: AppWindowMorphState?,
    isScreenLockOverlayVisible: Boolean,
    requestDrawerSearchFocus: Boolean,
    homePressEvents: SharedFlow<Unit>,
    onRequestRoleHome: () -> Unit,
    onDismissRoleBanner: () -> Unit,
    onExpandNotifications: () -> Unit,
    onExpandQuickSettings: () -> Unit,
    onLockScreenAction: () -> Unit,
    onWakeScreenLockOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenFavoriteAppGesture: () -> Unit,
    onConsumeDrawerSearchFocus: () -> Unit,
    onMorphAnimationFinished: () -> Unit,
    onWindowBlurProgressChanged: (Float) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Long?, Rect) -> Unit,
    onStartDragSession: (AppInfo, Long?, Int, Int, Int, Rect, Offset) -> Unit,
    onUpdateDragPosition: (Offset, Int, Float, Float, Float, Float, Float) -> Boolean,
    onFinishDragSession: (Int, Boolean) -> Unit,
    onDismissAppPopup: () -> Unit,
    onClearBadgeForPackage: (String) -> Unit,
    onFolderClick: (HomeItemEntity) -> Unit,
    onCloseFolder: () -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onToggleAppInFolder: (HomeItemEntity, String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onWebSearchClick: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onWorkspaceLongPress: () -> Unit,
    onDismissWorkspaceMenu: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onDismissWidgetPicker: () -> Unit,
    onAddBuiltInWidget: (BuiltInPixelWidgetType, Int) -> Unit,
    onAddSystemWidget: (SystemWidgetCandidate, Int) -> Unit,
    onWidgetLongPress: (HomeItemEntity) -> Unit,
    onDismissWidgetResize: () -> Unit,
    onResizeWidget: (Long, Int, Int) -> Unit,
    onToggleSimulatedBadges: (Boolean) -> Unit,
    onOpenQuickSettings: () -> Unit,
    onDismissQuickSettings: () -> Unit,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onResetSettingsAndLayout: () -> Unit,
    onExportBackupJson: ((String) -> Unit) -> Unit,
    onImportBackupJson: (String) -> Unit,
    onExportBackupToFileSaf: () -> Unit,
    onImportBackupFromFileSaf: () -> Unit,
    onAddAppToHome: (AppInfo, Int) -> Unit,
    onRemoveHomeItem: (Long) -> Unit,
    onAppInfoClick: (AppInfo) -> Unit,
    onUninstallAppClick: (AppInfo) -> Unit,
    onTriggerSearchGestureFocus: () -> Unit
) {
    val settings = uiState.settings
    val context = LocalContext.current
    val rootView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val pageCount = max(settings.homePageCount, 2)
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect {
                LauncherHaptics.performSnapOrTick(rootView, settings)
            }
    }

    val drawerProgress = remember { Animatable(0f) }
    val workspaceWaveProgress = remember { Animatable(1f) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    val layoutMetrics = remember { WorkspaceLayoutMetrics() }

    var workspaceMenuAnchorOffset by remember { mutableStateOf<Offset?>(null) }
    var showWelcomeScreen by remember { mutableStateOf(!settings.hasSeenWelcome) }

    fun triggerWorkspaceWaveBounce() {
        coroutineScope.launch {
            workspaceWaveProgress.snapTo(0.15f)
            workspaceWaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = (settings.springDampingRatio * 0.72f).coerceIn(0.48f, 0.85f),
                    stiffness = (settings.springStiffness * 0.85f).coerceIn(180f, 900f)
                )
            )
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            workspaceWaveProgress.snapTo(0f)
            workspaceWaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.64f,
                    stiffness = 280f
                )
            )
        }
    }

    fun animateDrawerTo(target: Float, initialVelocity: Float = 0f, focusSearch: Boolean = false) {
        coroutineScope.launch {
            if (target == 1f && (focusSearch || settings.autoOpenDrawerKeyboard)) {
                onTriggerSearchGestureFocus()
            }
            if (target == 0f && drawerProgress.value > 0.35f) {
                triggerWorkspaceWaveBounce()
            }
            drawerProgress.animateTo(
                targetValue = target.coerceIn(0f, 1f),
                initialVelocity = initialVelocity,
                animationSpec = LauncherPhysics.springSpec(
                    settings = settings,
                    context = context,
                    stiffnessScale = 1.35f
                )
            )
            if (target == 0f) {
                onSearchQueryChange("")
            }
        }
    }

    fun executeGestureAction(action: LauncherGestureAction, velocityPxPerSec: Float = 0f, screenHeightPx: Float = 2000f) {
        LauncherHaptics.performGestureConfirm(rootView, settings)
        when (action) {
            LauncherGestureAction.DRAWER -> {
                animateDrawerTo(1f, initialVelocity = -velocityPxPerSec / screenHeightPx)
            }
            LauncherGestureAction.SEARCH -> {
                animateDrawerTo(1f, initialVelocity = -velocityPxPerSec / screenHeightPx, focusSearch = true)
            }
            LauncherGestureAction.NOTIFICATIONS -> {
                onExpandNotifications()
            }
            LauncherGestureAction.QUICK_SETTINGS -> {
                onExpandQuickSettings()
            }
            LauncherGestureAction.LOCK_SCREEN -> {
                onLockScreenAction()
            }
            LauncherGestureAction.OPEN_APP -> {
                onOpenFavoriteAppGesture()
            }
            LauncherGestureAction.NOTHING -> Unit
        }
    }

    // Only toggle hardware window blur when drawer settles open/closed to avoid mid-swipe SurfaceFlinger stalls
    LaunchedEffect(drawerProgress) {
        snapshotFlow { drawerProgress.value > 0.85f }
            .distinctUntilChanged()
            .collect { settledOpen ->
                onWindowBlurProgressChanged(if (settledOpen) 1f else 0f)
            }
    }

    LaunchedEffect(homePressEvents) {
        homePressEvents.collect {
            val spec = LauncherPhysics.springSpec<Float>(settings, context)
            triggerWorkspaceWaveBounce()
            launch { drawerProgress.animateTo(0f, spec) }
            launch { pagerState.animateScrollToPage(0, animationSpec = spec) }
        }
    }

    val isDrawerOpen by remember { derivedStateOf { drawerProgress.value > 0.05f } }
    val isDrawerVisible by remember { derivedStateOf { drawerProgress.value > 0.005f } }
    val hasActiveBackTarget = isDrawerOpen ||
        uiState.activeFolderItem != null ||
        uiState.activePopupApp != null ||
        uiState.isWorkspaceMenuVisible ||
        pagerState.currentPage > 0

    PredictiveBackHandler(enabled = hasActiveBackTarget) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }
            predictiveBackProgress = 0f
            LauncherHaptics.performSnapOrTick(rootView, settings)
            when {
                uiState.activePopupApp != null -> onDismissAppPopup()
                uiState.isWorkspaceMenuVisible -> onDismissWorkspaceMenu()
                uiState.activeFolderItem != null -> onCloseFolder()
                isDrawerOpen -> animateDrawerTo(0f)
                pagerState.currentPage > 0 -> {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = 0,
                            animationSpec = LauncherPhysics.springSpec(settings, context)
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            predictiveBackProgress = 0f
            throw e
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launcher_root_container")
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val screenHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val topDropZoneBottomPx = with(density) { (statusBarTop + 92.dp).toPx() }

        // Automatic Device Form-Factor Detection (Tablet / Unfolded Foldable vs Phone)
        val configuration = LocalConfiguration.current
        val isLargeScreenConfig =
            (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isTabletModeActive = configuration.smallestScreenWidthDp >= 600 ||
            isLargeScreenConfig ||
            maxWidth >= 600.dp

        val workspaceMaxWidth = if (isTabletModeActive) 1280.dp else 640.dp

        val parsedAccentColor = remember(settings.accentColorHex) {
            parseHexColorSafe(settings.accentColorHex)
        }

        // 1. Multi-layer Wallpaper Parallax + Full-Bleed Scrim Layer with cached GPU Shaders (zero per-frame allocations)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val verticalScrimBrush = Brush.verticalGradient(
                        0.0f to Color(0x550B111E),
                        0.35f to Color(0x180B111E),
                        0.75f to Color(0x280B111E),
                        1.0f to Color(0x66080D18)
                    )
                    val baseGlowCenter = Offset(size.width * 0.82f, size.height * 0.18f)
                    val glowRadius = (size.width * 0.65f).coerceAtLeast(1f)
                    val radialGlowBrush = Brush.radialGradient(
                        colors = listOf(
                            parsedAccentColor.copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = baseGlowCenter,
                        radius = glowRadius
                    )

                    onDrawBehind {
                        drawRect(brush = verticalScrimBrush)
                        val parallaxDx = if (settings.wallpaperParallax) {
                            val pageFraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction) /
                                pageCount.toFloat().coerceAtLeast(1f)
                            -pageFraction * 44.dp.toPx()
                        } else {
                            0f
                        }
                        withTransform({ translate(left = parallaxDx, top = 0f) }) {
                            drawCircle(
                                brush = radialGlowBrush,
                                center = baseGlowCenter,
                                radius = glowRadius
                            )
                        }
                        val drawerDim = (drawerProgress.value * 0.52f).coerceIn(0f, 0.58f)
                        if (drawerDim > 0.005f) {
                            drawRect(color = Color.Black.copy(alpha = drawerDim))
                        }
                    }
                }
        )

        // 2. Home Workspace Layer (Phone Single-Pane or Tablet Dual-Pane Spread)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = workspaceMaxWidth)
                .align(Alignment.Center)
                .padding(
                    top = statusBarTop + 4.dp,
                    bottom = navBarBottom + 8.dp
                )
                .graphicsLayer {
                    val p = drawerProgress.value.coerceIn(0f, 1f)
                    alpha = (1f - p * 1.45f).coerceIn(0f, 1f)
                    val s = 1f - (0.05f * p)
                    scaleX = s
                    scaleY = s
                    translationY = -p * 36.dp.toPx()
                }
                .pointerInput(settings.swipeUpAction, settings.swipeDownAction, dragSession != null) {
                    if (dragSession != null) return@pointerInput
                    val velocityTracker = VelocityTracker()
                    var totalDy = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            totalDy = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            totalDy += dragAmount
                            if (settings.swipeUpAction == LauncherGestureAction.DRAWER ||
                                settings.swipeUpAction == LauncherGestureAction.SEARCH
                            ) {
                                val deltaProgress = -dragAmount / (screenHeightPx * 0.44f)
                                val next = (drawerProgress.value + deltaProgress).coerceIn(0f, 1f)
                                if (next > 0f || drawerProgress.value > 0f) {
                                    change.consume()
                                    coroutineScope.launch {
                                        drawerProgress.snapTo(next)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val vy = velocityTracker.calculateVelocity().y
                            if (drawerProgress.value > 0.01f) {
                                val open = vy < -420f || (drawerProgress.value > 0.24f && vy < 420f)
                                if (open) {
                                    LauncherHaptics.performSnapOrTick(rootView, settings)
                                }
                                animateDrawerTo(
                                    target = if (open) 1f else 0f,
                                    initialVelocity = -vy / (screenHeightPx * 0.44f),
                                    focusSearch = open && settings.swipeUpAction == LauncherGestureAction.SEARCH
                                )
                            } else if (totalDy < -70f || vy < -650f) {
                                executeGestureAction(settings.swipeUpAction, vy, screenHeightPx)
                            } else if (totalDy > 75f || vy > 750f) {
                                executeGestureAction(settings.swipeDownAction, vy, screenHeightPx)
                            }
                        },
                        onDragCancel = {
                            animateDrawerTo(if (drawerProgress.value > 0.4f) 1f else 0f)
                        }
                    )
                }
        ) {
            // Top Drop Target Bar (`Remove`, `App info`, `Uninstall`) slides in during drag
            TopDragDropTargetBar(
                visible = dragSession != null,
                hoveredTarget = dragSession?.hoveredTopTarget ?: com.example.launcher.drag.TopDropTarget.NONE,
                showRemoveTarget = dragSession?.sourceHomeItemId != null,
                settings = settings
            )

            // First-run banner to set PixelHome as default home app (RoleManager ROLE_HOME)
            if (dragSession == null && !uiState.isDefaultHomeApp && !settings.defaultHomePromptDismissed) {
                DefaultHomeRoleBanner(
                    onRequestRoleHome = onRequestRoleHome,
                    onDismiss = onDismissRoleBanner
                )
            }

            if (settings.searchBarPosition == SearchBarPosition.TOP) {
                PixelSearchBar(
                    settings = settings,
                    onSearchBarClick = { animateDrawerTo(1f, focusSearch = true) },
                    onVoiceSearchClick = onVoiceSearchClick,
                    onLensClick = { onWebSearchClick("Google Lens") },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }

            // Main Workspace Area: Tablet Dual-Pane Spread vs Phone Single-Pane Pager
            if (isTabletModeActive && settings.tabletDualPaneWorkspace) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("tablet_dual_pane_workspace")
                ) {
                    // Left Tablet Pane: At-a-Glance + Live Built-In Widgets
                    TabletDashboardSidePane(
                        settings = settings,
                        widgetHostController = widgetHostController,
                        onCalendarClick = onCalendarClick,
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                    )

                    // Right Tablet Pane: Multi-Page App Grid Pager
                    HorizontalPager(
                        state = pagerState,
                        key = { it },
                        userScrollEnabled = dragSession == null,
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                            .testTag("workspace_horizontal_pager")
                    ) { pageIndex ->
                        val pageItems = uiState.workspaceItemsByPage[pageIndex].orEmpty()
                        WorkspacePageGrid(
                            pageIndex = pageIndex,
                            isTabletSideBySide = true,
                            pageOffsetProvider = {
                                (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                            },
                            waveProgressProvider = { workspaceWaveProgress.value },
                            items = pageItems,
                            appsByKey = uiState.appsByKey,
                            iconCache = iconCache,
                            widgetHostController = widgetHostController,
                            dragSession = if (pageIndex == pagerState.currentPage) dragSession else null,
                            settings = settings,
                            onGridBoundsMeasured = { topPx, bottomPx ->
                                if (pageIndex == pagerState.currentPage) {
                                    layoutMetrics.gridTopPx = topPx
                                    layoutMetrics.gridBottomPx = bottomPx
                                }
                            },
                            onAppClick = onAppClick,
                            onAppLongClick = { clickedApp, itemId, rect ->
                                LauncherHaptics.performLongPress(rootView, settings)
                                onAppLongClick(clickedApp, itemId, rect)
                            },
                            onStartDragFromCell = { app, itemId, cx, cy, rect, winOffset ->
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                                onStartDragSession(app, itemId, pageIndex, cx, cy, rect, winOffset)
                            },
                            onDragMove = { winOffset ->
                                val changed = onUpdateDragPosition(
                                    winOffset,
                                    pagerState.currentPage,
                                    screenWidthPx,
                                    topDropZoneBottomPx,
                                    layoutMetrics.gridTopPx,
                                    layoutMetrics.gridBottomPx,
                                    layoutMetrics.hotseatTopPx
                                )
                                if (changed) {
                                    LauncherHaptics.performSnapOrTick(rootView, settings)
                                }
                            },
                            onDragEnd = { cancelled ->
                                LauncherHaptics.performGestureConfirm(rootView, settings)
                                onFinishDragSession(pagerState.currentPage, cancelled)
                            },
                            onFolderClick = onFolderClick,
                            onWidgetLongPress = { widgetItem ->
                                LauncherHaptics.performLongPress(rootView, settings)
                                onWidgetLongPress(widgetItem)
                            },
                            onEmptySpaceLongPress = { touchOffset ->
                                workspaceMenuAnchorOffset = touchOffset
                                LauncherHaptics.performLongPress(rootView, settings)
                                onWorkspaceLongPress()
                            },
                            onEmptySpaceDoubleTap = {
                                executeGestureAction(settings.doubleTapAction)
                            },
                            onCalendarClick = onCalendarClick
                        )
                    }
                }
            } else {
                // Standard Single-Pane Multi-Page Home Grid Pager
                HorizontalPager(
                    state = pagerState,
                    key = { it },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = dragSession == null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("workspace_horizontal_pager")
                ) { pageIndex ->
                    val pageItems = uiState.workspaceItemsByPage[pageIndex].orEmpty()
                    WorkspacePageGrid(
                        pageIndex = pageIndex,
                        isTabletSideBySide = false,
                        pageOffsetProvider = {
                            (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        },
                        waveProgressProvider = { workspaceWaveProgress.value },
                        items = pageItems,
                        appsByKey = uiState.appsByKey,
                        iconCache = iconCache,
                        widgetHostController = widgetHostController,
                        dragSession = if (pageIndex == pagerState.currentPage) dragSession else null,
                        settings = settings,
                        onGridBoundsMeasured = { topPx, bottomPx ->
                            if (pageIndex == pagerState.currentPage) {
                                layoutMetrics.gridTopPx = topPx
                                layoutMetrics.gridBottomPx = bottomPx
                            }
                        },
                        onAppClick = onAppClick,
                        onAppLongClick = { clickedApp, itemId, rect ->
                            LauncherHaptics.performLongPress(rootView, settings)
                            onAppLongClick(clickedApp, itemId, rect)
                        },
                        onStartDragFromCell = { app, itemId, cx, cy, rect, winOffset ->
                            LauncherHaptics.performSnapOrTick(rootView, settings)
                            onStartDragSession(app, itemId, pageIndex, cx, cy, rect, winOffset)
                        },
                        onDragMove = { winOffset ->
                            val changed = onUpdateDragPosition(
                                winOffset,
                                pagerState.currentPage,
                                screenWidthPx,
                                topDropZoneBottomPx,
                                layoutMetrics.gridTopPx,
                                layoutMetrics.gridBottomPx,
                                layoutMetrics.hotseatTopPx
                            )
                            if (changed) {
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                            }
                        },
                        onDragEnd = { cancelled ->
                            LauncherHaptics.performGestureConfirm(rootView, settings)
                            onFinishDragSession(pagerState.currentPage, cancelled)
                        },
                        onFolderClick = onFolderClick,
                        onWidgetLongPress = { widgetItem ->
                            LauncherHaptics.performLongPress(rootView, settings)
                            onWidgetLongPress(widgetItem)
                        },
                        onEmptySpaceLongPress = { touchOffset ->
                            workspaceMenuAnchorOffset = touchOffset
                            LauncherHaptics.performLongPress(rootView, settings)
                            onWorkspaceLongPress()
                        },
                        onEmptySpaceDoubleTap = {
                            executeGestureAction(settings.doubleTapAction)
                        },
                        onCalendarClick = onCalendarClick
                    )
                }
            }

            // Swipe-up chevron hint + Page Indicator Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = drawerProgress.value.coerceIn(0f, 1f)
                        alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                    }
                    .clickable(enabled = settings.infiniteScroll && pageCount > 1) {
                        coroutineScope.launch {
                            val nextPage = (pagerState.currentPage + 1) % pageCount
                            pagerState.animateScrollToPage(
                                page = nextPage,
                                animationSpec = LauncherPhysics.springSpec(settings, context)
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Swipe up to open app drawer",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.combinedClickable(
                        onClick = { animateDrawerTo(1f) }
                    )
                )
                WorkspacePageIndicator(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    style = settings.pageIndicatorStyle
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Dock / Taskbar: Pixel Tablet Taskbar when Tablet Mode is active, else Phone Hotseat Dock
            if (settings.showDockOnAllPages || pagerState.currentPage == 0) {
                if (isTabletModeActive && settings.tabletTaskbarStyle) {
                    PixelTabletTaskbar(
                        hotseatItems = uiState.hotseatItems,
                        suggestedApps = uiState.suggestedApps,
                        appsByKey = uiState.appsByKey,
                        iconCache = iconCache,
                        dragSession = dragSession,
                        settings = settings,
                        drawerProgressProvider = { drawerProgress.value },
                        onHotseatTopMeasured = { topY -> layoutMetrics.hotseatTopPx = topY },
                        onOpenDrawerClick = { animateDrawerTo(1f) },
                        onAppClick = onAppClick,
                        onAppLongClick = { clickedApp, itemId, rect ->
                            LauncherHaptics.performLongPress(rootView, settings)
                            onAppLongClick(clickedApp, itemId, rect)
                        },
                        onStartDragFromHotseat = { app, itemId, dockSlot, rect, winOffset ->
                            LauncherHaptics.performSnapOrTick(rootView, settings)
                            onStartDragSession(app, itemId, pagerState.currentPage, dockSlot, -1, rect, winOffset)
                        },
                        onDragMove = { winOffset ->
                            val changed = onUpdateDragPosition(
                                winOffset,
                                pagerState.currentPage,
                                screenWidthPx,
                                topDropZoneBottomPx,
                                layoutMetrics.gridTopPx,
                                layoutMetrics.gridBottomPx,
                                layoutMetrics.hotseatTopPx
                            )
                            if (changed) {
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                            }
                        },
                        onDragEnd = { cancelled ->
                            LauncherHaptics.performGestureConfirm(rootView, settings)
                            onFinishDragSession(pagerState.currentPage, cancelled)
                        }
                    )
                } else {
                    HotseatDockRow(
                        hotseatItems = uiState.hotseatItems,
                        appsByKey = uiState.appsByKey,
                        iconCache = iconCache,
                        dragSession = dragSession,
                        settings = settings,
                        drawerProgressProvider = { drawerProgress.value },
                        waveProgressProvider = { workspaceWaveProgress.value },
                        onHotseatTopMeasured = { topY -> layoutMetrics.hotseatTopPx = topY },
                        onAppClick = onAppClick,
                        onAppLongClick = { clickedApp, itemId, rect ->
                            LauncherHaptics.performLongPress(rootView, settings)
                            onAppLongClick(clickedApp, itemId, rect)
                        },
                        onStartDragFromHotseat = { app, itemId, dockSlot, rect, winOffset ->
                            LauncherHaptics.performSnapOrTick(rootView, settings)
                            onStartDragSession(app, itemId, pagerState.currentPage, dockSlot, -1, rect, winOffset)
                        },
                        onDragMove = { winOffset ->
                            val changed = onUpdateDragPosition(
                                winOffset,
                                pagerState.currentPage,
                                screenWidthPx,
                                topDropZoneBottomPx,
                                layoutMetrics.gridTopPx,
                                layoutMetrics.gridBottomPx,
                                layoutMetrics.hotseatTopPx
                            )
                            if (changed) {
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                            }
                        },
                        onDragEnd = { cancelled ->
                            LauncherHaptics.performGestureConfirm(rootView, settings)
                            onFinishDragSession(pagerState.currentPage, cancelled)
                        }
                    )
                }
            }

            // Signature Pixel Pill Search Bar under the Hotseat
            if (settings.searchBarPosition == SearchBarPosition.BOTTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                PixelSearchBar(
                    settings = settings,
                    onSearchBarClick = { animateDrawerTo(1f, focusSearch = true) },
                    onVoiceSearchClick = onVoiceSearchClick,
                    onLensClick = { onWebSearchClick("Google Lens") },
                    modifier = Modifier
                        .widthIn(max = if (isTabletModeActive) 620.dp else 640.dp)
                        .padding(horizontal = 18.dp)
                        .graphicsLayer {
                            val p = drawerProgress.value.coerceIn(0f, 1f)
                            translationY = -p * (screenHeightPx * 0.72f)
                            alpha = (1f - p * 1.4f).coerceIn(0f, 1f)
                        }
                )
            }
        }

        // 3. App Drawer Layer — Kept pre-composed in tree so swipe-up starts on frame 1 with zero cold-layout lag
        AppDrawerSheet(
            isVisible = isDrawerVisible,
            progressProvider = { drawerProgress.value },
            predictiveBackProgressProvider = { predictiveBackProgress },
            allApps = uiState.allApps,
            suggestedApps = uiState.suggestedApps,
            iconCache = iconCache,
            settings = if (isTabletModeActive && settings.drawerColumns < 6) {
                settings.copy(drawerColumns = 6)
            } else {
                settings
            },
            searchQuery = uiState.searchQuery,
            requestSearchFocus = requestDrawerSearchFocus,
            onSearchFocusConsumed = onConsumeDrawerSearchFocus,
            onSearchQueryChange = onSearchQueryChange,
            onAppClick = onAppClick,
            onAppLongClick = { app, rect ->
                LauncherHaptics.performLongPress(rootView, settings)
                onAppLongClick(app, null, rect)
            },
            onDragDrawerByDelta = { dy ->
                val deltaProgress = -dy / (screenHeightPx * 0.44f)
                val next = (drawerProgress.value + deltaProgress).coerceIn(0f, 1f)
                coroutineScope.launch {
                    drawerProgress.snapTo(next)
                }
            },
            onSettleDrawerWithVelocity = { vy ->
                val shouldClose = vy > 420f || (drawerProgress.value < 0.74f && vy > -380f)
                if (shouldClose) {
                    LauncherHaptics.performSnapOrTick(rootView, settings)
                }
                animateDrawerTo(
                    target = if (shouldClose) 0f else 1f,
                    initialVelocity = -vy / (screenHeightPx * 0.44f)
                )
            },
            onCloseDrawer = { animateDrawerTo(0f) },
            onWebSearchClick = onWebSearchClick,
            onOpenLauncherSettings = onOpenQuickSettings
        )

        // 4. Expanded Folder Panel Modal
        uiState.activeFolderItem?.let { folderItem ->
            val memberApps = remember(folderItem.folderMemberKeysCsv, uiState.appsByKey) {
                folderItem.folderMemberKeys.mapNotNull { uiState.appsByKey[it] }
            }
            ExpandedFolderDialog(
                folderItem = folderItem,
                memberApps = memberApps,
                allPersonalApps = uiState.allApps.filter { !it.isWorkProfile },
                iconCache = iconCache,
                settings = settings,
                predictiveBackProgressProvider = { predictiveBackProgress },
                onRenameFolder = { newName -> onRenameFolder(folderItem.id, newName) },
                onToggleAppMembership = { appKey -> onToggleAppInFolder(folderItem, appKey) },
                onAppClick = onAppClick,
                onAppLongClick = { app, rect ->
                    LauncherHaptics.performLongPress(rootView, settings)
                    onAppLongClick(app, null, rect)
                },
                onDismiss = onCloseFolder
            )
        }

        // 5. Long-Press Empty Space Pill Menu ("Wallpaper & style", "Widgets", "Home settings")
        WorkspaceLongPressMenuPopup(
            visible = uiState.isWorkspaceMenuVisible,
            anchorOffset = workspaceMenuAnchorOffset,
            onDismiss = onDismissWorkspaceMenu,
            onWallpaperAndStyleClick = onOpenQuickSettings,
            onWidgetsClick = onOpenWidgetPicker,
            onHomeSettingsClick = onOpenQuickSettings
        )

        // 5B. Cute Welcome & Onboarding Dialog
        PixelWelcomeScreenDialog(
            visible = showWelcomeScreen && !settings.hasSeenWelcome,
            isDefaultHome = uiState.isDefaultHomeApp,
            onRequestSetDefaultHome = onRequestRoleHome,
            onGetStarted = {
                LauncherHaptics.performGestureConfirm(rootView, settings)
                onUpdateSettings { it.copy(hasSeenWelcome = true) }
                showWelcomeScreen = false
            },
            onDismiss = {
                onUpdateSettings { it.copy(hasSeenWelcome = true) }
                showWelcomeScreen = false
            }
        )

        // 6. Origin-Anchored Long-Press App Shortcut Popup
        AppShortcutPopupDialog(
            app = uiState.activePopupApp,
            anchorRect = uiState.activePopupAnchorRect,
            onDismiss = onDismissAppPopup,
            onShortcutClick = { app, _ ->
                onAppClick(app, uiState.activePopupAnchorRect ?: Rect(0, 0, 200, 200))
            },
            onClearBadgeClick = onClearBadgeForPackage,
            onAppInfoClick = onAppInfoClick,
            onAddToHomeClick = if (uiState.activePopupHomeItemId == null) {
                { app -> onAddAppToHome(app, pagerState.currentPage) }
            } else null,
            onRemoveFromHomeClick = uiState.activePopupHomeItemId?.let { itemId ->
                { onRemoveHomeItem(itemId) }
            },
            onUninstallClick = onUninstallAppClick
        )

        // 7. Widget & Notification Badge Picker Bottom Sheet
        if (uiState.isWidgetPickerSheetVisible) {
            val systemWidgets = remember { widgetHostController.getInstalledSystemWidgets() }
            val isNotifGranted = remember {
                PixelNotificationListenerService.isNotificationAccessEnabled(context)
            }
            WidgetAndBadgePickerSheet(
                systemWidgets = systemWidgets,
                settings = settings,
                isNotificationAccessGranted = isNotifGranted,
                simulatedBadgesActive = uiState.simulatedBadgesActive,
                onAddBuiltInWidget = { type -> onAddBuiltInWidget(type, pagerState.currentPage) },
                onAddSystemWidget = { candidate -> onAddSystemWidget(candidate, pagerState.currentPage) },
                onUpdateBadgeStyle = { style ->
                    onUpdateSettings { it.copy(badgeStyle = style) }
                },
                onToggleSimulatedBadges = onToggleSimulatedBadges,
                onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                onDismiss = onDismissWidgetPicker
            )
        }

        // 8. Widget Resize & Remove Dialog
        WidgetResizeAndRemoveDialog(
            widgetItem = uiState.activeWidgetResizeItem,
            onResize = { sx, sy ->
                uiState.activeWidgetResizeItem?.let { onResizeWidget(it.id, sx, sy) }
            },
            onRemove = {
                uiState.activeWidgetResizeItem?.let { onRemoveHomeItem(it.id) }
            },
            onDismiss = onDismissWidgetResize
        )

        // 9. Full Settings Studio with Live Preview, App Colors, Tablet Mode, Icon Packs & Backup/Restore
        if (uiState.isQuickSettingsSheetVisible) {
            QuickStyleAndSettingsSheet(
                settings = settings,
                previewApps = uiState.allApps.filter { !it.isWorkProfile },
                iconCache = iconCache,
                availableIconPacks = uiState.availableIconPacks,
                backupStatusMessage = uiState.backupStatusMessage,
                onUpdateSettings = onUpdateSettings,
                onResetDefaults = onResetSettingsAndLayout,
                onExportJsonString = onExportBackupJson,
                onImportJsonString = onImportBackupJson,
                onExportToFileSaf = onExportBackupToFileSaf,
                onImportFromFileSaf = onImportBackupFromFileSaf,
                onDismiss = onDismissQuickSettings
            )
        }

        // 10. Floating Lifted Squircle Icon Overlay during Drag & Drop
        FloatingDraggedIconOverlay(
            dragSession = dragSession,
            iconCache = iconCache,
            settings = settings
        )

        // 11. Spring App Open / App Close Morph Window Overlay
        AppWindowMorphOverlay(
            morphState = activeMorphState,
            iconCache = iconCache,
            settings = settings,
            onAnimationFinished = onMorphAnimationFinished
        )

        // 12. Double-Tap Screen Lock Always-On-Display Overlay
        SimulatedScreenLockOverlay(
            visible = isScreenLockOverlayVisible,
            isAccessibilityConnected = PixelLockAccessibilityService.isConnected,
            onWakeUp = onWakeScreenLockOverlay,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings
        )
    }
}

/**
 * Left pane of the Tablet & Foldable Dual-Pane Workspace Spread:
 * Houses the expanded At-a-Glance widget and live built-in Pixel widgets.
 */
@Composable
private fun TabletDashboardSidePane(
    settings: LauncherSettings,
    widgetHostController: WidgetHostController,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        if (settings.showAtAGlance) {
            AtAGlanceWidget(
                settings = settings,
                onDateClick = onCalendarClick,
                onWeatherClick = onCalendarClick
            )
        }

        // Live Built-In Pixel Widgets in Tablet Dashboard
        WorkspaceWidgetTile(
            componentKey = BuiltInPixelWidgetType.PIXEL_CLOCK.key,
            appWidgetId = -1,
            widgetHostController = widgetHostController,
            temperatureUnit = settings.temperatureUnit,
            onClick = onCalendarClick,
            onLongClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        WorkspaceWidgetTile(
            componentKey = BuiltInPixelWidgetType.PIXEL_BATTERY.key,
            appWidgetId = -1,
            widgetHostController = widgetHostController,
            temperatureUnit = settings.temperatureUnit,
            onClick = onCalendarClick,
            onLongClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspacePageGrid(
    pageIndex: Int,
    isTabletSideBySide: Boolean,
    pageOffsetProvider: () -> Float,
    waveProgressProvider: () -> Float = { 1f },
    items: List<HomeItemEntity>,
    appsByKey: Map<String, AppInfo>,
    iconCache: IconCache,
    widgetHostController: WidgetHostController,
    dragSession: DragSessionState?,
    settings: LauncherSettings,
    onGridBoundsMeasured: (Float, Float) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Long?, Rect) -> Unit,
    onStartDragFromCell: (AppInfo, Long, Int, Int, Rect, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onFolderClick: (HomeItemEntity) -> Unit,
    onWidgetLongPress: (HomeItemEntity) -> Unit,
    onEmptySpaceLongPress: (Offset) -> Unit,
    onEmptySpaceDoubleTap: () -> Unit,
    onCalendarClick: () -> Unit
) {
    val context = LocalContext.current
    val cols = if (isTabletSideBySide) {
        settings.tabletGridColumns.coerceIn(4, 8)
    } else {
        settings.gridColumns.coerceIn(4, 7)
    }
    val rows = if (isTabletSideBySide) {
        settings.tabletGridRows.coerceIn(4, 7)
    } else {
        settings.gridRows.coerceIn(4, 7)
    }

    val widgetsOnPage = remember(items) {
        items.filter { it.itemType == HomeCellItemType.WIDGET.name }
    }
    val nonWidgetItemsByCell = remember(items) {
        items.filter { it.itemType != HomeCellItemType.WIDGET.name }
            .associateBy { it.cellX to it.cellY }
    }

    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(exponent = settings.squircleExponent, preset = settings.iconShape)
    }

    val gridCoordsHolder = remember { GridCoordsHolder() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pageOff = pageOffsetProvider().coerceIn(-1f, 1f)
                val wave = waveProgressProvider().coerceIn(0f, 1.15f)
                val waveScale = 0.88f + 0.12f * wave
                val parallaxScale = if (settings.wallpaperParallax) {
                    1f - (abs(pageOff) * 0.045f)
                } else {
                    1f
                }
                val combinedScale = waveScale * parallaxScale
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = ((1f - abs(pageOff) * 0.22f) * wave.coerceIn(0f, 1f)).coerceIn(0.45f, 1f)
                translationY = (1f - wave) * 18.dp.toPx()
            }
            .onGloballyPositioned { coords ->
                gridCoordsHolder.coords = coords
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onEmptySpaceDoubleTap() },
                    onLongPress = { localOffset ->
                        val gridWinOffset = gridCoordsHolder.resolveWindowPosition()
                        val winOffset = Offset(
                            gridWinOffset.x + localOffset.x,
                            gridWinOffset.y + localOffset.y
                        )
                        onEmptySpaceLongPress(winOffset)
                    }
                )
            }
            .padding(horizontal = settings.gridHorizontalPaddingDp.dp)
    ) {
        val startRow = if (!isTabletSideBySide && pageIndex == 0 && settings.showAtAGlance) {
            AtAGlanceWidget(
                settings = settings,
                onDateClick = onCalendarClick,
                onWeatherClick = onCalendarClick
            )
            1
        } else {
            0
        }

        // Render any placed Widgets on this page
        widgetsOnPage.forEach { widgetItem ->
            val heightDp = (widgetItem.spanY.coerceIn(1, 3) * 86).dp
            WorkspaceWidgetTile(
                componentKey = widgetItem.componentKey,
                appWidgetId = widgetItem.appWidgetId,
                widgetHostController = widgetHostController,
                temperatureUnit = settings.temperatureUnit,
                onClick = onCalendarClick,
                onLongClick = { onWidgetLongPress(widgetItem) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        val b = coords.boundsInWindow()
                        onGridBoundsMeasured(b.top, b.bottom)
                    }
                }
        ) {
            for (r in startRow until rows) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (c in 0 until cols) {
                        val cellItem = nonWidgetItemsByCell[c to r]
                        val isHoveredTargetCell = dragSession != null &&
                            dragSession.hoveredCellX == c &&
                            dragSession.hoveredCellY == r

                        val cellModifier = if (dragSession != null) {
                            val reflowTarget = cellItem?.id?.let { dragSession.reflowPreviewOffsets[it] }
                            val reflowDelta by animateOffsetAsState(
                                targetValue = if (reflowTarget != null) {
                                    Offset(
                                        (reflowTarget.x - c).toFloat(),
                                        (reflowTarget.y - r).toFloat()
                                    )
                                } else {
                                    Offset.Zero
                                },
                                animationSpec = LauncherPhysics.springSpec(settings, context),
                                label = "NeighborReflowSpring"
                            )
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (reflowDelta != Offset.Zero) {
                                        translationX = reflowDelta.x * size.width
                                        translationY = reflowDelta.y * size.height
                                    }
                                }
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = cellModifier
                        ) {
                            if (isHoveredTargetCell &&
                                (dragSession?.hoverMode == WorkspaceHoverMode.EMPTY_CELL ||
                                    dragSession?.hoverMode == WorkspaceHoverMode.REFLOW_NEIGHBOR)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((56.dp * settings.iconSizeScale))
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            shape = squircleShape
                                        )
                                        .graphicsLayer {
                                            clip = true
                                            shape = squircleShape
                                        }
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                )
                            }

                            if (cellItem != null) {
                                val isMergeTarget = isHoveredTargetCell &&
                                    dragSession?.hoverMode == WorkspaceHoverMode.MERGE_FOLDER

                                when (cellItem.itemType) {
                                    HomeCellItemType.FOLDER.name -> {
                                        val members = cellItem.folderMemberKeys.mapNotNull { appsByKey[it] }
                                        SquircleFolderTile(
                                            title = cellItem.title.ifBlank { "Folder" },
                                            memberApps = members,
                                            iconCache = iconCache,
                                            settings = settings,
                                            isFolderMergeHighlighted = isMergeTarget,
                                            onClick = { onFolderClick(cellItem) }
                                        )
                                    }
                                    else -> {
                                        val app = appsByKey[cellItem.componentKey]
                                        if (app != null) {
                                            val isDraggedOrigin = dragSession?.sourceHomeItemId == cellItem.id
                                            SquircleAppIcon(
                                                app = app,
                                                iconCache = iconCache,
                                                settings = settings,
                                                showLabel = settings.showHomeLabels,
                                                useThemedIcon = settings.themedIconsEnabled,
                                                isBeingDragged = isDraggedOrigin,
                                                isFolderMergeHighlighted = isMergeTarget,
                                                onClick = onAppClick,
                                                onLongClick = { clickedApp, rect ->
                                                    onAppLongClick(clickedApp, cellItem.id, rect)
                                                },
                                                onDragStartFromIcon = { draggedApp, rect, winOffset ->
                                                    onStartDragFromCell(
                                                        draggedApp,
                                                        cellItem.id,
                                                        c,
                                                        r,
                                                        rect,
                                                        winOffset
                                                    )
                                                },
                                                onDragMoveWindowOffset = onDragMove,
                                                onDragFinish = onDragEnd
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotseatDockRow(
    hotseatItems: List<HomeItemEntity>,
    appsByKey: Map<String, AppInfo>,
    iconCache: IconCache,
    dragSession: DragSessionState?,
    settings: LauncherSettings,
    drawerProgressProvider: () -> Float,
    waveProgressProvider: () -> Float = { 1f },
    onHotseatTopMeasured: (Float) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Long?, Rect) -> Unit,
    onStartDragFromHotseat: (AppInfo, Long, Int, Rect, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit
) {
    val dockModifier = when (settings.dockBackgroundStyle) {
        DockBackgroundStyle.NONE -> Modifier
            .fillMaxWidth()
            .padding(horizontal = settings.gridHorizontalPaddingDp.dp)
        DockBackgroundStyle.TRANSLUCENT_PILL -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = settings.dockOpacity.coerceIn(0.3f, 0.95f)
                )
            )
            .padding(vertical = 6.dp, horizontal = 8.dp)
        DockBackgroundStyle.SOLID -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 6.dp, horizontal = 8.dp)
    }

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = dockModifier
            .onGloballyPositioned { coords ->
                onHotseatTopMeasured(coords.boundsInWindow().top)
            }
            .graphicsLayer {
                val p = drawerProgressProvider().coerceIn(0f, 1f)
                val wave = waveProgressProvider().coerceIn(0f, 1.15f)
                alpha = ((1f - p * 1.8f) * wave).coerceIn(0f, 1f)
                translationY = (p * 28.dp.toPx()) + ((1f - wave) * 20.dp.toPx())
                val s = 0.82f + 0.18f * wave
                scaleX = s
                scaleY = s
            }
            .testTag("hotseat_dock_row")
    ) {
        hotseatItems.forEachIndexed { slotIndex, item ->
            val app = appsByKey[item.componentKey]
            if (app != null) {
                val isDraggedOrigin = dragSession?.sourceHomeItemId == item.id
                val isSlotHovered = dragSession?.hoveredHotseatIndex == slotIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    SquircleAppIcon(
                        app = app,
                        iconCache = iconCache,
                        settings = settings,
                        showLabel = false,
                        useThemedIcon = settings.themedIconsEnabled,
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
        }
    }
}

@Composable
private fun ExpandedFolderDialog(
    folderItem: HomeItemEntity,
    memberApps: List<AppInfo>,
    allPersonalApps: List<AppInfo>,
    iconCache: IconCache,
    settings: LauncherSettings,
    predictiveBackProgressProvider: () -> Float,
    onRenameFolder: (String) -> Unit,
    onToggleAppMembership: (String) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Rect) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editableTitle by remember(folderItem.id, folderItem.title) {
        mutableStateOf(folderItem.title.ifBlank { "Folder" })
    }
    var isEditingMembers by remember { mutableStateOf(false) }

    val folderSurfaceColor = parseHexColorSafe(
        settings.folderBackgroundColorHex,
        MaterialTheme.colorScheme.surfaceContainerHigh
    ).copy(alpha = settings.folderBackgroundOpacity.coerceIn(0.65f, 1.0f))

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                animationSpec = LauncherPhysics.springSpec(settings, context),
                initialScale = 0.72f
            ) + fadeIn(),
            exit = scaleOut(targetScale = 0.78f) + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = folderSurfaceColor,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        val backP = predictiveBackProgressProvider().coerceIn(0f, 1f)
                        val s = 1f - 0.12f * backP
                        scaleX = s
                        scaleY = s
                    }
                    .testTag("expanded_folder_panel")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    if (settings.showFolderTitles) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            BasicTextField(
                                value = editableTitle,
                                onValueChange = {
                                    editableTitle = it
                                    onRenameFolder(it)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .widthIn(min = 80.dp, max = 200.dp)
                                    .testTag("folder_title_input")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit folder name",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!isEditingMembers) {
                        val cols = settings.folderColumns.coerceIn(3, 4)
                        val rows = (memberApps.size + cols - 1) / cols
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (r in 0 until rows) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (c in 0 until cols) {
                                        val idx = r * cols + c
                                        val app = memberApps.getOrNull(idx)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (app != null) {
                                                val itemReveal = remember { Animatable(0f) }
                                                LaunchedEffect(app.componentKey) {
                                                    kotlinx.coroutines.delay(idx * 22L)
                                                    itemReveal.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = LauncherPhysics.springSpec(settings, context)
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier.graphicsLayer {
                                                        val p = itemReveal.value
                                                        scaleX = 0.65f + 0.35f * p
                                                        scaleY = 0.65f + 0.35f * p
                                                        alpha = p
                                                    }
                                                ) {
                                                    SquircleAppIcon(
                                                        app = app,
                                                        iconCache = iconCache,
                                                        settings = settings,
                                                        showLabel = true,
                                                        useThemedIcon = settings.themedIconsEnabled,
                                                        onClick = onAppClick,
                                                        onLongClick = onAppLongClick
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val currentKeys = remember(folderItem.folderMemberKeysCsv) {
                            folderItem.folderMemberKeys.toSet()
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            items(allPersonalApps, key = { it.componentKey }) { candidate ->
                                val checked = candidate.componentKey in currentKeys
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleAppMembership(candidate.componentKey) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = candidate.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { onToggleAppMembership(candidate.componentKey) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { isEditingMembers = !isEditingMembers },
                        modifier = Modifier.testTag("folder_manage_apps_button")
                    ) {
                        Icon(
                            imageVector = if (isEditingMembers) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEditingMembers) "Done" else "Add / Manage Apps",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
