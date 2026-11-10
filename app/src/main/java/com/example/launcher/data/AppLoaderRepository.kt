package com.example.launcher.data

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.View
import com.example.launcher.model.AppCategoryType
import com.example.launcher.model.AppInfo
import com.example.launcher.model.AppOpenAnimationStyle
import com.example.launcher.model.AppShortcutItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads installed apps across all profiles (Personal + Work profile) via [LauncherApps],
 * listens for real-time package installation/removal/update events via [LauncherApps.Callback],
 * queries dynamic/static shortcuts via [LauncherApps.getShortcuts], and starts activities with
 * icon-bounds [ActivityOptions] transitions.
 */
class AppLoaderRepository(
    private val context: Context,
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onPackageRemovedListener: ((String) -> Unit)? = null
) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val pixelPaletteSeeds = intArrayOf(
        0xFF4285F4.toInt(), // Google Blue
        0xFF34A853.toInt(), // Google Green
        0xFFEA4335.toInt(), // Google Red
        0xFFFBBC04.toInt(), // Google Yellow
        0xFF8AB4F8.toInt(), // Pixel Sky
        0xFFA8C7FA.toInt(), // Material Tonal Blue
        0xFF81C995.toInt(), // Mint
        0xFFF28B82.toInt(), // Coral
        0xFFC58AF9.toInt()  // Lavender
    )

    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            if (!packageName.isNullOrBlank()) {
                onPackageRemovedListener?.invoke(packageName)
            }
            refreshInstalledApps()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            refreshInstalledApps()
        }

        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            refreshInstalledApps()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {
            refreshInstalledApps()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {
            refreshInstalledApps()
        }
    }

    private val packageBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if ((action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_FULLY_REMOVED) &&
                !isReplacing && !pkg.isNullOrBlank()
            ) {
                onPackageRemovedListener?.invoke(pkg)
            }
            refreshInstalledApps()
        }
    }

    init {
        runCatching {
            launcherApps?.registerCallback(launcherCallback, Handler(Looper.getMainLooper()))
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(packageBroadcastReceiver, filter)
            }
        }
        refreshInstalledApps()
    }

    fun unregister() {
        runCatching {
            launcherApps?.unregisterCallback(launcherCallback)
        }
        runCatching {
            context.unregisterReceiver(packageBroadcastReceiver)
        }
    }

    private val activityInfoCache = java.util.concurrent.ConcurrentHashMap<String, LauncherActivityInfo>()

    fun getCachedActivityInfo(componentKey: String): LauncherActivityInfo? {
        return activityInfoCache[componentKey]
    }

    fun refreshInstalledApps() {
        externalScope.launch(ioDispatcher) {
            val loaded = queryAllLauncherActivities()
            _installedApps.value = loaded
            // Enrich dynamic shortcuts asynchronously in background if host permission is active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                launcherApps?.hasShortcutHostPermission() == true
            ) {
                val withShortcuts = loaded.map { app ->
                    if (app.isSynthesizedSample) app
                    else app.copy(shortcuts = queryAppShortcutsSafe(app.packageName, app.userHandle))
                }
                if (withShortcuts != loaded) {
                    _installedApps.value = withShortcuts
                }
            }
        }
    }

    private suspend fun queryAllLauncherActivities(): List<AppInfo> = withContext(ioDispatcher) {
        val result = mutableListOf<AppInfo>()
        val myPackage = context.packageName
        val primaryUser = Process.myUserHandle()
        val profiles: List<UserHandle> = userManager?.userProfiles?.ifEmpty { listOf(primaryUser) }
            ?: listOf(primaryUser)

        for ((profileIdx, profile) in profiles.withIndex()) {
            val rawSerial = runCatching { userManager?.getSerialNumberForUser(profile) }.getOrNull()
            val userSerial = if (rawSerial != null && (profileIdx == 0 || rawSerial != 0L)) {
                rawSerial
            } else {
                profileIdx.toLong()
            }
            val isWork = profile != primaryUser
            val activities: List<LauncherActivityInfo> = runCatching {
                launcherApps?.getActivityList(null, profile) ?: emptyList()
            }.getOrDefault(emptyList())

            for (info in activities) {
                val pkg = info.componentName.packageName
                if (pkg == myPackage) continue // Don't list PixelHome inside its own drawer
                val label = info.label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                val appFlags = info.applicationInfo?.flags ?: 0
                val isSys = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSys = (appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val category = mapAndroidCategory(info.applicationInfo, pkg, label)
                val seedColor = pixelPaletteSeeds[(pkg.hashCode() and Int.MAX_VALUE) % pixelPaletteSeeds.size]
                val shortcuts = buildDefaultShortcuts(pkg)

                val appInfo = AppInfo(
                    packageName = pkg,
                    activityName = info.componentName.className,
                    label = label,
                    userSerial = userSerial,
                    userHandle = profile,
                    isWorkProfile = isWork,
                    firstInstallTime = info.firstInstallTime,
                    category = category,
                    shortcuts = shortcuts,
                    accentTintSeed = seedColor,
                    isSynthesizedSample = false,
                    isSystemApp = isSys,
                    isUpdatedSystemApp = isUpdatedSys
                )
                activityInfoCache[appInfo.componentKey] = info
                result.add(appInfo)
            }
        }

        result
            .distinctBy { it.componentKey }
            .sortedBy { it.label.lowercase() }
    }

    private fun queryAppShortcutsSafe(packageName: String, userHandle: UserHandle): List<AppShortcutItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return buildDefaultShortcuts(packageName)
        }
        val la = launcherApps ?: return buildDefaultShortcuts(packageName)
        if (!la.hasShortcutHostPermission()) {
            return buildDefaultShortcuts(packageName)
        }
        return runCatching {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val list = la.getShortcuts(query, userHandle).orEmpty()
            if (list.isEmpty()) {
                buildDefaultShortcuts(packageName)
            } else {
                list.take(4).map { s ->
                    AppShortcutItem(
                        id = s.id,
                        shortLabel = s.shortLabel?.toString() ?: s.id,
                        longLabel = s.longLabel?.toString() ?: s.shortLabel?.toString() ?: s.id,
                        packageName = s.`package`
                    )
                }
            }
        }.getOrElse { buildDefaultShortcuts(packageName) }
    }

    private fun buildDefaultShortcuts(packageName: String): List<AppShortcutItem> {
        return when {
            packageName.contains("camera", true) -> listOf(
                AppShortcutItem("selfie", "Take a selfie", "Take a selfie", packageName),
                AppShortcutItem("video", "Record video", "Record a video", packageName),
                AppShortcutItem("portrait", "Portrait mode", "Capture portrait", packageName)
            )
            packageName.contains("chrome", true) || packageName.contains("browser", true) -> listOf(
                AppShortcutItem("new_tab", "New tab", "Open new tab", packageName),
                AppShortcutItem("incognito", "Incognito tab", "New Incognito tab", packageName)
            )
            packageName.contains("Dialer", true) || packageName.contains("phone", true) -> listOf(
                AppShortcutItem("new_contact", "New contact", "Create new contact", packageName),
                AppShortcutItem("voicemail", "Voicemail", "Listen to voicemail", packageName)
            )
            packageName.contains("message", true) || packageName.contains("mms", true) -> listOf(
                AppShortcutItem("new_conversation", "New message", "Start new conversation", packageName)
            )
            packageName.contains("settings", true) -> listOf(
                AppShortcutItem("wifi", "Wi-Fi & Network", "Network settings", packageName),
                AppShortcutItem("battery", "Battery usage", "Check battery status", packageName),
                AppShortcutItem("display", "Display & Theme", "Display settings", packageName)
            )
            else -> listOf(
                AppShortcutItem("app_info", "App details", "View app info", packageName)
            )
        }
    }

    private fun mapAndroidCategory(appInfo: ApplicationInfo?, pkg: String, label: String): AppCategoryType {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return AppCategoryType.GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategoryType.COMMUNICATION
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategoryType.PRODUCTIVITY
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategoryType.MEDIA
            }
        }
        val combined = "$pkg $label".lowercase()
        return when {
            combined.contains("phone") || combined.contains("dialer") ||
                combined.contains("message") || combined.contains("chat") ||
                combined.contains("mail") || combined.contains("contacts") -> AppCategoryType.COMMUNICATION
            combined.contains("calendar") || combined.contains("docs") ||
                combined.contains("keep") || combined.contains("notes") ||
                combined.contains("files") || combined.contains("clock") -> AppCategoryType.PRODUCTIVITY
            combined.contains("camera") || combined.contains("photo") ||
                combined.contains("music") || combined.contains("youtube") ||
                combined.contains("gallery") -> AppCategoryType.MEDIA
            combined.contains("settings") || combined.contains("calc") ||
                combined.contains("store") || combined.contains("chrome") -> AppCategoryType.TOOLS
            else -> AppCategoryType.OTHER
        }
    }

    /**
     * Launches an app with [LauncherApps.startMainActivity] and [ActivityOptions]
     * so the activity window expands smoothly out of the tapped icon's screen bounds.
     */
    fun launchApp(
        app: AppInfo,
        rootView: View?,
        iconBoundsOnScreen: Rect?,
        animationStyle: AppOpenAnimationStyle = AppOpenAnimationStyle.ICON_ZOOM
    ): Boolean {
        return runCatching {
            val bounds = iconBoundsOnScreen ?: Rect(0, 0, 200, 200)
            val optionsBundle = if (rootView != null) {
                val width = bounds.width().coerceAtLeast(48)
                val height = bounds.height().coerceAtLeast(48)
                when (animationStyle) {
                    AppOpenAnimationStyle.ICON_ZOOM -> {
                        ActivityOptions.makeClipRevealAnimation(
                            rootView,
                            bounds.left,
                            bounds.top,
                            width,
                            height
                        ).toBundle()
                    }
                    AppOpenAnimationStyle.FADE,
                    AppOpenAnimationStyle.SLIDE -> {
                        ActivityOptions.makeScaleUpAnimation(
                            rootView,
                            bounds.left,
                            bounds.top,
                            width,
                            height
                        ).toBundle()
                    }
                }
            } else {
                null
            }

            if (!app.isSynthesizedSample && launcherApps != null) {
                launcherApps.startMainActivity(
                    app.componentName,
                    app.userHandle,
                    bounds,
                    optionsBundle
                )
                true
            } else {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    sourceBounds = bounds
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent, optionsBundle)
                    true
                } else {
                    false
                }
            }
        }.getOrDefault(false)
    }

    fun openAppDetailsSettings(app: AppInfo) {
        runCatching {
            val bounds = Rect(0, 0, 200, 200)
            if (launcherApps != null && !app.isSynthesizedSample) {
                launcherApps.startAppDetailsActivity(app.componentName, app.userHandle, bounds, null)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun requestUninstallApp(app: AppInfo): Boolean {
        if (!app.canSystemUninstall) {
            return false
        }
        val pkgUri = Uri.fromParts("package", app.packageName, null)
        val launchedDelete = runCatching {
            val intent = Intent(Intent.ACTION_DELETE, pkgUri).apply {
                putExtra(Intent.EXTRA_USER, app.userHandle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launchedDelete) return true

        return runCatching {
            @Suppress("DEPRECATION")
            val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri).apply {
                putExtra(Intent.EXTRA_USER, app.userHandle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        }.getOrDefault(false)
    }
}
