package com.example.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.IconPackInfo
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AnimationPreset
import com.example.launcher.model.AppColorPaletteMode
import com.example.launcher.model.AppInfo
import com.example.launcher.model.AppOpenAnimationStyle
import com.example.launcher.model.BadgeStyle
import com.example.launcher.model.CustomAppOverride
import com.example.launcher.model.DockBackgroundStyle
import com.example.launcher.model.DrawerBackgroundStyle
import com.example.launcher.model.DrawerLayoutMode
import com.example.launcher.model.DrawerSearchPosition
import com.example.launcher.model.DrawerSortMode
import com.example.launcher.model.FolderPreviewStyle
import com.example.launcher.model.HapticStrength
import com.example.launcher.model.IconShapePreset
import com.example.launcher.model.LabelColorMode
import com.example.launcher.model.LauncherFontFamily
import com.example.launcher.model.LauncherGestureAction
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.OemCompatibilityProfile
import com.example.launcher.model.PageIndicatorStyle
import com.example.launcher.model.SearchBarPosition
import com.example.launcher.model.SearchBarStyle
import com.example.launcher.model.SearchEngine
import com.example.launcher.model.ThemeMode
import com.example.ui.theme.parseHexColorSafe
import java.util.Locale

private enum class SettingsSectionTab(val title: String) {
    ALL("All"),
    APP_COLORS("App Colors"),
    TABLET_UNIVERSAL("Tablet & Universal"),
    ICONS("Icons & Packs"),
    HOME("Home Grid"),
    SEARCH("Search Bar"),
    DOCK("Dock"),
    DRAWER("Drawer"),
    FOLDERS("Folders"),
    ANIMATIONS("Animations & Gestures"),
    THEME("Theme"),
    BACKUP("Backup & System")
}

private data class AppColorThemePreset(
    val name: String,
    val plateHex: String,
    val glyphHex: String
)

private val curatedAppColorThemes = listOf(
    AppColorThemePreset("Cyber Neon", "#0D111A", "#00E5FF"),
    AppColorThemePreset("Ocean Sapphire", "#10243E", "#7AB8FF"),
    AppColorThemePreset("Emerald Mint", "#0F291E", "#81C995"),
    AppColorThemePreset("Sunset Coral", "#2D1418", "#F28B82"),
    AppColorThemePreset("Royal Amethyst", "#211536", "#C58AF9"),
    AppColorThemePreset("Gold Luxe", "#2B2312", "#FDD663"),
    AppColorThemePreset("Clean Snow", "#F3F5FA", "#1A1C1E"),
    AppColorThemePreset("Obsidian Mono", "#14171F", "#F1F4F9")
)

/**
 * Full-Screen Material 3 PixelHome Settings & Live Preview Studio.
 *
 * Automatically adapts to a 2-column Split-View Navigation Rail layout on Tablets, Foldables,
 * or when `settings.tabletLayoutMode == TabletLayoutMode.ALWAYS_TABLET`.
 */
