package com.example.launcher.ui.components

import android.graphics.Rect
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.launcher.animation.LauncherHaptics
import com.example.launcher.icon.IconCache
import com.example.launcher.model.AppCategoryType
import com.example.launcher.model.AppInfo
import com.example.launcher.model.DrawerBackgroundStyle
import com.example.launcher.model.DrawerLayoutMode
import com.example.launcher.model.DrawerSearchPosition
import com.example.launcher.model.DrawerSortMode
import com.example.launcher.model.LabelColorMode
import com.example.launcher.model.LauncherSettings
import kotlinx.coroutines.launch

/**
 * Full-featured Pixel App Drawer (Phase 4):
 * - Supports all 3 layout modes: `VERTICAL` (LazyVerticalGrid), `PAGED` (HorizontalPager of grids),
 *   and `LIST` (Compact alphabetical list).
 * - Category filter tabs (`All`, `Social & Chat`, `Productivity`, `Media & Photo`, `System & Tools`, `Games`)
 *   when `settings.showDrawerCategoryTabs` is enabled.
 * - Hidden Apps Vault with Biometric / Security Unlock prompt when `settings.hiddenAppKeys` is non-empty.
 * - Universal Search across Apps, App Shortcuts, System Settings, and Web Search.
 */
@Composable
fun AppDrawerSheet(
    progressProvider: () -> Float,
    isVisible: Boolean = true,
    predictiveBackProgressProvider: () -> Float = { 0f },
    allApps: List<AppInfo>,
    suggestedApps: List<AppInfo>,
    iconCache: IconCache,
    settings: LauncherSettings,
    searchQuery: String,
    requestSearchFocus: Boolean = false,
    onSearchFocusConsumed: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo, Rect) -> Unit,
    onDragDrawerByDelta: (Float) -> Unit = {},
    onSettleDrawerWithVelocity: (Float) -> Unit = {},
    onCloseDrawer: () -> Unit,
    onOpenLauncherSettings: () -> Unit = {},
    onWebSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val rootView = LocalView.current
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }

    var selectedWorkTab by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<AppCategoryType?>(null) }
    var activeFastScrollLetter by remember { mutableStateOf<String?>(null) }
    var showHiddenVaultUnlockPrompt by remember { mutableStateOf(false) }
    var isHiddenVaultUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            runCatching { searchFocusRequester.requestFocus() }
            onSearchFocusConsumed()
        }
    }

    val hasWorkProfileApps = remember(allApps) { allApps.any { it.isWorkProfile } }

    val pullDownToCloseConnection = remember(gridState) {
        object : NestedScrollConnection {
            private var pullDownOverscrollAccumulated = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val currentP = progressProvider()
                if (currentP >= 0.999f) {
                    if (available.y < 0f) {
                        pullDownOverscrollAccumulated = 0f
                    }
                    return Offset.Zero
                }
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    onDragDrawerByDelta(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    consumed.y == 0f &&
                    available.y > 0f &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                ) {
                    pullDownOverscrollAccumulated += available.y
                    if (pullDownOverscrollAccumulated > 28f) {
                        onDragDrawerByDelta(available.y)
                    }
                    return Offset(0f, available.y)
                }
                if (available.y <= 0f) {
                    pullDownOverscrollAccumulated = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDownOverscrollAccumulated = 0f
                val currentP = progressProvider()
                if (currentP < 0.96f) {
                    onSettleDrawerWithVelocity(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val filteredApps = remember(
        allApps,
        searchQuery,
        selectedWorkTab,
        selectedCategory,
        isHiddenVaultUnlocked,
        settings.drawerSortMode,
        settings.hiddenAppKeys,
        settings.showWorkProfileTab,
        settings.showDrawerCategoryTabs,
        settings.customAppOverrides,
        settings.searchApps,
        hasWorkProfileApps
    ) {
        val base = allApps
            .distinctBy { it.componentKey }
            .filter { app ->
                val isHidden = app.componentKey in settings.hiddenAppKeys
                val passesHiddenFilter = if (isHiddenVaultUnlocked) true else !isHidden
                val passesProfile = if (settings.showWorkProfileTab && hasWorkProfileApps && searchQuery.isBlank()) {
                    app.isWorkProfile == selectedWorkTab
                } else {
                    !app.isWorkProfile
                }
                val passesCategory = if (settings.showDrawerCategoryTabs && selectedCategory != null && searchQuery.isBlank()) {
                    app.category == selectedCategory
                } else {
                    true
                }
                val displayLabel = settings.customAppOverrides[app.componentKey]?.customLabel ?: app.label
                val passesQuery = searchQuery.isBlank() ||
                    (settings.searchApps && (
                        displayLabel.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                        ))

                passesHiddenFilter && passesProfile && passesCategory && passesQuery
            }

        when (settings.drawerSortMode) {
            DrawerSortMode.A_TO_Z -> base.sortedBy {
                (settings.customAppOverrides[it.componentKey]?.customLabel ?: it.label).lowercase()
            }
            DrawerSortMode.MOST_USED -> base.sortedWith(
                compareByDescending<AppInfo> { it.launchCount }
                    .thenByDescending { it.lastLaunchTimestamp }
                    .thenBy { it.label.lowercase() }
            )
            DrawerSortMode.RECENT -> base.sortedByDescending { it.firstInstallTime }
        }
    }

    val uniqueSuggestedApps = remember(suggestedApps, settings.drawerColumns) {
        suggestedApps
            .distinctBy { it.componentKey }
            .take(settings.drawerColumns.coerceIn(3, 7))
    }

    // Matching App Shortcuts when searching and `settings.searchShortcuts` is enabled
    val matchingShortcuts = remember(allApps, searchQuery, settings.searchShortcuts) {
        if (!settings.searchShortcuts || searchQuery.length < 2) emptyList()
        else {
            allApps.filter { !it.isWorkProfile }
                .flatMap { app -> app.shortcuts.map { app to it } }
                .filter { (app, shortcut) ->
                    shortcut.shortLabel.contains(searchQuery, ignoreCase = true) ||
                        app.label.contains(searchQuery, ignoreCase = true)
                }
                .take(4)
        }
    }

    val alphabetLetters = remember {
        ('A'..'Z').map { it.toString() } + "#"
    }

    val drawerIconSettings = remember(settings) {
        settings.copy(
            labelColorMode = LabelColorMode.LIGHT,
            iconShadowEnabled = false
        )
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val surfaceColor = when (settings.drawerBackgroundStyle) {
        DrawerBackgroundStyle.BLUR -> MaterialTheme.colorScheme.surfaceContainerLow
        DrawerBackgroundStyle.DYNAMIC -> MaterialTheme.colorScheme.secondaryContainer
        DrawerBackgroundStyle.SOLID -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isVisible) Modifier.nestedScroll(pullDownToCloseConnection) else Modifier)
            .graphicsLayer {
                if (!isVisible) {
                    alpha = 0f
                    translationY = size.height
                    clip = false
                } else {
                    val p = progressProvider().coerceIn(0f, 1f)
                    val backP = predictiveBackProgressProvider().coerceIn(0f, 1f)
                    if (p >= 0.995f && backP <= 0.005f) {
                        alpha = 1f
                        translationY = 0f
                        scaleX = 1f
                        scaleY = 1f
                        clip = false
                    } else {
                        val backScale = 1f - (0.08f * backP)
                        val cornerDp = ((1f - p) * 28f + (28f * backP)).coerceIn(0f, 32f)

                        alpha = (p * 2.2f * (1f - 0.25f * backP)).coerceIn(0f, 1f)
                        translationY = ((1f - p) * (size.height * 0.52f)) + (backP * 36.dp.toPx())
                        scaleX = backScale
                        scaleY = backScale
                        clip = cornerDp > 1f
                        shape = RoundedCornerShape(cornerDp.dp)
                    }
                }
            }
            .background(
                surfaceColor.copy(alpha = settings.drawerBackgroundOpacity.coerceIn(0.60f, 1.0f))
            )
            .testTag("app_drawer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 8.dp)
        ) {
            // Drag handle pill at the top of the drawer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDragDrawerByDelta(dragAmount)
                            },
                            onDragEnd = { onSettleDrawerWithVelocity(0f) },
                            onDragCancel = { onSettleDrawerWithVelocity(0f) }
                        )
                    }
                    .clickable(onClick = onCloseDrawer)
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            // Top Search Bar inside Drawer
            if (settings.drawerSearchPosition == DrawerSearchPosition.TOP) {
                DrawerSearchPill(
                    query = searchQuery,
                    focusRequester = searchFocusRequester,
                    onQueryChange = onSearchQueryChange,
                    onSettingsClick = onOpenLauncherSettings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Personal / Work Profile Tabs + Hidden Vault Chip
            if ((settings.showWorkProfileTab && hasWorkProfileApps || settings.hiddenAppKeys.isNotEmpty()) &&
                searchQuery.isBlank()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    if (settings.showWorkProfileTab && hasWorkProfileApps) {
                        FilterChip(
                            selected = !selectedWorkTab,
                            onClick = {
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                                selectedWorkTab = false
                            },
                            label = { Text(stringResource(R.string.personal_profile_tab)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("drawer_personal_tab")
                        )

                        FilterChip(
                            selected = selectedWorkTab,
                            onClick = {
                                LauncherHaptics.performSnapOrTick(rootView, settings)
                                selectedWorkTab = true
                            },
                            label = { Text(stringResource(R.string.work_profile_tab)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Work,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("drawer_work_tab")
                        )
                    }

                    if (settings.hiddenAppKeys.isNotEmpty()) {
                        FilterChip(
                            selected = isHiddenVaultUnlocked,
                            onClick = {
                                if (isHiddenVaultUnlocked) {
                                    isHiddenVaultUnlocked = false
                                } else if (settings.biometricLockForHiddenApps) {
                                    showHiddenVaultUnlockPrompt = true
                                } else {
                                    isHiddenVaultUnlocked = true
                                }
                            },
                            label = {
                                Text(if (isHiddenVaultUnlocked) "Vault Unlocked" else "Hidden (${settings.hiddenAppKeys.size})")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Hidden apps vault",
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            // Optional Category Filter Tabs
            if (settings.showDrawerCategoryTabs && searchQuery.isBlank() && !selectedWorkTab) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") }
                    )
                    AppCategoryType.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            }

            val cols = settings.drawerColumns.coerceIn(3, 7)

            Box(modifier = Modifier.weight(1f)) {
                when (settings.drawerLayoutMode) {
                    DrawerLayoutMode.LIST -> {
                        LazyColumn(
                            userScrollEnabled = isVisible,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 28.dp,
                                top = 8.dp,
                                bottom = navBarBottom + 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredApps, key = { "list_${it.componentKey}" }) { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { onAppClick(app, Rect(0, 0, 200, 200)) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    SquircleAppIcon(
                                        app = app,
                                        iconCache = iconCache,
                                        settings = drawerIconSettings,
                                        showLabel = false,
                                        enableLabelShadow = false,
                                        useThemedIcon = settings.themedIconsInDrawer,
                                        baseIconSize = 44.dp,
                                        onClick = onAppClick,
                                        onLongClick = onAppLongClick
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayLabel = settings.customAppOverrides[app.componentKey]?.customLabel
                                            ?.takeIf { it.isNotBlank() } ?: app.label
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = app.category.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DrawerLayoutMode.PAGED -> {
                        val pageSize = (cols * 4).coerceAtLeast(12)
                        val pages = remember(filteredApps, pageSize) {
                            filteredApps.chunked(pageSize).ifEmpty { listOf(emptyList()) }
                        }
                        val drawerPagerState = rememberPagerState(pageCount = { pages.size })
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            HorizontalPager(
                                state = drawerPagerState,
                                userScrollEnabled = isVisible,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { pageIdx ->
                                val pageApps = pages[pageIdx]
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(cols),
                                    userScrollEnabled = false,
                                    contentPadding = PaddingValues(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(pageApps, key = { "paged_${it.componentKey}" }) { app ->
                                        SquircleAppIcon(
                                            app = app,
                                            iconCache = iconCache,
                                            settings = drawerIconSettings,
                                            showLabel = settings.showDrawerLabels,
                                            enableLabelShadow = false,
                                            useThemedIcon = settings.themedIconsInDrawer,
                                            onClick = onAppClick,
                                            onLongClick = onAppLongClick
                                        )
                                    }
                                }
                            }
                            WorkspacePageIndicator(
                                pageCount = pages.size,
                                currentPage = drawerPagerState.currentPage,
                                style = settings.pageIndicatorStyle,
                                modifier = Modifier.padding(bottom = navBarBottom + 8.dp)
                            )
                        }
                    }

                    DrawerLayoutMode.VERTICAL -> {
                        LazyVerticalGrid(
                            state = gridState,
                            userScrollEnabled = isVisible,
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = if (settings.showAzFastScroller) 30.dp else 14.dp,
                                top = 8.dp,
                                bottom = navBarBottom + 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("drawer_apps_grid")
                        ) {
                            if (settings.showSuggestedAppsRow &&
                                searchQuery.isBlank() &&
                                !selectedWorkTab &&
                                selectedCategory == null &&
                                uniqueSuggestedApps.isNotEmpty()
                            ) {
                                item(
                                    key = "header_suggested",
                                    contentType = "header",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.suggested_apps),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                itemsIndexed(
                                    items = uniqueSuggestedApps,
                                    key = { _, app -> "suggested_${app.componentKey}" },
                                    contentType = { _, _ -> "app_icon" }
                                ) { _, app ->
                                    SquircleAppIcon(
                                        app = app,
                                        iconCache = iconCache,
                                        settings = drawerIconSettings,
                                        showLabel = settings.showDrawerLabels,
                                        enableLabelShadow = false,
                                        useThemedIcon = settings.themedIconsInDrawer,
                                        onClick = onAppClick,
                                        onLongClick = onAppLongClick
                                    )
                                }

                                item(
                                    key = "divider_suggested",
                                    contentType = "divider",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.all_apps),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }

                            itemsIndexed(
                                items = filteredApps,
                                key = { _, app -> "all_${app.componentKey}" },
                                contentType = { _, _ -> "app_icon" }
                            ) { _, app ->
                                SquircleAppIcon(
                                    app = app,
                                    iconCache = iconCache,
                                    settings = drawerIconSettings,
                                    showLabel = settings.showDrawerLabels,
                                    enableLabelShadow = false,
                                    useThemedIcon = settings.themedIconsInDrawer,
                                    onClick = onAppClick,
                                    onLongClick = onAppLongClick
                                )
                            }

                            if (filteredApps.isEmpty() && searchQuery.isNotBlank()) {
                                item(
                                    key = "empty_search_state",
                                    contentType = "empty_state",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 12.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.empty_search_title, searchQuery),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.empty_search_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            TextButton(onClick = { onSearchQueryChange("") }) {
                                                Text(stringResource(R.string.clear_search_filter))
                                            }
                                        }
                                    }
                                }
                            }

                            // Universal Search Results: App Shortcuts
                            if (matchingShortcuts.isNotEmpty()) {
                                item(
                                    key = "search_shortcuts_header",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Shortcuts",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        matchingShortcuts.forEach { (app, shortcut) ->
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onAppClick(app, Rect(0, 0, 200, 200)) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Shortcut,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = shortcut.shortLabel,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        )
                                                        Text(
                                                            text = app.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Universal Search Results: Launcher Settings Match
                            if (searchQuery.isNotBlank() && settings.searchSettings) {
                                item(
                                    key = "search_settings_action",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clickable(onClick = onOpenLauncherSettings)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Open PixelHome Settings for \"$searchQuery\"",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            // Web search fallback button when typing a query
                            if (searchQuery.isNotBlank() && settings.searchWeb) {
                                item(
                                    key = "web_search_action",
                                    span = { GridItemSpan(cols) }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                            .clickable { onWebSearchClick(searchQuery) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "Search ${settings.searchEngine.displayName} for \"$searchQuery\"",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right-edge A–Z Fast Scroller
                if (settings.showAzFastScroller &&
                    settings.drawerLayoutMode == DrawerLayoutMode.VERTICAL &&
                    searchQuery.isBlank()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 16.dp, bottom = 16.dp, end = 4.dp)
                            .width(24.dp)
                            .pointerInput(filteredApps, uniqueSuggestedApps) {
                                detectVerticalDragGestures(
                                    onDragEnd = { activeFastScrollLetter = null },
                                    onDragCancel = { activeFastScrollLetter = null },
                                    onVerticalDrag = { change, _ ->
                                        val posY = change.position.y
                                        if (!posY.isFinite()) return@detectVerticalDragGestures
                                        val fraction = (posY / size.height.coerceAtLeast(1))
                                            .coerceIn(0f, 0.999f)
                                        val letterIndex = (fraction * alphabetLetters.size).toInt()
                                            .coerceIn(0, alphabetLetters.lastIndex)
                                        val letter = alphabetLetters[letterIndex]
                                        if (activeFastScrollLetter != letter) {
                                            activeFastScrollLetter = letter
                                            LauncherHaptics.performSnapOrTick(rootView, settings)
                                            val targetIdx = filteredApps.indexOfFirst {
                                                it.initialLetter == letter
                                            }
                                            if (targetIdx >= 0) {
                                                val headerOffset = if (
                                                    settings.showSuggestedAppsRow &&
                                                    searchQuery.isBlank() &&
                                                    !selectedWorkTab &&
                                                    selectedCategory == null &&
                                                    uniqueSuggestedApps.isNotEmpty()
                                                ) {
                                                    uniqueSuggestedApps.size + 2
                                                } else {
                                                    0
                                                }
                                                coroutineScope.launch {
                                                    runCatching {
                                                        gridState.scrollToItem((headerOffset + targetIdx).coerceAtLeast(0))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        alphabetLetters.forEach { letter ->
                            val isHighlighted = activeFastScrollLetter == letter
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isHighlighted) 12.sp else 9.sp,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isHighlighted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    }
                                )
                            )
                        }
                    }
                }

                activeFastScrollLetter?.let { letter ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            if (settings.drawerSearchPosition == DrawerSearchPosition.BOTTOM) {
                DrawerSearchPill(
                    query = searchQuery,
                    focusRequester = searchFocusRequester,
                    onQueryChange = onSearchQueryChange,
                    onSettingsClick = onOpenLauncherSettings,
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = navBarBottom + 10.dp
                        )
                )
            }
        }
    }

    if (showHiddenVaultUnlockPrompt) {
        AlertDialog(
            onDismissRequest = { showHiddenVaultUnlockPrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Biometric verification",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Unlock Hidden Apps Vault") },
            text = {
                Text("Verify your identity to reveal ${settings.hiddenAppKeys.size} hidden app(s) in the drawer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        LauncherHaptics.performGestureConfirm(rootView, settings)
                        showHiddenVaultUnlockPrompt = false
                        isHiddenVaultUnlocked = true
                    }
                ) {
                    Text("Authenticate & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHiddenVaultUnlockPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DrawerSearchPill(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("drawer_search_input_container")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search apps",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_apps_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("drawer_search_text_field")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("drawer_open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Launcher settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
