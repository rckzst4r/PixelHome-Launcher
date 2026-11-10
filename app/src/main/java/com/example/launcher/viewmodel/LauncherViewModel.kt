package com.example.launcher.viewmodel

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcher.animation.AppWindowMorphState
import com.example.launcher.data.AppLoaderRepository
import com.example.launcher.data.BackupRestoreManager
import com.example.launcher.data.HomeLayoutRepository
import com.example.launcher.data.SettingsRepository
import com.example.launcher.data.db.HomeCellItemType
import com.example.launcher.data.db.HomeContainerType
import com.example.launcher.data.db.HomeDatabase
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.drag.DragDropMath
import com.example.launcher.drag.DragSessionState
import com.example.launcher.drag.TopDropTarget
import com.example.launcher.drag.WorkspaceHoverMode
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.IconPackInfo
import com.example.launcher.model.AppInfo
import com.example.launcher.model.LauncherSettings
import com.example.launcher.service.PixelNotificationListenerService
import com.example.launcher.widget.BuiltInPixelWidgetType
import com.example.launcher.widget.SystemWidgetCandidate
import com.example.launcher.widget.WidgetHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Immutable
data class PopupAnchorData(
    val app: AppInfo,
    val homeItemId: Long?,
    val anchorRect: Rect
)