@Composable
fun QuickStyleAndSettingsSheet(
    settings: LauncherSettings,
    previewApps: List<AppInfo> = emptyList(),
    iconCache: IconCache? = null,
    availableIconPacks: List<IconPackInfo> = emptyList(),
    backupStatusMessage: String? = null,
    onExportJsonString: (callback: (String) -> Unit) -> Unit = {},
    onImportJsonString: (String) -> Unit = {},
    onExportToFileSaf: () -> Unit = {},
    onImportFromFileSaf: () -> Unit = {},
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeSection = SettingsSectionTab.entries[selectedTab]
    var showPerAppEditorDialog by remember { mutableStateOf(false) }
    var showHiddenAppsDialog by remember { mutableStateOf(false) }
    var showWelcomeDialogInSettings by remember { mutableStateOf(false) }
    var jsonInspectorText by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .testTag("quick_style_settings_sheet")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop, bottom = navBarBottom)
            ) {
                val configuration = LocalConfiguration.current
                val isLargeScreenConfig =
                    (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
                val isTabletSplitLayout = configuration.smallestScreenWidthDp >= 600 ||
                    isLargeScreenConfig ||
                    maxWidth >= 600.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    // Top App Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Home"
                                )
                            }
                            Column {
                                Text(
                                    text = "Customization & Home Settings",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "App Colors · Squircle Engine · Gestures · Universal Android",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(onClick = onResetDefaults) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    HorizontalDivider()

                    if (isTabletSplitLayout) {
                        // Canonical Tablet 2-Pane Split Layout:
                        // Left Pane: Sticky Live Home Preview + Vertical Category Rail
                        // Right Pane: Scrollable Settings Controls
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .width(260.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                if (iconCache != null && previewApps.isNotEmpty()) {
                                    LiveHomePreviewBanner(
                                        settings = settings,
                                        previewApps = previewApps,
                                        iconCache = iconCache
                                    )
                                }
                                Text(
                                    text = "Categories",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                                SettingsSectionTab.entries.forEachIndexed { idx, tab ->
                                    FilterChip(
                                        selected = selectedTab == idx,
                                        onClick = { selectedTab = idx },
                                        label = { Text(tab.title) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            VerticalDivider()

                            SettingsScrollableContent(
                                activeSection = activeSection,
                                settings = settings,
                                availableIconPacks = availableIconPacks,
                                backupStatusMessage = backupStatusMessage,
                                onUpdateSettings = onUpdateSettings,
                                onOpenPerAppEditor = { showPerAppEditorDialog = true },
                                onOpenHiddenAppsEditor = { showHiddenAppsDialog = true },
                                onOpenWelcomeScreen = { showWelcomeDialogInSettings = true },
                                onExportToFileSaf = onExportToFileSaf,
                                onImportFromFileSaf = onImportFromFileSaf,
                                onOpenJsonInspector = {
                                    onExportJsonString { exported -> jsonInspectorText = exported }
                                },
                                onResetDefaults = onResetDefaults,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // Compact Phone Layout: Sticky Preview Banner + Horizontal Chip Bar + Scrollable Controls
                        if (iconCache != null && previewApps.isNotEmpty()) {
                            LiveHomePreviewBanner(
                                settings = settings,
                                previewApps = previewApps,
                                iconCache = iconCache,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            SettingsSectionTab.entries.forEachIndexed { idx, tab ->
                                FilterChip(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    label = { Text(tab.title) }
                                )
                            }
                        }

                        HorizontalDivider()

                        SettingsScrollableContent(
                            activeSection = activeSection,
                            settings = settings,
                            availableIconPacks = availableIconPacks,
                            backupStatusMessage = backupStatusMessage,
                            onUpdateSettings = onUpdateSettings,
                            onOpenPerAppEditor = { showPerAppEditorDialog = true },
                            onOpenHiddenAppsEditor = { showHiddenAppsDialog = true },
                            onOpenWelcomeScreen = { showWelcomeDialogInSettings = true },
                            onExportToFileSaf = onExportToFileSaf,
                            onImportFromFileSaf = onImportFromFileSaf,
                            onOpenJsonInspector = {
                                onExportJsonString { exported -> jsonInspectorText = exported }
                            },
                            onResetDefaults = onResetDefaults,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Per-App Custom Icon Color, Glyph & Name Override Dialog
    if (showPerAppEditorDialog) {
        PerAppOverrideEditorDialog(
            apps = previewApps,
            iconCache = iconCache,
            settings = settings,
            currentOverrides = settings.customAppOverrides,
            onSaveOverride = { key, customLabel, customPlateHex, customGlyphHex ->
                onUpdateSettings { current ->
                    val nextMap = current.customAppOverrides.toMutableMap()
                    if (customLabel.isNullOrBlank() && customPlateHex.isNullOrBlank() && customGlyphHex.isNullOrBlank()) {
                        nextMap.remove(key)
                    } else {
                        nextMap[key] = CustomAppOverride(
                            componentKey = key,
                            customLabel = customLabel?.trim()?.takeIf { it.isNotEmpty() },
                            customIconColorHex = customPlateHex?.takeIf { it.isNotEmpty() },
                            customGlyphColorHex = customGlyphHex?.takeIf { it.isNotEmpty() }
                        )
                    }
                    current.copy(customAppOverrides = nextMap)
                }
            },
            onDismiss = { showPerAppEditorDialog = false }
        )
    }

    // Hidden Apps Checklist Dialog
    if (showHiddenAppsDialog) {
        HiddenAppsSelectorDialog(
            apps = previewApps,
            hiddenKeys = settings.hiddenAppKeys,
            onToggleHidden = { key ->
                onUpdateSettings { current ->
                    val next = if (key in current.hiddenAppKeys) {
                        current.hiddenAppKeys - key
                    } else {
                        current.hiddenAppKeys + key
                    }
                    current.copy(hiddenAppKeys = next)
                }
            },
            onDismiss = { showHiddenAppsDialog = false }
        )
    }

    // In-App JSON Backup Inspector & Restore Modal
    jsonInspectorText?.let { currentJson ->
        AlertDialog(
            onDismissRequest = { jsonInspectorText = null },
            title = { Text("JSON Backup & Restore (Layout + Settings)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copy this JSON to back up your layout and settings, or edit/paste a JSON backup below and tap Restore.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = currentJson,
                        onValueChange = { jsonInspectorText = it },
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportJsonString(currentJson)
                        jsonInspectorText = null
                    }
                ) {
                    Text("Restore JSON")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(currentJson))
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = { jsonInspectorText = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsScrollableContent(
    activeSection: SettingsSectionTab,
    settings: LauncherSettings,
    availableIconPacks: List<IconPackInfo>,
    backupStatusMessage: String?,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onOpenPerAppEditor: () -> Unit,
    onOpenHiddenAppsEditor: () -> Unit,
    onOpenWelcomeScreen: () -> Unit,
    onExportToFileSaf: () -> Unit,
    onImportFromFileSaf: () -> Unit,
    onOpenJsonInspector: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // =====================================================
        // SECTION A: APP COLORS STUDIO (Global & Per-App Color Engine)
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.APP_COLORS) {
            SectionHeader("App Color Studio (Global & Per-App Colors)")

            Text("App Icon Color Mode", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AppColorPaletteMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.appColorPaletteMode == mode,
                        onClick = { onUpdateSettings { it.copy(appColorPaletteMode = mode) } },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            // 1-Tap Curated App Color Themes
            Text("1-Tap App Color Themes", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                curatedAppColorThemes.forEach { theme ->
                    val isSelected = settings.appColorPaletteMode == AppColorPaletteMode.CUSTOM_PALETTE &&
                        settings.globalAppPlateColorHex.equals(theme.plateHex, ignoreCase = true) &&
                        settings.globalAppGlyphColorHex.equals(theme.glyphHex, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = parseHexColorSafe(theme.plateHex),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .clickable {
                                onUpdateSettings {
                                    it.copy(
                                        appColorPaletteMode = AppColorPaletteMode.CUSTOM_PALETTE,
                                        globalAppPlateColorHex = theme.plateHex,
                                        globalAppGlyphColorHex = theme.glyphHex,
                                        accentColorHex = theme.glyphHex
                                    )
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColorSafe(theme.glyphHex))
                            )
                            Text(
                                text = theme.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = parseHexColorSafe(theme.glyphHex)
                            )
                        }
                    }
                }
            }

            ColorSwatchesRow(
                title = "Global App Icon Background Plate Color",
                colors = listOf(
                    "#1B2234", "#0D111A", "#10243E", "#0F291E",
                    "#2D1418", "#211536", "#F3F5FA", "#4285F4"
                ),
                selectedHex = settings.globalAppPlateColorHex,
                onSelectHex = { hex ->
                    onUpdateSettings {
                        it.copy(
                            appColorPaletteMode = AppColorPaletteMode.CUSTOM_PALETTE,
                            globalAppPlateColorHex = hex
                        )
                    }
                }
            )

            ColorSwatchesRow(
                title = "Global App Icon Foreground Glyph Color",
                colors = listOf(
                    "#7AB8FF", "#00E5FF", "#81C995", "#FDD663",
                    "#F28B82", "#C58AF9", "#FFFFFF", "#181C24"
                ),
                selectedHex = settings.globalAppGlyphColorHex,
                onSelectHex = { hex ->
                    onUpdateSettings {
                        it.copy(
                            appColorPaletteMode = AppColorPaletteMode.CUSTOM_PALETTE,
                            globalAppGlyphColorHex = hex
                        )
                    }
                }
            )

            if (settings.appColorPaletteMode == AppColorPaletteMode.ACCENT_HARMONIZED) {
                LabeledSlider(
                    title = "Accent Color Harmony Intensity",
                    valueText = "${(settings.appColorHarmonyIntensity * 100).toInt()}%",
                    value = settings.appColorHarmonyIntensity,
                    valueRange = 0.20f..0.95f,
                    onValueChange = { intensity ->
                        onUpdateSettings { it.copy(appColorHarmonyIntensity = intensity) }
                    }
                )
            }

            Button(
                onClick = onOpenPerAppEditor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_per_app_color_editor_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ColorLens,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Customize Individual App Colors & Names (${settings.customAppOverrides.size} active)")
            }

            HorizontalDivider()
        }

        // =====================================================
        // SECTION B: TABLET, FOLDABLE & UNIVERSAL ANDROID SUPPORT
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.TABLET_UNIVERSAL) {
            SectionHeader("Tablet, Foldable & Universal Android Support")

            val configuration = LocalConfiguration.current
            val isLargeScreenConfig =
                (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
            val isAutoDetectedTablet = configuration.smallestScreenWidthDp >= 600 ||
                isLargeScreenConfig ||
                configuration.screenWidthDp >= 600

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TabletAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (isAutoDetectedTablet) {
                                "Auto-Detected Device: Tablet / Large Screen (${configuration.screenWidthDp}×${configuration.screenHeightDp}dp)"
                            } else {
                                "Auto-Detected Device: Phone Handheld (${configuration.screenWidthDp}×${configuration.screenHeightDp}dp)"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Tablet Dual-Pane Workspace and Floating Taskbar activate automatically when running on a tablet or unfolded foldable (≥ 600dp).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            SettingToggleRow(
                title = "Tablet Dual-Pane Workspace Spread",
                subtitle = "Show At-a-Glance + Widgets Dashboard alongside the App Grid in Tablet Mode",
                checked = settings.tabletDualPaneWorkspace,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(tabletDualPaneWorkspace = v) }
                }
            )

            SettingToggleRow(
                title = "Pixel Tablet Floating Taskbar Dock",
                subtitle = "Floating bottom taskbar with All-Apps button, Color Studio, pinned & recent apps",
                checked = settings.tabletTaskbarStyle,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(tabletTaskbarStyle = v) }
                }
            )

            Text("Tablet Grid Columns × Rows", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(5 to 4, 6 to 5, 6 to 6, 7 to 5, 8 to 6).forEach { (tc, tr) ->
                    FilterChip(
                        selected = settings.tabletGridColumns == tc && settings.tabletGridRows == tr,
                        onClick = {
                            onUpdateSettings { it.copy(tabletGridColumns = tc, tabletGridRows = tr) }
                        },
                        label = { Text("${tc}×$tr Tablet Grid") }
                    )
                }
            }

            Text("Universal Android & OEM Compatibility Profile (API 24–36)", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                OemCompatibilityProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = settings.oemCompatibilityProfile == profile,
                        onClick = {
                            onUpdateSettings { current ->
                                when (profile) {
                                    OemCompatibilityProfile.SAMSUNG_ONE_UI -> current.copy(
                                        oemCompatibilityProfile = profile,
                                        iconShape = IconShapePreset.SQUIRCLE,
                                        squircleExponent = 3.6f
                                    )
                                    OemCompatibilityProfile.XIAOMI_HYPER_OS -> current.copy(
                                        oemCompatibilityProfile = profile,
                                        iconShape = IconShapePreset.ROUNDED_SQUARE,
                                        squircleExponent = 4.6f
                                    )
                                    OemCompatibilityProfile.NOTHING_OS -> current.copy(
                                        oemCompatibilityProfile = profile,
                                        iconShape = IconShapePreset.CIRCLE,
                                        fontFamily = LauncherFontFamily.MONO,
                                        appColorPaletteMode = AppColorPaletteMode.CUSTOM_PALETTE,
                                        globalAppPlateColorHex = "#14171F",
                                        globalAppGlyphColorHex = "#F1F4F9"
                                    )
                                    else -> current.copy(oemCompatibilityProfile = profile)
                                }
                            }
                        },
                        label = { Text(profile.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Universal Legacy Icon Auto-Normalizer",
                subtitle = "Automatically extract dominant brand colors & safe-zone plates for Android 7+ / non-adaptive OEM icons",
                checked = settings.universalLegacyIconNormalize,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(universalLegacyIconNormalize = v) }
                }
            )

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 1: ICONS & ICON PACKS
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.ICONS) {
            SectionHeader("1. Icons & Squircle Engine (|x|ⁿ + |y|ⁿ = 1)")

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                IconShapePreset.entries.forEach { preset ->
                    val selected = settings.iconShape == preset
                    val sampleShape = SquircleShape(
                        exponent = settings.squircleExponent,
                        preset = preset
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onUpdateSettings { it.copy(iconShape = preset) } }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    clip = true
                                    shape = sampleShape
                                }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preset.displayName.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LabeledSlider(
                title = "Superellipse Roundness (n)",
                valueText = String.format(Locale.US, "n = %.1f", settings.squircleExponent),
                value = settings.squircleExponent,
                valueRange = 2.0f..10.0f,
                testTag = "squircle_exponent_slider",
                onValueChange = { n -> onUpdateSettings { it.copy(squircleExponent = n) } }
            )

            LabeledSlider(
                title = "Icon Size Scale",
                valueText = "${(settings.iconSizeScale * 100).toInt()}%",
                value = settings.iconSizeScale,
                valueRange = 0.50f..1.30f,
                onValueChange = { s -> onUpdateSettings { it.copy(iconSizeScale = s) } }
            )

            SettingToggleRow(
                title = "Show Home Labels",
                subtitle = "Display app names underneath workspace icons",
                checked = settings.showHomeLabels,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showHomeLabels = v) } }
            )

            SettingToggleRow(
                title = "Show Drawer Labels",
                subtitle = "Display app names inside the App Drawer",
                checked = settings.showDrawerLabels,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showDrawerLabels = v) } }
            )

            LabeledSlider(
                title = "Label Font Size",
                valueText = "${settings.labelFontSizeSp.toInt()}sp",
                value = settings.labelFontSizeSp,
                valueRange = 10f..16f,
                onValueChange = { sp -> onUpdateSettings { it.copy(labelFontSizeSp = sp) } }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(1, 2).forEach { lines ->
                    FilterChip(
                        selected = settings.labelMaxLines == lines,
                        onClick = { onUpdateSettings { it.copy(labelMaxLines = lines) } },
                        label = { Text("$lines Label Line${if (lines > 1) "s" else ""}") }
                    )
                }
            }

            Text("Label Color Mode", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                LabelColorMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.labelColorMode == mode,
                        onClick = { onUpdateSettings { it.copy(labelColorMode = mode) } },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Icon Drop Shadow",
                subtitle = "Subtle elevation shadow underneath squircle icons",
                checked = settings.iconShadowEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(iconShadowEnabled = v) } }
            )

            SettingToggleRow(
                title = "Themed Icons on Home (Android 13+)",
                subtitle = "Tint home screen icons with Material You monochrome colors",
                checked = settings.themedIconsEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(themedIconsEnabled = v) } }
            )

            SettingToggleRow(
                title = "Themed Icons in App Drawer",
                subtitle = "Extend Material You monochrome tinting to the App Drawer",
                checked = settings.themedIconsInDrawer,
                onCheckedChange = { v -> onUpdateSettings { it.copy(themedIconsInDrawer = v) } }
            )

            if (availableIconPacks.isNotEmpty()) {
                Text("Icon Pack (ADW / Nova / Studio)", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    availableIconPacks.forEach { pack ->
                        FilterChip(
                            selected = settings.iconPackPackage == pack.packageName,
                            onClick = {
                                onUpdateSettings { it.copy(iconPackPackage = pack.packageName) }
                            },
                            label = { Text(pack.label) }
                        )
                    }
                }
            }

            ColorSwatchesRow(
                title = "Legacy Icon Safe-Zone Plate Color",
                colors = listOf("#F3F5FA", "#1E222B", "#E8F0FE", "#FCE8E6", "#E6F4EA"),
                selectedHex = settings.legacyIconPlateHex,
                onSelectHex = { hex -> onUpdateSettings { it.copy(legacyIconPlateHex = hex) } }
            )

            OutlinedButton(
                onClick = onOpenPerAppEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Per-App Custom Color & Name (${settings.customAppOverrides.size} customized)")
            }

            Text("Notification Badge Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BadgeStyle.entries.forEach { badge ->
                    FilterChip(
                        selected = settings.badgeStyle == badge,
                        onClick = { onUpdateSettings { it.copy(badgeStyle = badge) } },
                        label = { Text(badge.displayName) }
                    )
                }
            }

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 2: HOME WORKSPACE
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.HOME) {
            SectionHeader("2. Home Grid & At a Glance")

            Text("Phone Grid Columns × Rows (4×4 to 7×7)", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(4 to 4, 4 to 5, 5 to 5, 5 to 6, 6 to 6, 7 to 7).forEach { (c, r) ->
                    FilterChip(
                        selected = settings.gridColumns == c && settings.gridRows == r,
                        onClick = {
                            onUpdateSettings { it.copy(gridColumns = c, gridRows = r) }
                        },
                        label = { Text("${c}×$r") }
                    )
                }
            }

            LabeledSlider(
                title = "Workspace Horizontal Padding",
                valueText = "${settings.gridHorizontalPaddingDp}dp",
                value = settings.gridHorizontalPaddingDp.toFloat(),
                valueRange = 6f..28f,
                onValueChange = { p ->
                    onUpdateSettings { it.copy(gridHorizontalPaddingDp = p.toInt()) }
                }
            )

            LabeledSlider(
                title = "Home Page Count",
                valueText = "${settings.homePageCount} Pages",
                value = settings.homePageCount.toFloat(),
                valueRange = 1f..5f,
                onValueChange = { c ->
                    onUpdateSettings { it.copy(homePageCount = c.toInt().coerceIn(1, 5)) }
                }
            )

            SettingToggleRow(
                title = "Infinite Pager Loop",
                subtitle = "Wrap continuously between last page and Page 1",
                checked = settings.infiniteScroll,
                onCheckedChange = { v -> onUpdateSettings { it.copy(infiniteScroll = v) } }
            )

            Text("Page Indicator Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PageIndicatorStyle.entries.forEach { ind ->
                    FilterChip(
                        selected = settings.pageIndicatorStyle == ind,
                        onClick = { onUpdateSettings { it.copy(pageIndicatorStyle = ind) } },
                        label = { Text(ind.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Lock Home Layout",
                subtitle = "Prevent accidental drag-and-drop movement or removal",
                checked = settings.lockHomeLayout,
                onCheckedChange = { v -> onUpdateSettings { it.copy(lockHomeLayout = v) } }
            )

            SettingToggleRow(
                title = "Auto-Add New Apps to Home",
                subtitle = "Place newly installed apps onto the next available workspace cell",
                checked = settings.autoAddNewApps,
                onCheckedChange = { v -> onUpdateSettings { it.copy(autoAddNewApps = v) } }
            )

            SettingToggleRow(
                title = "Show System Status Bar",
                subtitle = "Keep clock and battery icons visible at the top of the home screen",
                checked = settings.showStatusBar,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showStatusBar = v) } }
            )

            SettingToggleRow(
                title = "Wallpaper Parallax & 3D Icon Depth",
                subtitle = "Multi-layer depth shift while swiping between pages",
                checked = settings.wallpaperParallax,
                onCheckedChange = { v -> onUpdateSettings { it.copy(wallpaperParallax = v) } }
            )

            SettingToggleRow(
                title = "At a Glance Widget",
                subtitle = "Show Pixel date, calendar event & weather on Page 1",
                checked = settings.showAtAGlance,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showAtAGlance = v) } }
            )

            SettingToggleRow(
                title = "Show Weather in At a Glance",
                subtitle = "Display temperature pill beside the date",
                checked = settings.atAGlanceShowWeather,
                onCheckedChange = { v -> onUpdateSettings { it.copy(atAGlanceShowWeather = v) } }
            )

            Text("At a Glance Date Format", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf("EEE, MMM d", "EEEE, MMMM d", "MMM d, yyyy", "d MMM · EEE").forEach { fmt ->
                    FilterChip(
                        selected = settings.atAGlanceDateFormat == fmt,
                        onClick = { onUpdateSettings { it.copy(atAGlanceDateFormat = fmt) } },
                        label = { Text(fmt) }
                    )
                }
            }

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 3: SEARCH BAR
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.SEARCH) {
            SectionHeader("3. Search Bar")

            Text("Search Bar Position", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SearchBarPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = settings.searchBarPosition == pos,
                        onClick = { onUpdateSettings { it.copy(searchBarPosition = pos) } },
                        label = { Text(pos.displayName) }
                    )
                }
            }

            Text("Search Bar Shape Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchBarStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.searchBarStyle == style,
                        onClick = { onUpdateSettings { it.copy(searchBarStyle = style) } },
                        label = { Text(style.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "Search Bar Opacity",
                valueText = "${(settings.searchBarOpacity * 100).toInt()}%",
                value = settings.searchBarOpacity,
                valueRange = 0.35f..1.00f,
                onValueChange = { o -> onUpdateSettings { it.copy(searchBarOpacity = o) } }
            )

            LabeledSlider(
                title = "Search Bar Corner Radius",
                valueText = "${settings.searchBarCornerRadiusDp}dp",
                value = settings.searchBarCornerRadiusDp.toFloat(),
                valueRange = 8f..28f,
                onValueChange = { r ->
                    onUpdateSettings { it.copy(searchBarCornerRadiusDp = r.toInt()) }
                }
            )

            SettingToggleRow(
                title = "Show Voice Mic Icon",
                subtitle = "Quick voice search trigger in search bar",
                checked = settings.showMicIcon,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showMicIcon = v) } }
            )

            SettingToggleRow(
                title = "Show Visual Lens Icon",
                subtitle = "Quick visual camera search trigger in search bar",
                checked = settings.showLensIcon,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showLensIcon = v) } }
            )

            Text("Default Web Search Engine", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SearchEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.searchEngine == engine,
                        onClick = { onUpdateSettings { it.copy(searchEngine = engine) } },
                        label = { Text(engine.displayName) }
                    )
                }
            }

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 4: DOCK (HOTSEAT)
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.DOCK) {
            SectionHeader("4. Hotseat Dock")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                (3..6).forEach { count ->
                    FilterChip(
                        selected = settings.dockIconCount == count,
                        onClick = { onUpdateSettings { it.copy(dockIconCount = count) } },
                        label = { Text("$count Icons") }
                    )
                }
            }

            Text("Dock Background Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DockBackgroundStyle.entries.forEach { bg ->
                    FilterChip(
                        selected = settings.dockBackgroundStyle == bg,
                        onClick = { onUpdateSettings { it.copy(dockBackgroundStyle = bg) } },
                        label = { Text(bg.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "Dock Background Opacity",
                valueText = "${(settings.dockOpacity * 100).toInt()}%",
                value = settings.dockOpacity,
                valueRange = 0.30f..1.00f,
                onValueChange = { o -> onUpdateSettings { it.copy(dockOpacity = o) } }
            )

            SettingToggleRow(
                title = "Show Dock on All Pages",
                subtitle = "Keep hotseat pinned across all workspace pages",
                checked = settings.showDockOnAllPages,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showDockOnAllPages = v) } }
            )

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 5: APP DRAWER
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.DRAWER) {
            SectionHeader("5. App Drawer & Hidden Vault")

            Text("Drawer Layout Mode", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DrawerLayoutMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.drawerLayoutMode == mode,
                        onClick = { onUpdateSettings { it.copy(drawerLayoutMode = mode) } },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "Drawer Grid Columns",
                valueText = "${settings.drawerColumns} Columns",
                value = settings.drawerColumns.toFloat(),
                valueRange = 3f..7f,
                onValueChange = { c ->
                    onUpdateSettings { it.copy(drawerColumns = c.toInt().coerceIn(3, 7)) }
                }
            )

            Text("Drawer Background Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DrawerBackgroundStyle.entries.forEach { bg ->
                    FilterChip(
                        selected = settings.drawerBackgroundStyle == bg,
                        onClick = { onUpdateSettings { it.copy(drawerBackgroundStyle = bg) } },
                        label = { Text(bg.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "Drawer Background Opacity",
                valueText = "${(settings.drawerBackgroundOpacity * 100).toInt()}%",
                value = settings.drawerBackgroundOpacity,
                valueRange = 0.60f..1.00f,
                onValueChange = { o -> onUpdateSettings { it.copy(drawerBackgroundOpacity = o) } }
            )

            Text("Drawer Search Bar Position", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DrawerSearchPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = settings.drawerSearchPosition == pos,
                        onClick = { onUpdateSettings { it.copy(drawerSearchPosition = pos) } },
                        label = { Text(pos.displayName) }
                    )
                }
            }

            Text("Drawer Sort Order", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DrawerSortMode.entries.forEach { sort ->
                    FilterChip(
                        selected = settings.drawerSortMode == sort,
                        onClick = { onUpdateSettings { it.copy(drawerSortMode = sort) } },
                        label = { Text(sort.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Suggested Apps Row",
                subtitle = "Show top apps ranked from launch history at top of drawer",
                checked = settings.showSuggestedAppsRow,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showSuggestedAppsRow = v) } }
            )

            LabeledSlider(
                title = "Suggested Apps Count",
                valueText = "${settings.suggestedAppsCount} Apps",
                value = settings.suggestedAppsCount.toFloat(),
                valueRange = 4f..8f,
                onValueChange = { c ->
                    onUpdateSettings { it.copy(suggestedAppsCount = c.toInt().coerceIn(4, 8)) }
                }
            )

            SettingToggleRow(
                title = "A–Z Right-Edge Fast Scroller",
                subtitle = "Quickly scrub through alphabetical sections with haptics",
                checked = settings.showAzFastScroller,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showAzFastScroller = v) } }
            )

            SettingToggleRow(
                title = "App Category Tabs",
                subtitle = "Show Social, Productivity, Media, Tools & Games filter chips",
                checked = settings.showDrawerCategoryTabs,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showDrawerCategoryTabs = v) } }
            )

            SettingToggleRow(
                title = "Work Profile Tab",
                subtitle = "Separate Personal and Work profile apps in the drawer",
                checked = settings.showWorkProfileTab,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showWorkProfileTab = v) } }
            )

            SettingToggleRow(
                title = "Auto-Open Keyboard on Drawer Swipe",
                subtitle = "Immediately focus the search field when opening the drawer",
                checked = settings.autoOpenDrawerKeyboard,
                onCheckedChange = { v -> onUpdateSettings { it.copy(autoOpenDrawerKeyboard = v) } }
            )

            OutlinedButton(
                onClick = onOpenHiddenAppsEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Hidden Apps (${settings.hiddenAppKeys.size} hidden)")
            }

            SettingToggleRow(
                title = "Biometric Lock for Hidden Apps Vault",
                subtitle = "Require authentication before revealing hidden apps in the drawer",
                checked = settings.biometricLockForHiddenApps,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(biometricLockForHiddenApps = v) }
                }
            )

            Text("Universal Search Sources", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = settings.searchApps,
                    onClick = { onUpdateSettings { it.copy(searchApps = !it.searchApps) } },
                    label = { Text("Apps") }
                )
                FilterChip(
                    selected = settings.searchShortcuts,
                    onClick = { onUpdateSettings { it.copy(searchShortcuts = !it.searchShortcuts) } },
                    label = { Text("Shortcuts") }
                )
                FilterChip(
                    selected = settings.searchSettings,
                    onClick = { onUpdateSettings { it.copy(searchSettings = !it.searchSettings) } },
                    label = { Text("Settings") }
                )
                FilterChip(
                    selected = settings.searchWeb,
                    onClick = { onUpdateSettings { it.copy(searchWeb = !it.searchWeb) } },
                    label = { Text("Web") }
                )
            }

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 6: FOLDERS
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.FOLDERS) {
            SectionHeader("6. Folders")

            Text("Folder Icon Preview Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FolderPreviewStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.folderPreviewStyle == style,
                        onClick = { onUpdateSettings { it.copy(folderPreviewStyle = style) } },
                        label = { Text(style.displayName) }
                    )
                }
            }

            ColorSwatchesRow(
                title = "Folder Panel Background Color",
                colors = listOf("#1F2430", "#121824", "#2B2338", "#1A2E2B", "#332222"),
                selectedHex = settings.folderBackgroundColorHex,
                onSelectHex = { hex ->
                    onUpdateSettings { it.copy(folderBackgroundColorHex = hex) }
                }
            )

            LabeledSlider(
                title = "Folder Background Opacity",
                valueText = "${(settings.folderBackgroundOpacity * 100).toInt()}%",
                value = settings.folderBackgroundOpacity,
                valueRange = 0.40f..1.00f,
                onValueChange = { o ->
                    onUpdateSettings { it.copy(folderBackgroundOpacity = o) }
                }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(3, 4).forEach { cols ->
                    FilterChip(
                        selected = settings.folderColumns == cols,
                        onClick = { onUpdateSettings { it.copy(folderColumns = cols) } },
                        label = { Text("$cols Folder Columns") }
                    )
                }
            }

            SettingToggleRow(
                title = "Show Folder Titles",
                subtitle = "Display folder name under icon and inside expanded panel",
                checked = settings.showFolderTitles,
                onCheckedChange = { v -> onUpdateSettings { it.copy(showFolderTitles = v) } }
            )

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 7: ANIMATIONS & GESTURES
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.ANIMATIONS) {
            SectionHeader("7. Spring Physics & Gestures")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AnimationPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.animationPreset == preset,
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    animationPreset = preset,
                                    springDampingRatio = preset.defaultDamping,
                                    springStiffness = preset.defaultStiffness,
                                    animationSpeedMultiplier = preset.speedMultiplier
                                )
                            }
                        },
                        label = { Text(preset.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "Animation Speed Multiplier",
                valueText = String.format(Locale.US, "%.2f×", settings.animationSpeedMultiplier),
                value = settings.animationSpeedMultiplier,
                valueRange = 0.50f..2.00f,
                testTag = "animation_speed_slider",
                onValueChange = { s ->
                    onUpdateSettings { it.copy(animationSpeedMultiplier = s) }
                }
            )

            LabeledSlider(
                title = "Spring Damping Ratio (~0.80 default)",
                valueText = String.format(Locale.US, "%.2f", settings.springDampingRatio),
                value = settings.springDampingRatio,
                valueRange = 0.50f..1.00f,
                testTag = "spring_damping_slider",
                onValueChange = { d -> onUpdateSettings { it.copy(springDampingRatio = d) } }
            )

            LabeledSlider(
                title = "Spring Stiffness",
                valueText = "${settings.springStiffness.toInt()}",
                value = settings.springStiffness,
                valueRange = 150f..1000f,
                testTag = "spring_stiffness_slider",
                onValueChange = { st -> onUpdateSettings { it.copy(springStiffness = st) } }
            )

            Text("App-Open & Close Window Morph Style", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AppOpenAnimationStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.appOpenAnimationStyle == style,
                        onClick = { onUpdateSettings { it.copy(appOpenAnimationStyle = style) } },
                        label = { Text(style.displayName) }
                    )
                }
            }

            Text("Haptic Feedback Strength", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                HapticStrength.entries.forEach { h ->
                    FilterChip(
                        selected = settings.hapticStrength == h,
                        onClick = { onUpdateSettings { it.copy(hapticStrength = h) } },
                        label = { Text(h.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Respect System 'Remove Animations'",
                subtitle = "Snap transitions immediately when system accessibility scale is 0",
                checked = settings.respectSystemAnimatorScale,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(respectSystemAnimatorScale = v) }
                }
            )

            GestureMappingRow(
                title = "Swipe Up Gesture",
                selectedAction = settings.swipeUpAction,
                onSelectAction = { a -> onUpdateSettings { it.copy(swipeUpAction = a) } }
            )

            GestureMappingRow(
                title = "Swipe Down Gesture",
                selectedAction = settings.swipeDownAction,
                onSelectAction = { a -> onUpdateSettings { it.copy(swipeDownAction = a) } }
            )

            GestureMappingRow(
                title = "Double-Tap Home Space",
                selectedAction = settings.doubleTapAction,
                onSelectAction = { a -> onUpdateSettings { it.copy(doubleTapAction = a) } }
            )

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 8: THEME & TYPOGRAPHY
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.THEME) {
            SectionHeader("8. Material You Theme & Typography")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            SettingToggleRow(
                title = "Material You Dynamic Colors (Android 12+)",
                subtitle = "Derive system color scheme from active wallpaper",
                checked = settings.dynamicColorsEnabled,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(dynamicColorsEnabled = v) }
                }
            )

            ColorSwatchesRow(
                title = "Custom Accent Seed Color (when Dynamic Color is off)",
                colors = listOf("#7AB8FF", "#81C995", "#F28B82", "#FDD663", "#C58AF9", "#FF8BCB"),
                selectedHex = settings.accentColorHex,
                onSelectHex = { hex ->
                    onUpdateSettings {
                        it.copy(
                            accentColorHex = hex,
                            dynamicColorsEnabled = false
                        )
                    }
                }
            )

            Text("Launcher Font Family", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                LauncherFontFamily.entries.forEach { font ->
                    FilterChip(
                        selected = settings.fontFamily == font,
                        onClick = { onUpdateSettings { it.copy(fontFamily = font) } },
                        label = { Text(font.displayName) }
                    )
                }
            }

            LabeledSlider(
                title = "UI Corner Radius Scale",
                valueText = String.format(Locale.US, "%.2f×", settings.uiCornerRadiusScale),
                value = settings.uiCornerRadiusScale,
                valueRange = 0.50f..1.50f,
                onValueChange = { s -> onUpdateSettings { it.copy(uiCornerRadiusScale = s) } }
            )

            HorizontalDivider()
        }

        // =====================================================
        // SECTION 9: SYSTEM, BACKUP & RESTORE
        // =====================================================
        if (activeSection == SettingsSectionTab.ALL || activeSection == SettingsSectionTab.BACKUP) {
            SectionHeader("9. Backup, Restore & Reset (JSON)")

            if (!backupStatusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = backupStatusMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onExportToFileSaf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_backup_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export File")
                }

                OutlinedButton(
                    onClick = onImportFromFileSaf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_backup_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore File")
                }
            }

            OutlinedButton(
                onClick = onOpenJsonInspector,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_json_backup_inspector_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View / Copy / Paste JSON Backup Directly")
            }

            OutlinedButton(
                onClick = onResetDefaults,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_defaults_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Settings & Home Layout to Defaults")
            }

            Button(
                onClick = onOpenWelcomeScreen,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_welcome_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Pixel Welcome & Feature Guide 🌸")
            }
        }
    }
}

