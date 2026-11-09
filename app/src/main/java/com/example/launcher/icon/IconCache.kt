package com.example.launcher.icon

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds pre-rendered layers of an app's icon so that shape clipping, scale animations,
 * icon pack styles, custom app color overrides, and Material You themed tinting happen on the GPU
 * at draw time without re-rasterizing.
 *
 * Universal Android Support (API 24+ through API 36+):
 * - On Android 8.0+ (API 26+), decomposes [AdaptiveIconDrawable] into background, foreground, and
 *   Android 13+ monochrome layers.
 * - On Android 7.0/7.1 (API 24–25) or when an app/OEM ROM supplies a legacy non-adaptive drawable,
 *   automatically samples the icon's dominant brand color and builds a normalized safe-zone foreground +
 *   monochrome mask so every app supports squircle clipping and custom app color tinting universally.
 */
data class CachedAppIconLayers(
    val componentKey: String,
    val isAdaptive: Boolean,
    val hasRealMonochrome: Boolean,
    val backgroundBitmap: ImageBitmap?,
    val foregroundBitmap: ImageBitmap,
    val monochromeBitmap: ImageBitmap?,
    val dominantSeedColor: Int
)

class IconCache(
    private val context: Context,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val iconPixelSize = 192
    val iconPackManager = IconPackManager(context)

    private val memoryCache = object : LruCache<String, CachedAppIconLayers>(240) {}

    fun getCachedIfPresent(componentKey: String, iconPackPkg: String = ""): CachedAppIconLayers? {
        return memoryCache.get("$componentKey|$iconPackPkg")
    }

    suspend fun getOrLoadIcon(app: AppInfo, iconPackPkg: String = ""): CachedAppIconLayers {
        val cacheKey = "${app.componentKey}|$iconPackPkg"
        memoryCache.get(cacheKey)?.let { return it }

        return withContext(defaultDispatcher) {
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val loaded = loadAppIconLayersInternal(app, iconPackPkg)
            memoryCache.put(cacheKey, loaded)
            loaded
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    private fun loadAppIconLayersInternal(app: AppInfo, iconPackPkg: String): CachedAppIconLayers {
        // 1. Check external ADW/Nova/Apex Icon Pack first if configured
        if (iconPackPkg.isNotBlank() && !iconPackPkg.startsWith("builtin:")) {
            val packDrawable = iconPackManager.loadDrawableFromExternalIconPack(
                iconPackPackage = iconPackPkg,
                componentName = app.componentName
            )
            if (packDrawable != null) {
                return rasterizeDrawableLayers(app, packDrawable)
            }
        }

        // 2. Load native AdaptiveIconDrawable or legacy icon via LauncherApps / PackageManager (API 24+ safe)
        if (!app.isSynthesizedSample) {
            runCatching {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                val activityInfo = launcherApps
                    ?.getActivityList(app.packageName, app.userHandle)
                    ?.firstOrNull { it.componentName.className == app.activityName }
                    ?: launcherApps?.getActivityList(app.packageName, app.userHandle)?.firstOrNull()

                val density = context.resources.displayMetrics.densityDpi
                val rawDrawable: Drawable? = activityInfo?.getIcon(density)
                    ?: runCatching {
                        context.packageManager.getActivityIcon(app.componentName)
                    }.getOrNull()
                    ?: runCatching {
                        context.packageManager.getApplicationIcon(app.packageName)
                    }.getOrNull()

                if (rawDrawable != null) {
                    return rasterizeDrawableLayers(app, rawDrawable)
                }
            }
        }

        return createFallbackLayers(app)
    }

    private fun rasterizeDrawableLayers(app: AppInfo, drawable: Drawable): CachedAppIconLayers {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val insetExpand = (iconPixelSize * 0.22f).toInt()

            val bgBitmap = drawable.background?.let { bg ->
                renderDrawableToBitmap(bg, iconPixelSize, insetExpand)
            }
            val fgBitmap = drawable.foreground?.let { fg ->
                renderDrawableToBitmap(fg, iconPixelSize, insetExpand)
            } ?: createMonogramBitmap(app.label, Color.WHITE)

            val isFgOpaque = isOpaqueSquareBitmap(fgBitmap)

            var hasRealMono = false
            val monoBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable.monochrome != null) {
                hasRealMono = true
                renderDrawableToBitmap(drawable.monochrome!!, iconPixelSize, insetExpand, forceWhiteMask = true)
            } else if (!isFgOpaque) {
                // Foreground has genuine transparent borders/margins: safe to extract silhouette
                hasRealMono = true
                extractAlphaSilhouette(fgBitmap)
            } else {
                // Foreground is an opaque square (typical on Xiaomi/MIUI): do NOT silhouette into a square block!
                hasRealMono = false
                null
            }

            val sampledColor = bgBitmap?.let { sampleDominantColor(it, app.accentTintSeed) }
                ?: app.accentTintSeed

            return CachedAppIconLayers(
                componentKey = app.componentKey,
                isAdaptive = true,
                hasRealMonochrome = hasRealMono,
                backgroundBitmap = bgBitmap?.asImageBitmap(),
                foregroundBitmap = fgBitmap.asImageBitmap(),
                monochromeBitmap = monoBitmap?.asImageBitmap(),
                dominantSeedColor = sampledColor
            )
        } else {
            // Universal Android 7.0+ (API 24+) & OEM Legacy Icon Normalizer (Xiaomi, Samsung legacy, etc.)
            val bitmap = Bitmap.createBitmap(iconPixelSize, iconPixelSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val margin = (iconPixelSize * 0.17f).toInt()
            drawable.setBounds(margin, margin, iconPixelSize - margin, iconPixelSize - margin)
            drawable.draw(canvas)

            val sampledColor = sampleDominantColor(bitmap, app.accentTintSeed)
            val isOpaque = isOpaqueSquareBitmap(bitmap)
            val monoBitmap = if (!isOpaque) extractAlphaSilhouette(bitmap) else null

            return CachedAppIconLayers(
                componentKey = app.componentKey,
                isAdaptive = false,
                hasRealMonochrome = !isOpaque && monoBitmap != null,
                backgroundBitmap = null,
                foregroundBitmap = bitmap.asImageBitmap(),
                monochromeBitmap = monoBitmap?.asImageBitmap(),
                dominantSeedColor = sampledColor
            )
        }
    }

    private fun isOpaqueSquareBitmap(bitmap: Bitmap): Boolean {
        return runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val c1 = Color.alpha(bitmap.getPixel(4, 4))
            val c2 = Color.alpha(bitmap.getPixel(w - 5, 4))
            val c3 = Color.alpha(bitmap.getPixel(4, h - 5))
            val c4 = Color.alpha(bitmap.getPixel(w - 5, h - 5))
            c1 > 220 && c2 > 220 && c3 > 220 && c4 > 220
        }.getOrDefault(false)
    }

    private fun sampleDominantColor(bitmap: Bitmap, fallbackColor: Int): Int {
        return runCatching {
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            val step = (bitmap.width / 8).coerceAtLeast(1)
            for (y in step until bitmap.height step step) {
                for (x in step until bitmap.width step step) {
                    val px = bitmap.getPixel(x, y)
                    val a = Color.alpha(px)
                    if (a > 160) {
                        val r = Color.red(px)
                        val g = Color.green(px)
                        val b = Color.blue(px)
                        // Skip pure white or pure black neutral pixels when finding brand accent
                        if ((r + g + b) in 45..710) {
                            rSum += r
                            gSum += g
                            bSum += b
                            count++
                        }
                    }
                }
            }
            if (count > 0) {
                Color.rgb(
                    (rSum / count).toInt().coerceIn(0, 255),
                    (gSum / count).toInt().coerceIn(0, 255),
                    (bSum / count).toInt().coerceIn(0, 255)
                )
            } else {
                fallbackColor
            }
        }.getOrDefault(fallbackColor)
    }

    private fun renderDrawableToBitmap(
        drawable: Drawable,
        size: Int,
        outset: Int,
        forceWhiteMask: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        copy.setBounds(-outset, -outset, size + outset, size + outset)
        if (forceWhiteMask) {
            copy.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        copy.draw(canvas)
        return bitmap
    }

    private fun extractAlphaSilhouette(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        val margin = (source.width * 0.14f)
        val dst = RectF(margin, margin, source.width - margin, source.height - margin)
        canvas.drawBitmap(source, null, dst, paint)
        return out
    }

    private fun createFallbackLayers(app: AppInfo): CachedAppIconLayers {
        val bgBitmap = Bitmap.createBitmap(iconPixelSize, iconPixelSize, Bitmap.Config.ARGB_8888)
        val bgCanvas = Canvas(bgBitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = app.accentTintSeed
        }
        bgCanvas.drawRect(0f, 0f, iconPixelSize.toFloat(), iconPixelSize.toFloat(), bgPaint)

        val fgBitmap = createMonogramBitmap(app.label, Color.WHITE)
        return CachedAppIconLayers(
            componentKey = app.componentKey,
            isAdaptive = true,
            hasRealMonochrome = true,
            backgroundBitmap = bgBitmap.asImageBitmap(),
            foregroundBitmap = fgBitmap.asImageBitmap(),
            monochromeBitmap = fgBitmap.asImageBitmap(),
            dominantSeedColor = app.accentTintSeed
        )
    }

    private fun createMonogramBitmap(label: String, textColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(iconPixelSize, iconPixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = iconPixelSize * 0.42f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val letter = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        val yPos = (iconPixelSize / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(letter, iconPixelSize / 2f, yPos, paint)
        return bmp
    }
}
