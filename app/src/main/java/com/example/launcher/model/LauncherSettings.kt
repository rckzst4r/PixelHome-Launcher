package com.example.launcher.model

import com.squareup.moshi.JsonClass

enum class IconShapePreset(val displayName: String) {
    SQUIRCLE("Squircle (Superellipse)"),
    CIRCLE("Circle"),
    ROUNDED_SQUARE("Rounded Square"),
    SQUARE("Square"),
    TEARDROP("Teardrop"),
    FLOWER("Pixel Scallop / Flower"),
    HEXAGON("Hexagon"),
    PEBBLE("Pebble")
}

enum class AppColorPaletteMode(val displayName: String) {
    ORIGINAL("Original App Colors"),
    CUSTOM_PALETTE("Custom App Color Palette"),
    ACCENT_HARMONIZED("Accent-Harmonized Tint"),
    MATERIAL_YOU("Material You Monochrome")
}

enum class TabletLayoutMode(val displayName: String) {
    AUTO("Auto (Detect Tablet / Foldable)"),
    ALWAYS_TABLET("Force Tablet & Taskbar Mode"),
    PHONE_PORTRAIT("Force Phone Portrait Mode")
}

enum class OemCompatibilityProfile(val displayName: String) {
    AUTO_UNIVERSAL("Universal Auto (All Androids)"),
    PIXEL_STOCK("Google Pixel Stock"),
    SAMSUNG_ONE_UI("Samsung One UI"),
    XIAOMI_HYPER_OS("Xiaomi HyperOS / MIUI"),
    NOTHING_OS("Nothing OS"),
    LEGACY_ANDROID_7("Legacy Android 7+ Safe Mode")
}

enum class LabelColorMode(val displayName: String) {
    WALLPAPER_ADAPTIVE("Wallpaper Adaptive"),
    LIGHT("Always Light"),
    DARK("Always Dark"),
    DYNAMIC_ACCENT("Material You Accent")
}

enum class BadgeStyle(val displayName: String) {
    DOT("Notification Dot"),
    COUNT("Number Count"),
    OFF("Hidden")
}

enum class PageIndicatorStyle(val displayName: String) {
    DOTS("Pixel Dots"),
    LINE("Progress Pill"),
    NONE("Hidden")
}

enum class SearchBarPosition(val displayName: String) {
    BOTTOM("Bottom (Under Dock)"),
    TOP("Top (Below At a Glance)"),
    HIDDEN("Hidden")
}

enum class SearchBarStyle(val displayName: String) {
    PILL("Pixel Pill"),
    ROUNDED("Rounded Card"),
    FLAT("Minimal Flat")
}

enum class SearchEngine(val displayName: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/dsearch?query=")
}

enum class DockBackgroundStyle(val displayName: String) {
    NONE("Transparent"),
    TRANSLUCENT_PILL("Translucent Pill"),
    SOLID("Material Surface")
}

enum class DrawerLayoutMode(val displayName: String) {
    VERTICAL("Vertical Grid"),
    PAGED("Horizontal Pages"),
    LIST("Compact List")
}

enum class DrawerBackgroundStyle(val displayName: String) {
    BLUR("Frosted Blur + Scrim"),
    DYNAMIC("Material You Surface"),
    SOLID("Solid Dark/Light")
}

enum class DrawerSearchPosition(val displayName: String) {
    TOP("Top Bar"),
    BOTTOM("Bottom Thumb Zone"),
    HIDDEN("Hidden")
}

enum class DrawerSortMode(val displayName: String) {
    A_TO_Z("Alphabetical (A–Z)"),
    MOST_USED("Most Used"),
    RECENT("Recently Installed")
}

enum class FolderPreviewStyle(val displayName: String) {
    GRID_2X2("2×2 Grid"),
    GRID_3X3("3×3 Mini Grid"),
    STACK("Diagonal Stack")
}

