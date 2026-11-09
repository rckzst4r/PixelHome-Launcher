package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.launcher.service.PixelLockAccessibilityService
import com.example.launcher.ui.LauncherScreen
import com.example.launcher.viewmodel.LauncherViewModel
import com.example.ui.theme.PixelHomeTheme
import kotlin.math.roundToInt

private data class PendingWidgetBindRequest(
    val appWidgetId: Int,
    val provider: ComponentName,
    val label: String,
    val spanX: Int,
    val spanY: Int,
    val page: Int
)

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val dragSession by viewModel.dragSession.collectAsStateWithLifecycle()
            val activeMorphState by viewModel.activeMorphState.collectAsStateWithLifecycle()
            val isScreenLockOverlayVisible by viewModel.isScreenLockOverlayVisible.collectAsStateWithLifecycle()
            val requestDrawerSearchFocus by viewModel.requestDrawerSearchFocus.collectAsStateWithLifecycle()

            val settings = uiState.settings
            val rootView = LocalView.current

            val insetsController = WindowCompat.getInsetsController(window, rootView)
            if (settings.showStatusBar) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            val roleHomeLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.refreshDefaultHomeStatus()
            }

            var pendingWidgetBind by remember { mutableStateOf<PendingWidgetBindRequest?>(null) }
            val widgetBindLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val pending = pendingWidgetBind
                pendingWidgetBind = null
                if (pending != null) {
                    if (result.resultCode == Activity.RESULT_OK) {
                        viewModel.completeSystemWidgetAdd(
                            appWidgetId = pending.appWidgetId,
                            provider = pending.provider,
                            label = pending.label,
                            spanX = pending.spanX,
                            spanY = pending.spanY,
                            page = pending.page
                        )
                    } else {
                        viewModel.widgetHostController.deleteWidgetId(pending.appWidgetId)
                    }
                }
            }

            // Phase 4: Storage Access Framework (SAF) Launchers for JSON Backup Export & Restore
            val exportBackupSafLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    viewModel.exportBackupJson { json ->
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(json.toByteArray(Charsets.UTF_8))
                            }
                            viewModel.setBackupStatusMessage("Saved backup JSON to selected file")
                        }.onFailure { err ->
                            viewModel.setBackupStatusMessage("File write failed: ${err.localizedMessage}")
                        }
                    }
                }
            }

            val importBackupSafLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        }
                    }.onSuccess { content ->
                        if (!content.isNullOrBlank()) {
                            viewModel.importBackupJson(content)
                        }
                    }.onFailure { err ->
                        viewModel.setBackupStatusMessage("File read failed: ${err.localizedMessage}")
                    }
                }
            }

            PixelHomeTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColorsEnabled,
                accentColorHex = settings.accentColorHex,
                fontFamily = settings.fontFamily,
                cornerRadiusScale = settings.uiCornerRadiusScale
            ) {
                LauncherScreen(
                    uiState = uiState,
                    iconCache = viewModel.iconCache,
                    widgetHostController = viewModel.widgetHostController,
                    dragSession = dragSession,
                    activeMorphState = activeMorphState,
                    isScreenLockOverlayVisible = isScreenLockOverlayVisible,
                    requestDrawerSearchFocus = requestDrawerSearchFocus,
                    homePressEvents = viewModel.homePressEvents,
                    onRequestRoleHome = {
                        requestDefaultHomeRole(
                            onLaunchIntent = { intent -> roleHomeLauncher.launch(intent) }
                        )
                    },
                    onDismissRoleBanner = {
                        viewModel.updateSettings { it.copy(defaultHomePromptDismissed = true) }
                    },
                    onExpandNotifications = { expandSystemNotificationShade() },
                    onExpandQuickSettings = { expandSystemQuickSettingsPanel() },
                    onLockScreenAction = { performLockScreenOrShowOverlay() },
                    onWakeScreenLockOverlay = { viewModel.setScreenLockOverlayVisible(false) },
                    onOpenAccessibilitySettings = {
                        viewModel.setScreenLockOverlayVisible(false)
                        runCatching {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                    onOpenNotificationAccessSettings = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    onOpenFavoriteAppGesture = {
                        viewModel.launchFavoriteOrFirstApp(rootView)
                    },
                    onConsumeDrawerSearchFocus = { viewModel.consumeDrawerSearchFocus() },
                    onMorphAnimationFinished = { viewModel.clearMorphState() },
                    onWindowBlurProgressChanged = { progress ->
                        applyWindowBackgroundBlur(progress)
                    },
                    onAppClick = { app, iconRect ->
                        viewModel.launchApp(app, rootView, iconRect)
                    },
                    onAppLongClick = { app, homeItemId, rect ->
                        viewModel.onAppLongPressed(app, homeItemId, rect)
                    },
                    onStartDragSession = { app, itemId, page, cx, cy, rect, winOffset ->
                        viewModel.startDragSession(app, itemId, page, cx, cy, rect, winOffset)
                    },
                    onUpdateDragPosition = { winOffset, page, sw, topDrop, gridTop, gridBot, dockTop ->
                        viewModel.updateDragFingerPosition(
                            fingerOffset = winOffset,
                            currentPage = page,
                            screenWidthPx = sw,
                            topDropZoneBottomPx = topDrop,
                            workspaceGridTopPx = gridTop,
                            workspaceGridBottomPx = gridBot,
                            hotseatTopPx = dockTop
                        )
                    },
                    onFinishDragSession = { page, cancelled ->
                        viewModel.finishDragSession(page, cancelled)
                    },
                    onDismissAppPopup = { viewModel.dismissAppPopup() },
                    onClearBadgeForPackage = { pkg -> viewModel.clearBadgeForPackage(pkg) },
                    onFolderClick = { folder -> viewModel.openFolder(folder) },
                    onCloseFolder = { viewModel.closeFolder() },
                    onRenameFolder = { folderId, title -> viewModel.renameFolder(folderId, title) },
                    onToggleAppInFolder = { folder, appKey ->
                        viewModel.toggleAppInFolder(folder, appKey)
                    },
                    onSearchQueryChange = { query -> viewModel.onSearchQueryChanged(query) },
                    onWebSearchClick = { query -> viewModel.openWebSearch(query) },
                    onVoiceSearchClick = { viewModel.openVoiceSearch() },
                    onCalendarClick = { viewModel.openCalendarOrClock() },
                    onWorkspaceLongPress = { viewModel.setWorkspaceMenuVisible(true) },
                    onDismissWorkspaceMenu = { viewModel.setWorkspaceMenuVisible(false) },
                    onOpenWidgetPicker = { viewModel.setWidgetPickerSheetVisible(true) },
                    onDismissWidgetPicker = { viewModel.setWidgetPickerSheetVisible(false) },
                    onAddBuiltInWidget = { type, page ->
                        viewModel.addBuiltInWidgetToPage(type, page)
                    },
                    onAddSystemWidget = { candidate, page ->
                        viewModel.bindAndAddSystemWidget(
                            candidate = candidate,
                            currentPage = page,
                            onNeedBindPermission = { intent, widgetId, provider, label, sx, sy ->
                                pendingWidgetBind = PendingWidgetBindRequest(
                                    appWidgetId = widgetId,
                                    provider = provider,
                                    label = label,
                                    spanX = sx,
                                    spanY = sy,
                                    page = page
                                )
                                runCatching { widgetBindLauncher.launch(intent) }
                            }
                        )
                    },
                    onWidgetLongPress = { widgetItem ->
                        viewModel.openWidgetResizeDialog(widgetItem)
                    },
                    onDismissWidgetResize = { viewModel.dismissWidgetResizeDialog() },
                    onResizeWidget = { id, sx, sy -> viewModel.resizeWidget(id, sx, sy) },
                    onToggleSimulatedBadges = { enabled -> viewModel.toggleSimulatedBadges(enabled) },
                    onOpenQuickSettings = { viewModel.setQuickSettingsSheetVisible(true) },
                    onDismissQuickSettings = { viewModel.setQuickSettingsSheetVisible(false) },
                    onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
                    onResetSettingsAndLayout = { viewModel.resetSettingsAndLayout() },
                    onExportBackupJson = { callback -> viewModel.exportBackupJson(callback) },
                    onImportBackupJson = { json -> viewModel.importBackupJson(json) },
                    onExportBackupToFileSaf = {
                        exportBackupSafLauncher.launch("pixelhome_backup.json")
                    },
                    onImportBackupFromFileSaf = {
                        importBackupSafLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onAddAppToHome = { app, page -> viewModel.addAppToCurrentWorkspace(app, page) },
                    onRemoveHomeItem = { id -> viewModel.removeHomeItem(id) },
                    onAppInfoClick = { app -> viewModel.openAppDetails(app) },
                    onUninstallAppClick = { app -> viewModel.uninstallApp(app) },
                    onTriggerSearchGestureFocus = { viewModel.triggerDrawerSearchFocus() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.widgetHostController.startListening()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultHomeStatus()
        viewModel.onResumeFromLaunchedApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        ) {
            viewModel.onHomeIntentReceived()
        }
    }

    private fun requestDefaultHomeRole(onLaunchIntent: (Intent) -> Unit) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null &&
                    roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                ) {
                    onLaunchIntent(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
            onLaunchIntent(Intent(Settings.ACTION_HOME_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun applyWindowBackgroundBlur(progress: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radiusPx = (progress.coerceIn(0f, 1f) * 44f).roundToInt()
                window.setBackgroundBlurRadius(radiusPx)
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun expandSystemNotificationShade() {
        if (PixelLockAccessibilityService.expandNotifications()) return
        runCatching {
            val statusBarService = getSystemService("statusbar") ?: return
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManagerClass.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        }
    }

    @SuppressLint("WrongConstant")
    private fun expandSystemQuickSettingsPanel() {
        if (PixelLockAccessibilityService.expandQuickSettings()) return
        runCatching {
            val statusBarService = getSystemService("statusbar") ?: return
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val expandSettingsMethod = statusBarManagerClass.getMethod("expandSettingsPanel")
            expandSettingsMethod.invoke(statusBarService)
        }.onFailure {
            expandSystemNotificationShade()
        }
    }

    private fun performLockScreenOrShowOverlay() {
        if (!PixelLockAccessibilityService.lockScreen()) {
            viewModel.setScreenLockOverlayVisible(true)
        }
    }
}