/**
 * Sticky live preview card at the top of the Settings screen showing real [SquircleAppIcon] and
 * [SquircleFolderTile] instances reacting immediately to every setting adjustment.
 */
@Composable
private fun LiveHomePreviewBanner(
    settings: LauncherSettings,
    previewApps: List<AppInfo>,
    iconCache: IconCache,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_live_preview_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF101726),
                            Color(0xFF1E2942),
                            Color(0xFF151C2C)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                previewApps.take(3).forEachIndexed { idx, app ->
                    SquircleAppIcon(
                        app = app.copy(notificationCount = if (idx == 0) 3 else 0),
                        iconCache = iconCache,
                        settings = settings,
                        showLabel = settings.showHomeLabels,
                        useThemedIcon = settings.themedIconsEnabled,
                        baseIconSize = 48.dp,
                        onClick = { _, _ -> }
                    )
                }
                SquircleFolderTile(
                    title = "Preview",
                    memberApps = previewApps.take(4),
                    iconCache = iconCache,
                    settings = settings,
                    baseIconSize = 48.dp,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    testTag: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun ColorSwatchesRow(
    title: String,
    colors: List<String>,
    selectedHex: String,
    onSelectHex: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            colors.forEach { hex ->
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                val color = parseHexColorSafe(hex)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onSelectHex(hex) }
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (hex == "#FFFFFF" || hex == "#F3F5FA" || hex == "#FDD663") Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureMappingRow(
    title: String,
    selectedAction: LauncherGestureAction,
    onSelectAction: (LauncherGestureAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = selectedAction.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            LauncherGestureAction.entries.forEach { action ->
                FilterChip(
                    selected = selectedAction == action,
                    onClick = { onSelectAction(action) },
                    label = { Text(action.displayName) }
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PerAppOverrideEditorDialog(
    apps: List<AppInfo>,
    iconCache: IconCache?,
    settings: LauncherSettings,
    currentOverrides: Map<String, CustomAppOverride>,
    onSaveOverride: (String, String?, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf(apps.firstOrNull()) }
    var customName by remember(selectedApp) {
        mutableStateOf(
            selectedApp?.let { currentOverrides[it.componentKey]?.customLabel } ?: ""
        )
    }
    var customPlateHex by remember(selectedApp) {
        mutableStateOf(
            selectedApp?.let { currentOverrides[it.componentKey]?.customIconColorHex } ?: ""
        )
    }
    var customGlyphHex by remember(selectedApp) {
        mutableStateOf(
            selectedApp?.let { currentOverrides[it.componentKey]?.customGlyphColorHex } ?: ""
        )
    }

    val previewSettings = remember(settings, selectedApp, customName, customPlateHex, customGlyphHex) {
        val app = selectedApp ?: return@remember settings
        val map = settings.customAppOverrides.toMutableMap()
        map[app.componentKey] = CustomAppOverride(
            componentKey = app.componentKey,
            customLabel = customName.takeIf { it.isNotBlank() },
            customIconColorHex = customPlateHex.takeIf { it.isNotBlank() },
            customGlyphColorHex = customGlyphHex.takeIf { it.isNotBlank() }
        )
        settings.copy(customAppOverrides = map)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Per-App Custom Colors & Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (iconCache != null && selectedApp != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF121826),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            SquircleAppIcon(
                                app = selectedApp!!,
                                iconCache = iconCache,
                                settings = previewSettings,
                                showLabel = true,
                                useThemedIcon = settings.themedIconsEnabled,
                                baseIconSize = 60.dp,
                                onClick = { _, _ -> }
                            )
                        }
                    }
                }
                Text(
                    text = "Select an app to customize its label, squircle background plate color, and foreground glyph tint:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    apps.take(18).forEach { app ->
                        FilterChip(
                            selected = selectedApp?.componentKey == app.componentKey,
                            onClick = { selectedApp = app },
                            label = { Text(app.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom App Label (leave blank for default)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorSwatchesRow(
                    title = "Custom Icon Background Plate Color",
                    colors = listOf(
                        "#4285F4", "#EA4335", "#34A853", "#FBBC04",
                        "#9334E6", "#00ACC1", "#1B2234", "#0F172A"
                    ),
                    selectedHex = customPlateHex,
                    onSelectHex = { customPlateHex = it }
                )
                ColorSwatchesRow(
                    title = "Custom Icon Foreground Glyph Color",
                    colors = listOf(
                        "#FFFFFF", "#7AB8FF", "#81C995", "#FDD663",
                        "#F28B82", "#C58AF9", "#00E5FF", "#181C24"
                    ),
                    selectedHex = customGlyphHex,
                    onSelectHex = { customGlyphHex = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedApp?.let { app ->
                        onSaveOverride(app.componentKey, customName, customPlateHex, customGlyphHex)
                    }
                    onDismiss()
                }
            ) {
                Text("Save Override")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        selectedApp?.let { app ->
                            onSaveOverride(app.componentKey, null, null, null)
                        }
                        onDismiss()
                    }
                ) {
                    Text("Reset App")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun HiddenAppsSelectorDialog(
    apps: List<AppInfo>,
    hiddenKeys: Set<String>,
    onToggleHidden: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden Apps Vault") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(apps.filter { !it.isWorkProfile }, key = { it.componentKey }) { app ->
                    val isHidden = app.componentKey in hiddenKeys
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleHidden(app.componentKey) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = isHidden,
                            onCheckedChange = { onToggleHidden(app.componentKey) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