enum class AnimationPreset(
    val displayName: String,
    val defaultDamping: Float,
    val defaultStiffness: Float,
    val speedMultiplier: Float
) {
    PIXEL("Pixel Silky", 0.80f, 380f, 1.0f),
    SNAPPY("Snappy", 0.86f, 620f, 1.25f),
    SMOOTH("Butter Smooth", 0.75f, 260f, 0.85f),
    MINIMAL("Minimal", 0.95f, 900f, 1.6f),
    OFF("Instant (No Motion)", 1.0f, 10000f, 2.0f)
}

enum class AppOpenAnimationStyle(val displayName: String) {
    ICON_ZOOM("Pixel Icon Expand"),
    FADE("Scale & Fade"),
    SLIDE("Vertical Slide")
}

enum class HapticStrength(val displayName: String) {
    OFF("Off"),
    LIGHT("Subtle Tick"),
    MEDIUM("Pixel Crisp"),
    STRONG("Firm")
}

enum class ThemeMode(val displayName: String) {
    AUTO("System Auto"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class LauncherFontFamily(val displayName: String) {
    ROBOTO_FLEX("Google Sans / Roboto Flex"),
    PLUS_JAKARTA("Plus Jakarta Sans"),
    SPACE_GROTESK("Space Grotesk"),
    MONO("JetBrains Mono")
}

enum class LauncherGestureAction(val displayName: String) {
    DRAWER("Open App Drawer"),
    NOTIFICATIONS("Expand Notifications"),
    QUICK_SETTINGS("Quick Settings"),
    SEARCH("Universal Search"),
    LOCK_SCREEN("Lock Screen"),
    OPEN_APP("Open Favorite App"),
    NOTHING("Do Nothing")
}

@JsonClass(generateAdapter = true)
data class CustomAppOverride(
    val componentKey: String,
    val customLabel: String? = null,
    val customIconColorHex: String? = null,
    val customGlyphColorHex: String? = null
)

/**
 * Single comprehensive settings model covering all PixelHome customization options,
 * including App Color Studio, Tablet & Foldable Adaptive Mode, and Universal Android Support.
 */
@JsonClass(generateAdapter = true)
data class LauncherSettings(
    // 1. Icons & App Colors
    val iconShape: IconShapePreset = IconShapePreset.SQUIRCLE,
    val squircleExponent: Float = 4.0f, // n = 2.0 (circle) to 10.0 (near-square)
    val iconSizeScale: Float = 1.0f, // 0.50f to 1.30f
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val labelFontSizeSp: Float = 12f,
    val labelMaxLines: Int = 1,
    val labelColorMode: LabelColorMode = LabelColorMode.WALLPAPER_ADAPTIVE,
    val iconShadowEnabled: Boolean = true,
    val iconPackPackage: String = "",
    val themedIconsEnabled: Boolean = false,
    val themedIconsInDrawer: Boolean = false,
    val appColorPaletteMode: AppColorPaletteMode = AppColorPaletteMode.ORIGINAL,
    val globalAppPlateColorHex: String = "#1B2234",
    val globalAppGlyphColorHex: String = "#7AB8FF",
    val appColorHarmonyIntensity: Float = 0.72f,
    val legacyIconPlateHex: String = "#F3F5FA",
    val customAppOverrides: Map<String, CustomAppOverride> = emptyMap(),
    val badgeStyle: BadgeStyle = BadgeStyle.DOT,

    // 2. Home Grid & Workspace
    val gridColumns: Int = 5, // 4 to 7
    val gridRows: Int = 5, // 4 to 7
    val gridHorizontalPaddingDp: Int = 14,
    val homePageCount: Int = 2,
    val infiniteScroll: Boolean = false,
    val pageIndicatorStyle: PageIndicatorStyle = PageIndicatorStyle.DOTS,
    val lockHomeLayout: Boolean = false,
    val autoAddNewApps: Boolean = true,
    val showStatusBar: Boolean = true,
    val wallpaperParallax: Boolean = true,
    val showAtAGlance: Boolean = true,
    val atAGlanceDateFormat: String = "EEE, MMM d",
    val atAGlanceShowWeather: Boolean = true,

    // 3. Tablet, Foldable & Universal Android Support
    val tabletLayoutMode: TabletLayoutMode = TabletLayoutMode.AUTO,
    val tabletDualPaneWorkspace: Boolean = true,
    val tabletTaskbarStyle: Boolean = true,
    val tabletGridColumns: Int = 6,
    val tabletGridRows: Int = 5,
    val oemCompatibilityProfile: OemCompatibilityProfile = OemCompatibilityProfile.AUTO_UNIVERSAL,
    val universalLegacyIconNormalize: Boolean = true,

    // 4. Search Bar
    val searchBarPosition: SearchBarPosition = SearchBarPosition.BOTTOM,
    val searchBarStyle: SearchBarStyle = SearchBarStyle.PILL,
    val searchBarOpacity: Float = 0.90f,
    val searchBarCornerRadiusDp: Int = 28,
    val showMicIcon: Boolean = true,
    val showLensIcon: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,

    // 5. Dock (Hotseat)
    val dockIconCount: Int = 4, // 3 to 6
    val dockBackgroundStyle: DockBackgroundStyle = DockBackgroundStyle.NONE,
    val dockOpacity: Float = 0.82f,
    val dockBlurEnabled: Boolean = true,
    val showDockOnAllPages: Boolean = true,

    // 6. App Drawer
    val drawerLayoutMode: DrawerLayoutMode = DrawerLayoutMode.VERTICAL,
    val drawerColumns: Int = 4, // 3 to 7
    val drawerBackgroundStyle: DrawerBackgroundStyle = DrawerBackgroundStyle.BLUR,
    val drawerBackgroundOpacity: Float = 0.92f,
    val drawerSearchPosition: DrawerSearchPosition = DrawerSearchPosition.TOP,
    val showSuggestedAppsRow: Boolean = true,
    val suggestedAppsCount: Int = 4, // 4 to 8
    val drawerSortMode: DrawerSortMode = DrawerSortMode.A_TO_Z,
    val showAzFastScroller: Boolean = true,
    val hiddenAppKeys: Set<String> = emptySet(),
    val biometricLockForHiddenApps: Boolean = true,
    val showDrawerCategoryTabs: Boolean = false,
    val showWorkProfileTab: Boolean = true,
    val autoOpenDrawerKeyboard: Boolean = false,
    val searchApps: Boolean = true,
    val searchContacts: Boolean = true,
    val searchShortcuts: Boolean = true,
    val searchSettings: Boolean = true,
    val searchWeb: Boolean = true,

    // 7. Folders
    val folderPreviewStyle: FolderPreviewStyle = FolderPreviewStyle.GRID_2X2,
    val folderBackgroundColorHex: String = "#1F2430",
    val folderBackgroundOpacity: Float = 0.90f,
    val folderColumns: Int = 3,
    val showFolderTitles: Boolean = true,

    // 8. Animations & Physics
    val animationPreset: AnimationPreset = AnimationPreset.PIXEL,
    val animationSpeedMultiplier: Float = 1.0f, // 0.5x to 2.0x
    val springDampingRatio: Float = 0.80f,
    val springStiffness: Float = 380f,
    val appOpenAnimationStyle: AppOpenAnimationStyle = AppOpenAnimationStyle.ICON_ZOOM,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val respectSystemAnimatorScale: Boolean = true,

    // 9. Gestures
    val swipeUpAction: LauncherGestureAction = LauncherGestureAction.DRAWER,
    val swipeDownAction: LauncherGestureAction = LauncherGestureAction.NOTIFICATIONS,
    val doubleTapAction: LauncherGestureAction = LauncherGestureAction.LOCK_SCREEN,
    val doubleTapTargetAppKey: String = "",

    // 10. Theme & Appearance
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColorsEnabled: Boolean = true,
    val accentColorHex: String = "#7AB8FF",
    val fontFamily: LauncherFontFamily = LauncherFontFamily.ROBOTO_FLEX,
    val uiCornerRadiusScale: Float = 1.0f,
    val simulatedWallpaperPreset: Int = 0,

    // 11. Onboarding / First-run state
    val defaultHomePromptDismissed: Boolean = false,
    val hasSeenWelcome: Boolean = false
)
