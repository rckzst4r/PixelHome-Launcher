package com.example.launcher.icon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val subtitle: String,
    val isBuiltInStudioPack: Boolean = false
)

/**
 * Discovers and parses ADW / Nova / Apex compatible icon packs (`org.adw.launcher.THEMES`,
 * `com.novalauncher.THEME`) by reading their `res/xml/appfilter.xml` or `assets/appfilter.xml`
 * component-to-drawable mappings.
 *
 * Also provides built-in studio icon pack styles (`builtin:neon`, `builtin:pastel`, `builtin:obsidian`)
 * so users can test icon pack theming immediately without installing external APKs.
 */
class IconPackManager(private val context: Context) {

    private val parsedAppFilters = ConcurrentHashMap<String, Map<String, String>>()

    fun getAvailableIconPacks(): List<IconPackInfo> {
        val list = mutableListOf(
            IconPackInfo(
                packageName = "",
                label = "System Default (Adaptive)",
                subtitle = "Original app icons with Squircle engine",
                isBuiltInStudioPack = true
            ),
            IconPackInfo(
                packageName = BUILTIN_NEON_PACK,
                label = "Pixel Cyber Neon",
                subtitle = "Deep obsidian plates with luminous neon glyphs",
                isBuiltInStudioPack = true
            ),
            IconPackInfo(
                packageName = BUILTIN_PASTEL_PACK,
                label = "Material Pastel Studio",
                subtitle = "Soft tonal watercolor plates with dark contrast glyphs",
                isBuiltInStudioPack = true
            ),
            IconPackInfo(
                packageName = BUILTIN_OBSIDIAN_PACK,
                label = "Obsidian Minimal",
                subtitle = "High-contrast matte carbon icons with crisp white glyphs",
                isBuiltInStudioPack = true
            )
        )

        val pm = context.packageManager
        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME"
        )
        val discoveredPackages = mutableSetOf<String>()

        for (action in actions) {
            val resolved = runCatching {
                pm.queryIntentActivities(Intent(action), 0)
            }.getOrDefault(emptyList())

            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && discoveredPackages.add(pkg)) {
                    val label = info.loadLabel(pm)?.toString() ?: pkg
                    list.add(
                        IconPackInfo(
                            packageName = pkg,
                            label = label,
                            subtitle = "Installed ADW/Nova Icon Pack ($pkg)",
                            isBuiltInStudioPack = false
                        )
                    )
                }
            }
        }

        return list
    }

    /**
     * Loads a custom [Drawable] for [componentName] from an installed ADW/Nova/Apex icon pack APK
     * using its `appfilter.xml` mapping.
     */
    fun loadDrawableFromExternalIconPack(
        iconPackPackage: String,
        componentName: ComponentName
    ): Drawable? {
        if (iconPackPackage.isBlank() || iconPackPackage.startsWith("builtin:")) return null

        return runCatching {
            val pm = context.packageManager
            val packResources = pm.getResourcesForApplication(iconPackPackage)
            val filterMap = parsedAppFilters.getOrPut(iconPackPackage) {
                parseAppFilterXml(iconPackPackage, packResources)
            }

            val key = "ComponentInfo{${componentName.packageName}/${componentName.className}}"
            val drawableName = filterMap[key]
                ?: filterMap.entries.firstOrNull { it.key.contains(componentName.packageName) }?.value
                ?: return null

            val resId = packResources.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId != 0) {
                ResourcesCompat.getDrawable(packResources, resId, null)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseAppFilterXml(iconPackPackage: String, packRes: Resources): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val xmlId = packRes.getIdentifier("appfilter", "xml", iconPackPackage)
            val parser: XmlPullParser = if (xmlId != 0) {
                packRes.getXml(xmlId)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val p = factory.newPullParser()
                p.setInput(packRes.assets.open("appfilter.xml"), "UTF-8")
                p
            }

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val componentAttr = parser.getAttributeValue(null, "component")
                    val drawableAttr = parser.getAttributeValue(null, "drawable")
                    if (!componentAttr.isNullOrBlank() && !drawableAttr.isNullOrBlank()) {
                        map[componentAttr] = drawableAttr
                    }
                }
                eventType = parser.next()
            }
        }
        return map
    }

    companion object {
        const val BUILTIN_NEON_PACK = "builtin:neon"
        const val BUILTIN_PASTEL_PACK = "builtin:pastel"
        const val BUILTIN_OBSIDIAN_PACK = "builtin:obsidian"
    }
}