@Immutable
data class LauncherUiState(
    val isLoading: Boolean = true,
    val settings: LauncherSettings = LauncherSettings(),
    val allApps: List<AppInfo> = emptyList(),
    val appsByKey: Map<String, AppInfo> = emptyMap(),
    val suggestedApps: List<AppInfo> = emptyList(),
    val workspaceItemsByPage: Map<Int, List<HomeItemEntity>> = emptyMap(),
    val hotseatItems: List<HomeItemEntity> = emptyList(),
    val isDefaultHomeApp: Boolean = false,
    val searchQuery: String = "",
    val activeFolderItem: HomeItemEntity? = null,
    val activePopupApp: AppInfo? = null,
    val activePopupHomeItemId: Long? = null,
    val activePopupAnchorRect: Rect? = null,
    val activeWidgetResizeItem: HomeItemEntity? = null,
    val pendingUninstallApp: AppInfo? = null,
    val isWorkspaceMenuVisible: Boolean = false,
    val isQuickSettingsSheetVisible: Boolean = false,
    val isWidgetPickerSheetVisible: Boolean = false,
    val simulatedBadgesActive: Boolean = false,
    val availableIconPacks: List<IconPackInfo> = emptyList(),
    val backupStatusMessage: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val homeDatabase = HomeDatabase.getInstance(appContext)
    private val homeLayoutRepository = HomeLayoutRepository(homeDatabase.homeDao())
    private val appLoaderRepository = AppLoaderRepository(
        context = appContext,
        externalScope = viewModelScope,
        onPackageRemovedListener = { removedPackage ->
            viewModelScope.launch {
                homeLayoutRepository.removePackageFromHomeAndFolders(removedPackage)
                PixelNotificationListenerService.clearBadgeForPackage(removedPackage)
            }
        }
    )
    private val backupRestoreManager = BackupRestoreManager(
        settingsRepository = settingsRepository,
        homeDao = homeDatabase.homeDao()
    )

    val iconCache = IconCache(
        context = appContext,
        activityInfoProvider = { key -> appLoaderRepository.getCachedActivityInfo(key) }
    )
    val widgetHostController = WidgetHostController(appContext)

    private val _searchQuery = MutableStateFlow("")
    private val _isDefaultHomeApp = MutableStateFlow(checkIsDefaultLauncher())
    private val _activeFolderId = MutableStateFlow<Long?>(null)
    private val _activePopupState = MutableStateFlow<PopupAnchorData?>(null)
    private val _activeWidgetResizeId = MutableStateFlow<Long?>(null)
    private val _pendingUninstallApp = MutableStateFlow<AppInfo?>(null)
    private val _isWorkspaceMenuVisible = MutableStateFlow(false)
    private val _isQuickSettingsSheetVisible = MutableStateFlow(false)
    private val _isWidgetPickerSheetVisible = MutableStateFlow(false)
    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    private val _availableIconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())

    // Phase 2: App Open / Close Window Morph State + Lock Overlay + Search Focus
    private val _activeMorphState = MutableStateFlow<AppWindowMorphState?>(null)
    val activeMorphState: StateFlow<AppWindowMorphState?> = _activeMorphState.asStateFlow()

    private var lastLaunchedAppAndBounds: Pair<AppInfo, Rect>? = null
    private var lastLaunchTimestampMs: Long = 0L

    private val _isScreenLockOverlayVisible = MutableStateFlow(false)
    val isScreenLockOverlayVisible: StateFlow<Boolean> = _isScreenLockOverlayVisible.asStateFlow()

    private val _requestDrawerSearchFocus = MutableStateFlow(false)
    val requestDrawerSearchFocus: StateFlow<Boolean> = _requestDrawerSearchFocus.asStateFlow()

    // Phase 3: Active Drag & Drop Session State
    private val _dragSession = MutableStateFlow<DragSessionState?>(null)
    val dragSession: StateFlow<DragSessionState?> = _dragSession.asStateFlow()

    private var seededSimulatedBadgesOnce = false

    private val _homePressEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressEvents: SharedFlow<Unit> = _homePressEvents.asSharedFlow()

    private data class OverlayState(
        val searchQuery: String,
        val isDefaultHome: Boolean,
        val activeFolderId: Long?,
        val activePopup: PopupAnchorData?,
        val activeWidgetResizeId: Long?,
        val pendingUninstallApp: AppInfo?,
        val workspaceMenuVisible: Boolean,
        val quickSettingsVisible: Boolean,
        val widgetPickerVisible: Boolean,
        val backupStatusMessage: String?
    )

    private val sheetVisibilityFlow = combine(
        _isWorkspaceMenuVisible,
        _isQuickSettingsSheetVisible,
        _isWidgetPickerSheetVisible,
        _activeWidgetResizeId,
        _backupStatusMessage
    ) { menu, quick, widgetPicker, resizeId, backupMsg ->
        listOf(menu, quick, widgetPicker, resizeId, backupMsg)
    }

    private val overlayStateFlow = combine(
        _searchQuery,
        _isDefaultHomeApp,
        _activeFolderId,
        _activePopupState,
        combine(sheetVisibilityFlow, _pendingUninstallApp) { sheets, uninstallApp ->
            sheets to uninstallApp
        }
    ) { query, isDefault, folderId, popup, (sheets, uninstallApp) ->
        OverlayState(
            searchQuery = query,
            isDefaultHome = isDefault,
            activeFolderId = folderId,
            activePopup = popup,
            activeWidgetResizeId = sheets[3] as Long?,
            pendingUninstallApp = uninstallApp,
            workspaceMenuVisible = sheets[0] as Boolean,
            quickSettingsVisible = sheets[1] as Boolean,
            widgetPickerVisible = sheets[2] as Boolean,
            backupStatusMessage = sheets[4] as String?
        )
    }

    private val notificationBadgesFlow = combine(
        PixelNotificationListenerService.liveNotificationCounts,
        PixelNotificationListenerService.simulatedCounts
    ) { live, sim ->
        val merged = live.toMutableMap()
        for ((pkg, count) in sim) {
            merged[pkg] = (merged[pkg] ?: 0) + count
        }
        merged to sim.isNotEmpty()
    }

    private data class CoreWorkspaceData(
        val settings: LauncherSettings,
        val homeItems: List<HomeItemEntity>,
        val enrichedApps: List<AppInfo>,
        val appsByKey: Map<String, AppInfo>,
        val suggestedApps: List<AppInfo>,
        val workspaceItemsByPage: Map<Int, List<HomeItemEntity>>,
        val hotseatItems: List<HomeItemEntity>,
        val simulatedBadgesActive: Boolean
    )

    private val coreWorkspaceFlow = combine(
        settingsRepository.settingsFlow,
        appLoaderRepository.installedApps,
        homeLayoutRepository.homeItemsFlow,
        homeLayoutRepository.appUsageFlow,
        notificationBadgesFlow
    ) { settings, rawApps, homeItems, usageList, badgePair ->
        val (badgeMap, simActive) = badgePair
        val usageByKey = usageList.associateBy { it.componentKey }

        if (!seededSimulatedBadgesOnce && rawApps.isNotEmpty() && badgeMap.isEmpty()) {
            seededSimulatedBadgesOnce = true
            PixelNotificationListenerService.setSimulatedBadgesForPackages(
                packages = rawApps.filter { !it.isWorkProfile }.take(4).map { it.packageName },
                enabled = true
            )
        }

        val enrichedApps = rawApps.map { app ->
            val usage = usageByKey[app.componentKey]
            val badgeCount = if (app.isWorkProfile) 0 else (badgeMap[app.packageName] ?: 0)
            if (usage == null && badgeCount == app.notificationCount) {
                app
            } else {
                app.copy(
                    launchCount = usage?.launchCount ?: 0,
                    lastLaunchTimestamp = usage?.lastLaunchTimestamp ?: 0L,
                    notificationCount = badgeCount
                )
            }
        }
        val appsByKey = enrichedApps.associateBy { it.componentKey }

        if (!seededInitialHomeOnce && enrichedApps.isNotEmpty() && homeItems.isEmpty()) {
            seededInitialHomeOnce = true
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                homeLayoutRepository.ensureSeededWithApps(
                    apps = enrichedApps,
                    dockCount = settings.dockIconCount,
                    columns = settings.gridColumns
                )
            }
        } else if (homeItems.isNotEmpty()) {
            seededInitialHomeOnce = true
        }

        val personalApps = enrichedApps.filter { !it.isWorkProfile && it.componentKey !in settings.hiddenAppKeys }
        val suggested = personalApps
            .sortedWith(
                compareByDescending<AppInfo> { it.launchCount }
                    .thenByDescending { it.lastLaunchTimestamp }
                    .thenBy { it.label.lowercase() }
            )
            .take(settings.suggestedAppsCount.coerceIn(4, 8))

        val workspaceItems = homeItems
            .asSequence()
            .filter { it.container == HomeContainerType.WORKSPACE.name }
            .groupBy { it.page }

        val hotseat = homeItems
            .asSequence()
            .filter { it.container == HomeContainerType.HOTSEAT.name }
            .sortedBy { it.cellX }
            .take(settings.dockIconCount)
            .toList()

        CoreWorkspaceData(
            settings = settings,
            homeItems = homeItems,
            enrichedApps = enrichedApps,
            appsByKey = appsByKey,
            suggestedApps = suggested,
            workspaceItemsByPage = workspaceItems,
            hotseatItems = hotseat,
            simulatedBadgesActive = simActive
        )
    }
        .distinctUntilChanged()
        .flowOn(kotlinx.coroutines.Dispatchers.Default)

    val uiState: StateFlow<LauncherUiState> = combine(
        coreWorkspaceFlow,
        overlayStateFlow,
        _availableIconPacks
    ) { core, overlay, iconPacks ->
        val activeFolder = overlay.activeFolderId?.let { id ->
            core.homeItems.firstOrNull { it.id == id && it.itemType == HomeCellItemType.FOLDER.name }
        }

        val activeWidgetResize = overlay.activeWidgetResizeId?.let { id ->
            core.homeItems.firstOrNull { it.id == id && it.itemType == HomeCellItemType.WIDGET.name }
        }

        val popupApp = overlay.activePopup?.app?.let { core.appsByKey[it.componentKey] ?: it }

        LauncherUiState(
            isLoading = core.enrichedApps.isEmpty(),
            settings = core.settings,
            allApps = core.enrichedApps,
            appsByKey = core.appsByKey,
            suggestedApps = core.suggestedApps,
            workspaceItemsByPage = core.workspaceItemsByPage,
            hotseatItems = core.hotseatItems,
            isDefaultHomeApp = overlay.isDefaultHome,
            searchQuery = overlay.searchQuery,
            activeFolderItem = activeFolder,
            activePopupApp = popupApp,
            activePopupHomeItemId = overlay.activePopup?.homeItemId,
            activePopupAnchorRect = overlay.activePopup?.anchorRect,
            activeWidgetResizeItem = activeWidgetResize,
            pendingUninstallApp = overlay.pendingUninstallApp,
            isWorkspaceMenuVisible = overlay.workspaceMenuVisible,
            isQuickSettingsSheetVisible = overlay.quickSettingsVisible,
            isWidgetPickerSheetVisible = overlay.widgetPickerVisible,
            simulatedBadgesActive = core.simulatedBadgesActive,
            availableIconPacks = iconPacks,
            backupStatusMessage = overlay.backupStatusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LauncherUiState()
    )

    init {
        widgetHostController.startListening()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _availableIconPacks.value = iconCache.iconPackManager.getAvailableIconPacks()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            appLoaderRepository.installedApps.collect { apps ->
                if (apps.isNotEmpty()) {
                    iconCache.preloadIcons(apps)
                }
            }
        }
    }

    fun refreshDefaultHomeStatus() {
        _isDefaultHomeApp.value = checkIsDefaultLauncher()
    }

    private fun checkIsDefaultLauncher(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = appContext.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = appContext.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo?.activityInfo?.packageName == appContext.packageName
        }.getOrDefault(false)
    }

    fun onHomeIntentReceived() {
        _searchQuery.value = ""
        _activeFolderId.value = null
        _activePopupState.value = null
        _activeWidgetResizeId.value = null
        _pendingUninstallApp.value = null
        _isWorkspaceMenuVisible.value = false
        _isQuickSettingsSheetVisible.value = false
        _isWidgetPickerSheetVisible.value = false
        _isScreenLockOverlayVisible.value = false
        _dragSession.value = null
        onResumeFromLaunchedApp()
        _homePressEvents.tryEmit(Unit)
    }

    private var seededInitialHomeOnce = false

    fun onResumeFromLaunchedApp() {
        lastLaunchedAppAndBounds = null
        _activeMorphState.value = null
        appLoaderRepository.refreshInstalledApps()
    }

    fun clearMorphState() {
        _activeMorphState.value = null
    }

    fun setScreenLockOverlayVisible(visible: Boolean) {
        _isScreenLockOverlayVisible.value = visible
    }

    fun triggerDrawerSearchFocus() {
        _requestDrawerSearchFocus.value = true
    }

    fun consumeDrawerSearchFocus() {
        _requestDrawerSearchFocus.value = false
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // -------------------------------------------------------------------------
    // Phase 4: Backup & Restore Engine (JSON + SAF File Export/Import)
    // -------------------------------------------------------------------------

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                backupRestoreManager.exportFullBackupJson(uiState.value.settings)
            }.onSuccess { json ->
                _backupStatusMessage.value = "Exported full backup (${json.length} bytes)"
                onResult(json)
            }.onFailure { err ->
                _backupStatusMessage.value = "Export failed: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            runCatching {
                backupRestoreManager.importFullBackupJson(json)
            }.onSuccess { restored ->
                if (restored) {
                    iconCache.clearCache()
                    _backupStatusMessage.value = "Restored settings & home layout successfully"
                } else {
                    _backupStatusMessage.value = "Restore failed: Invalid backup JSON format"
                }
            }.onFailure { err ->
                _backupStatusMessage.value = "Restore failed: ${err.localizedMessage ?: "Invalid JSON"}"
            }
        }
    }

    fun setBackupStatusMessage(message: String?) {
        _backupStatusMessage.value = message
    }

    // -------------------------------------------------------------------------
    // Phase 3: Drag & Drop Engine
    // -------------------------------------------------------------------------

    fun startDragSession(
        app: AppInfo,
        sourceHomeItemId: Long?,
        currentPage: Int,
        sourceCellX: Int,
        sourceCellY: Int,
        iconRect: Rect,
        fingerWindowOffset: Offset
    ) {
        _activePopupState.value = null
        _activeFolderId.value = null
        _dragSession.value = DragSessionState(
            draggedApp = app,
            sourceHomeItemId = sourceHomeItemId,
            sourcePage = currentPage,
            sourceCellX = sourceCellX,
            sourceCellY = sourceCellY,
            initialIconCenter = Offset(iconRect.exactCenterX(), iconRect.exactCenterY()),
            fingerWindowOffset = fingerWindowOffset
        )
    }

    fun updateDragFingerPosition(
        fingerOffset: Offset,
        currentPage: Int,
        screenWidthPx: Float,
        topDropZoneBottomPx: Float,
        workspaceGridTopPx: Float,
        workspaceGridBottomPx: Float,
        hotseatTopPx: Float
    ): Boolean {
        val current = _dragSession.value ?: return false
        val state = uiState.value
        val settings = state.settings
        val cols = settings.gridColumns.coerceIn(4, 7)
        val rows = settings.gridRows.coerceIn(4, 7)
        val startRow = if (currentPage == 0 && settings.showAtAGlance) 1 else 0

        if (fingerOffset.y <= topDropZoneBottomPx) {
            val fractionX = (fingerOffset.x / screenWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val topTarget = if (current.sourceHomeItemId != null) {
                when {
                    fractionX < 0.34f -> TopDropTarget.REMOVE
                    fractionX < 0.67f -> TopDropTarget.APP_INFO
                    else -> TopDropTarget.UNINSTALL
                }
            } else {
                when {
                    fractionX < 0.50f -> TopDropTarget.APP_INFO
                    else -> TopDropTarget.UNINSTALL
                }
            }
            val changed = topTarget != current.hoveredTopTarget
            _dragSession.value = current.copy(
                fingerWindowOffset = fingerOffset,
                hoveredTopTarget = topTarget,
                hoveredCellX = -1,
                hoveredCellY = -1,
                hoveredHotseatIndex = -1,
                reflowPreviewOffsets = emptyMap()
            )
            return changed
        }

        if (settings.lockHomeLayout) {
            _dragSession.value = current.copy(
                fingerWindowOffset = fingerOffset,
                hoveredTopTarget = TopDropTarget.NONE,
                hoveredCellX = -1,
                hoveredCellY = -1,
                hoveredHotseatIndex = -1
            )
            return false
        }

        if (fingerOffset.y >= hotseatTopPx) {
            val dockCount = settings.dockIconCount.coerceIn(3, 6)
            val dockIndex = ((fingerOffset.x / screenWidthPx.coerceAtLeast(1f)) * dockCount)
                .toInt()
                .coerceIn(0, dockCount - 1)
            val changed = dockIndex != current.hoveredHotseatIndex
            _dragSession.value = current.copy(
                fingerWindowOffset = fingerOffset,
                hoveredTopTarget = TopDropTarget.NONE,
                hoveredCellX = -1,
                hoveredCellY = -1,
                hoveredHotseatIndex = dockIndex,
                hoverMode = WorkspaceHoverMode.HOTSEAT_SLOT,
                reflowPreviewOffsets = emptyMap()
            )
            return changed
        }

        val gridHeight = (workspaceGridBottomPx - workspaceGridTopPx).coerceAtLeast(1f)
        val visibleRows = (rows - startRow).coerceAtLeast(1)
        val cellWidth = screenWidthPx / cols
        val cellHeight = gridHeight / visibleRows

        val cellX = ((fingerOffset.x / cellWidth).toInt()).coerceIn(0, cols - 1)
        val relRow = (((fingerOffset.y - workspaceGridTopPx) / cellHeight).toInt())
            .coerceIn(0, visibleRows - 1)
        val cellY = (startRow + relRow).coerceIn(startRow, rows - 1)

        val cellCenterX = (cellX + 0.5f) * cellWidth
        val cellCenterY = workspaceGridTopPx + (relRow + 0.5f) * cellHeight
        val radialDist = hypot(fingerOffset.x - cellCenterX, fingerOffset.y - cellCenterY)
        val cellRadius = minOf(cellWidth, cellHeight) * 0.5f

        val pageItems = state.workspaceItemsByPage[currentPage].orEmpty()
        val occupant = pageItems.firstOrNull {
            it.id != current.sourceHomeItemId &&
                it.cellX == cellX &&
                it.cellY == cellY &&
                it.itemType != HomeCellItemType.WIDGET.name
        }

        val hoverMode = when {
            occupant == null -> WorkspaceHoverMode.EMPTY_CELL
            radialDist < cellRadius * 0.48f -> WorkspaceHoverMode.MERGE_FOLDER
            else -> WorkspaceHoverMode.REFLOW_NEIGHBOR
        }

        val sourceCell = if (current.sourceCellX >= 0 && current.sourceCellY >= 0) {
            current.sourceCellX to current.sourceCellY
        } else null

        val reflowOffsets = DragDropMath.computeReflowMap(
            pageItems = pageItems,
            draggedItemId = current.sourceHomeItemId,
            sourceCell = sourceCell,
            hoveredCellX = cellX,
            hoveredCellY = cellY,
            hoverMode = hoverMode,
            cols = cols,
            rows = rows,
            startRow = startRow
        )

        val changed = cellX != current.hoveredCellX ||
            cellY != current.hoveredCellY ||
            hoverMode != current.hoverMode

        _dragSession.value = current.copy(
            fingerWindowOffset = fingerOffset,
            hoveredTopTarget = TopDropTarget.NONE,
            hoveredCellX = cellX,
            hoveredCellY = cellY,
            hoveredHotseatIndex = -1,
            hoverMode = hoverMode,
            reflowPreviewOffsets = reflowOffsets
        )
        return changed
    }

    fun finishDragSession(currentPage: Int, cancelled: Boolean = false) {
        val session = _dragSession.value ?: return
        _dragSession.value = null
        if (cancelled) return

        val state = uiState.value
        when (session.hoveredTopTarget) {
            TopDropTarget.REMOVE -> {
                session.sourceHomeItemId?.let { id -> removeHomeItem(id) }
                return
            }
            TopDropTarget.APP_INFO -> {
                openAppDetails(session.draggedApp)
                return
            }
            TopDropTarget.UNINSTALL -> {
                uninstallApp(session.draggedApp)
                return
            }
            TopDropTarget.NONE -> Unit
        }

        if (state.settings.lockHomeLayout) return

        viewModelScope.launch {
            if (session.hoveredHotseatIndex >= 0) {
                homeLayoutRepository.commitDragDropMove(
                    draggedApp = session.draggedApp,
                    sourceHomeItemId = session.sourceHomeItemId,
                    targetContainer = HomeContainerType.HOTSEAT,
                    targetPage = 0,
                    targetCellX = session.hoveredHotseatIndex,
                    targetCellY = 0
                )
            } else if (session.hoveredCellX >= 0 && session.hoveredCellY >= 0) {
                val pageItems = state.workspaceItemsByPage[currentPage].orEmpty()
                val targetOccupant = pageItems.firstOrNull {
                    it.id != session.sourceHomeItemId &&
                        it.cellX == session.hoveredCellX &&
                        it.cellY == session.hoveredCellY
                }

                if (session.hoverMode == WorkspaceHoverMode.MERGE_FOLDER && targetOccupant != null) {
                    homeLayoutRepository.mergeIntoFolder(
                        draggedApp = session.draggedApp,
                        sourceHomeItemId = session.sourceHomeItemId,
                        targetItem = targetOccupant
                    )
                } else {
                    val reflowMoves = session.reflowPreviewOffsets.mapValues { (_, offset) ->
                        offset.x to offset.y
                    }
                    homeLayoutRepository.commitDragDropMove(
                        draggedApp = session.draggedApp,
                        sourceHomeItemId = session.sourceHomeItemId,
                        targetContainer = HomeContainerType.WORKSPACE,
                        targetPage = currentPage,
                        targetCellX = session.hoveredCellX,
                        targetCellY = session.hoveredCellY,
                        reflowNeighborMoves = reflowMoves
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3: Folders, Widgets & Notification Badges
    // -------------------------------------------------------------------------

    fun renameFolder(folderId: Long, newTitle: String) {
        viewModelScope.launch {
            homeLayoutRepository.renameFolder(folderId, newTitle)
        }
    }

    fun toggleAppInFolder(folderItem: HomeItemEntity, appKey: String) {
        viewModelScope.launch {
            val currentMembers = folderItem.folderMemberKeys
            val updated = if (appKey in currentMembers) {
                currentMembers - appKey
            } else {
                currentMembers + appKey
            }
            homeLayoutRepository.updateFolderMembers(folderItem.id, updated)
        }
    }

    fun setWidgetPickerSheetVisible(visible: Boolean) {
        _isWidgetPickerSheetVisible.value = visible
    }

    fun openWidgetResizeDialog(widgetItem: HomeItemEntity) {
        _activeWidgetResizeId.value = widgetItem.id
    }

    fun dismissWidgetResizeDialog() {
        _activeWidgetResizeId.value = null
    }

    fun resizeWidget(widgetId: Long, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            homeLayoutRepository.resizeWidgetItem(widgetId, spanX, spanY)
        }
    }

    fun addBuiltInWidgetToPage(widgetType: BuiltInPixelWidgetType, currentPage: Int) {
        viewModelScope.launch {
            val state = uiState.value
            val rows = state.settings.gridRows.coerceIn(4, 7)
            val startRow = if (currentPage == 0 && state.settings.showAtAGlance) 1 else 0
            homeLayoutRepository.addWidgetToWorkspace(
                componentKey = widgetType.key,
                appWidgetId = -1,
                title = widgetType.title,
                page = currentPage,
                cellX = 0,
                cellY = startRow.coerceAtMost(rows - 1),
                spanX = widgetType.defaultSpanX,
                spanY = widgetType.defaultSpanY
            )
        }
    }

    fun bindAndAddSystemWidget(
        candidate: SystemWidgetCandidate,
        currentPage: Int,
        onNeedBindPermission: (Intent, Int, ComponentName, String, Int, Int) -> Unit
    ) {
        val allocatedId = widgetHostController.allocateWidgetId()
        if (widgetHostController.tryBindWidgetSilently(allocatedId, candidate.providerComponent)) {
            completeSystemWidgetAdd(
                appWidgetId = allocatedId,
                provider = candidate.providerComponent,
                label = candidate.label,
                spanX = candidate.minSpanX,
                spanY = candidate.minSpanY,
                page = currentPage
            )
        } else {
            val intent = widgetHostController.createBindPermissionIntent(
                allocatedId,
                candidate.providerComponent
            )
            onNeedBindPermission(
                intent,
                allocatedId,
                candidate.providerComponent,
                candidate.label,
                candidate.minSpanX,
                candidate.minSpanY
            )
        }
    }

    fun completeSystemWidgetAdd(
        appWidgetId: Int,
        provider: ComponentName,
        label: String,
        spanX: Int,
        spanY: Int,
        page: Int
    ) {
        viewModelScope.launch {
            val state = uiState.value
            val startRow = if (page == 0 && state.settings.showAtAGlance) 1 else 0
            homeLayoutRepository.addWidgetToWorkspace(
                componentKey = provider.flattenToString(),
                appWidgetId = appWidgetId,
                title = label,
                page = page,
                cellX = 0,
                cellY = startRow,
                spanX = spanX,
                spanY = spanY
            )
        }
    }

    fun toggleSimulatedBadges(enabled: Boolean) {
        val pkgs = uiState.value.allApps.filter { !it.isWorkProfile }.map { it.packageName }
        PixelNotificationListenerService.setSimulatedBadgesForPackages(pkgs, enabled)
    }

    fun clearBadgeForPackage(packageName: String) {
        PixelNotificationListenerService.clearBadgeForPackage(packageName)
    }

    // -------------------------------------------------------------------------
    // App Launch & Popups
    // -------------------------------------------------------------------------

    fun launchApp(app: AppInfo, rootView: View?, iconBounds: Rect?) {
        _activePopupState.value = null
        _activeFolderId.value = null
        _activeMorphState.value = null
        val bounds = iconBounds ?: Rect(200, 400, 360, 560)

        appLoaderRepository.launchApp(
            app = app,
            rootView = rootView,
            iconBoundsOnScreen = bounds,
            animationStyle = uiState.value.settings.appOpenAnimationStyle
        )

        viewModelScope.launch {
            homeLayoutRepository.recordAppLaunch(app.componentKey)
        }
    }

    fun launchFavoriteOrFirstApp(rootView: View?) {
        val state = uiState.value
        val target = state.appsByKey[state.settings.doubleTapTargetAppKey]
            ?: state.suggestedApps.firstOrNull()
            ?: state.allApps.firstOrNull()
        if (target != null) {
            launchApp(target, rootView, Rect(360, 800, 520, 960))
        }
    }

    fun onAppLongPressed(app: AppInfo, homeItemId: Long? = null, anchorRect: Rect = Rect(200, 400, 360, 560)) {
        if (_dragSession.value == null) {
            _activePopupState.value = PopupAnchorData(
                app = app,
                homeItemId = homeItemId,
                anchorRect = anchorRect
            )
        }
    }

    fun dismissAppPopup() {
        _activePopupState.value = null
    }

    fun openFolder(folderItem: HomeItemEntity) {
        _activeFolderId.value = folderItem.id
    }

    fun closeFolder() {
        _activeFolderId.value = null
    }

    fun setWorkspaceMenuVisible(visible: Boolean) {
        _isWorkspaceMenuVisible.value = visible
    }

    fun setQuickSettingsSheetVisible(visible: Boolean) {
        _isQuickSettingsSheetVisible.value = visible
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }

    fun resetSettingsAndLayout() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            iconCache.clearCache()
            val currentApps = uiState.value.allApps
            homeLayoutRepository.resetHomeLayout(
                apps = currentApps,
                dockCount = 4,
                columns = 5
            )
            _backupStatusMessage.value = "Reset settings and home layout to defaults"
        }
    }

    fun addAppToCurrentWorkspace(app: AppInfo, currentPage: Int) {
        viewModelScope.launch {
            val state = uiState.value
            val cols = state.settings.gridColumns.coerceIn(4, 7)
            val rows = state.settings.gridRows.coerceIn(4, 7)
            val totalPages = maxOf(
                state.settings.homePageCount,
                (state.workspaceItemsByPage.keys.maxOrNull() ?: 0) + 1,
                1
            )

            // Search currentPage first, then remaining pages in order
            val candidatePages = buildList {
                add(currentPage.coerceIn(0, totalPages - 1))
                for (p in 0 until totalPages) {
                    if (p !in this) add(p)
                }
            }

            for (page in candidatePages) {
                val existing = state.workspaceItemsByPage[page].orEmpty()
                val occupied = existing
                    .filter { it.itemType != HomeCellItemType.WIDGET.name }
                    .map { it.cellX to it.cellY }
                    .toSet()
                val startRow = if (page == 0 && state.settings.showAtAGlance) 1 else 0

                for (r in startRow until rows) {
                    for (c in 0 until cols) {
                        if ((c to r) !in occupied) {
                            homeLayoutRepository.addAppToHome(app, page, c, r)
                            return@launch
                        }
                    }
                }
            }

            // All existing pages are full: create a new page and place the app at (0, 0)
            val newPage = totalPages
            settingsRepository.updateSettings {
                it.copy(homePageCount = (newPage + 1).coerceAtMost(7))
            }
            homeLayoutRepository.addAppToHome(app, newPage, 0, 0)
        }
    }

    fun removeHomeItem(id: Long) {
        _activePopupState.value = null
        viewModelScope.launch {
            val items = uiState.value.workspaceItemsByPage.values.flatten()
            val widgetItem = items.firstOrNull { it.id == id && it.appWidgetId > 0 }
            if (widgetItem != null) {
                widgetHostController.deleteWidgetId(widgetItem.appWidgetId)
            }
            homeLayoutRepository.removeHomeItem(id)
        }
    }

    fun openAppDetails(app: AppInfo) {
        _activePopupState.value = null
        _pendingUninstallApp.value = null
        appLoaderRepository.openAppDetailsSettings(app)
    }

    fun uninstallApp(app: AppInfo) {
        _activePopupState.value = null
        val launchedSystemUninstaller = appLoaderRepository.requestUninstallApp(app)
        if (!launchedSystemUninstaller) {
            _pendingUninstallApp.value = app
        }
    }

    fun confirmLauncherUninstall(app: AppInfo) {
        _pendingUninstallApp.value = null
        _activePopupState.value = null
        viewModelScope.launch {
            homeLayoutRepository.removePackageFromHomeAndFolders(
                packageName = app.packageName,
                componentKey = app.componentKey
            )
            settingsRepository.updateSettings { current ->
                current.copy(hiddenAppKeys = current.hiddenAppKeys + app.componentKey)
            }
            PixelNotificationListenerService.clearBadgeForPackage(app.packageName)
            runCatching {
                Toast.makeText(appContext, "${app.label} uninstalled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dismissUninstallDialog() {
        _pendingUninstallApp.value = null
    }

    fun openCalendarOrClock() {
        runCatching {
            val calUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = calUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            runCatching {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(clockIntent)
            }
        }
    }

    fun openWebSearch(query: String) {
        val url = uiState.value.settings.searchEngine.queryUrl + Uri.encode(query.ifBlank { "Google Pixel" })
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    fun openVoiceSearch() {
        runCatching {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            openWebSearch("")
        }
    }

    override fun onCleared() {
        super.onCleared()
        widgetHostController.stopListening()
        appLoaderRepository.unregister()
    }
}
