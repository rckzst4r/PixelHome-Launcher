package com.example.launcher.icon

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.launcher.model.AppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Holds pre-rendered layers of an app's icon so that shape clipping, scale animations,
 * icon pack styles, custom app color overrides, and Material You themed tinting happen on the GPU
 * at draw time without re-rasterizing.
 *
 * [compositeBitmap] holds the pre-composited full icon (background + foreground) so standard
 * and duotone rendering only requires a single [androidx.compose.foundation.Image] draw node.
 */
@Immutable
data class CachedAppIconLayers(
    val componentKey: String,
    val isAdaptive: Boolean,
    val hasRealMonochrome: Boolean,
    val isFullBleedLegacy: Boolean = false,
    val compositeBitmap: ImageBitmap,
    val backgroundBitmap: ImageBitmap? = null,
    val foregroundBitmap: ImageBitmap,
    val monochromeBitmap: ImageBitmap?,
    val dominantSeedColor: Int
)

@Stable
class IconCache(
    private val context: Context,
    private val activityInfoProvider: ((String) -> LauncherActivityInfo?)? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    // 112px is 2x density for 56dp icons: crisp inside squircle masks while using 40% less RAM & CPU than 144px
    private val iconPixelSize = 112
    private val densityDpi = context.resources.displayMetrics.densityDpi
    val iconPackManager = IconPackManager(context)

    private val memoryCache = object : LruCache<String, CachedAppIconLayers>(320) {}

    // Tiny 8x8 emergency bitmap shared if memory is ever critically low
    private val emergencySharedBitmap: ImageBitmap by lazy {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF4285F4.toInt())
        bmp.asImageBitmap()
    }

    companion object {
        /**
         * Detects Xiaomi, Redmi, POCO, MIUI, or HyperOS devices where the system icon engine
         * wraps or flattens app icons into non-standard opaque bitmaps.
         */
        val isXiaomiOrMiuiDevice: Boolean by lazy {
            val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
            val brand = (Build.BRAND ?: "").lowercase()
            val display = (Build.DISPLAY ?: "").lowercase()
            manufacturer.contains("xiaomi") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco") ||
                display.contains("hyperos") ||
                display.contains("miui")
        }
    }

    fun getCachedIfPresent(componentKey: String, iconPackPkg: String = ""): CachedAppIconLayers? {
        return memoryCache.get("$componentKey|$iconPackPkg")
    }

    suspend fun preloadIcons(apps: List<AppInfo>, iconPackPkg: String = "") {
        withContext(defaultDispatcher) {
            supervisorScope {
                apps.distinctBy { it.componentKey }.chunked(8).forEach { batch ->
                    batch.map { app ->
                        async {
                            val cacheKey = "${app.componentKey}|$iconPackPkg"
                            if (memoryCache.get(cacheKey) == null) {
                                try {
                                    val loaded = loadAppIconLayersInternal(app, iconPackPkg)
                                    memoryCache.put(cacheKey, loaded)
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (_: Throwable) {
                                    // Ignore individual icon preload failures
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    suspend fun getOrLoadIcon(app: AppInfo, iconPackPkg: String = ""): CachedAppIconLayers {
        val cacheKey = "${app.componentKey}|$iconPackPkg"
        memoryCache.get(cacheKey)?.let { return it }

        return withContext(defaultDispatcher) {
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val loaded = try {
                loadAppIconLayersInternal(app, iconPackPkg)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                createEmergencyLayers(app)
            }
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
            try {
                val packDrawable = iconPackManager.loadDrawableFromExternalIconPack(
                    iconPackPackage = iconPackPkg,
                    componentName = app.componentName
                )
                if (packDrawable != null) {
                    return rasterizeDrawableLayers(app, packDrawable)
                }
            } catch (_: Throwable) {
                // Fall back to system icon
            }
        }

        // 2. Load native AdaptiveIconDrawable or legacy icon via cached LauncherActivityInfo or PackageManager
        if (!app.isSynthesizedSample) {
            try {
                val cachedActivityInfo = activityInfoProvider?.invoke(app.componentKey)

                val rawDrawable: Drawable? = cachedActivityInfo?.let {
                    runCatching { it.getIcon(densityDpi) }.getOrNull()
                } ?: runCatching {
                    context.packageManager.getActivityIcon(app.componentName)
                }.getOrNull() ?: runCatching {
                    context.packageManager.getApplicationIcon(app.packageName)
                }.getOrNull() ?: runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                    launcherApps?.getActivityList(app.packageName, app.userHandle)
                        ?.firstOrNull()
                        ?.getIcon(densityDpi)
                }.getOrNull()

                if (rawDrawable != null) {
                    return rasterizeDrawableLayers(app, rawDrawable)
                }
            } catch (_: Throwable) {
                // Fall back to synthesized monogram layers
            }
        }

        return try {
            createFallbackLayers(app)
        } catch (_: Throwable) {
            createEmergencyLayers(app)
        }
    }

    private fun rasterizeDrawableLayers(app: AppInfo, drawable: Drawable): CachedAppIconLayers {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val insetExpand = (iconPixelSize * 0.22f).toInt()

            val bgBitmap = drawable.background?.let { bg ->
                renderDrawableToBitmapSafe(bg, iconPixelSize, insetExpand)
            }
            val fgBitmap = drawable.foreground?.let { fg ->
                renderDrawableToBitmapSafe(fg, iconPixelSize, insetExpand)
            } ?: createMonogramBitmap(app.label, Color.WHITE)

            var hasRealMono = false
            val monoBitmap: Bitmap? = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                drawable.monochrome != null
            ) {
                hasRealMono = true
                renderDrawableToBitmapSafe(
                    drawable.monochrome!!,
                    iconPixelSize,
                    insetExpand,
                    forceWhiteMask = true
                )
            } else if (!isXiaomiOrMiuiDevice && isTrueSingleColorGlyph(fgBitmap)) {
                hasRealMono = true
                extractAlphaSilhouette(fgBitmap)
            } else {
                hasRealMono = false
                null
            }

            val sampledColor = bgBitmap?.let { sampleDominantColor(it, app.accentTintSeed) }
                ?: sampleDominantColor(fgBitmap, app.accentTintSeed)

            // Pre-composite background + foreground into a single Bitmap so standard rendering
            // only needs 1 Image composable and doesn't have to keep bgBitmap in RAM.
            val compositeBmp = Bitmap.createBitmap(iconPixelSize, iconPixelSize, Bitmap.Config.ARGB_8888)
            val compositeCanvas = Canvas(compositeBmp)
            compositeCanvas.drawColor(sampledColor)
            if (bgBitmap != null) {
                compositeCanvas.drawBitmap(bgBitmap, 0f, 0f, null)
            }
            compositeCanvas.drawBitmap(fgBitmap, 0f, 0f, null)

            return CachedAppIconLayers(
                componentKey = app.componentKey,
                isAdaptive = true,
                hasRealMonochrome = hasRealMono,
                isFullBleedLegacy = false,
                compositeBitmap = compositeBmp.asImageBitmap(),
                backgroundBitmap = null,
                foregroundBitmap = fgBitmap.asImageBitmap(),
                monochromeBitmap = monoBitmap?.asImageBitmap(),
                dominantSeedColor = sampledColor
            )
        } else {
            // Non-adaptive Drawable (common on Xiaomi MIUI/HyperOS, Samsung themes, and Android 7.0+)
            val fullBitmap = renderDrawableToBitmapSafe(drawable, iconPixelSize, outset = 0)
            val sampledColor = sampleDominantColor(fullBitmap, app.accentTintSeed)
            val isFullBleedTile = isFilledSquareOrSquircleTile(fullBitmap)

            val finalFgBitmap = if (isFullBleedTile) {
                fullBitmap
            } else {
                val margin = (iconPixelSize * 0.14f).toInt()
                renderDrawableToBitmapSafe(drawable, iconPixelSize, outset = -margin)
            }

            val finalImageBitmap = finalFgBitmap.asImageBitmap()
            return CachedAppIconLayers(
                componentKey = app.componentKey,
                isAdaptive = false,
                hasRealMonochrome = false,
                isFullBleedLegacy = isFullBleedTile,
                compositeBitmap = finalImageBitmap,
                backgroundBitmap = null,
                foregroundBitmap = finalImageBitmap,
                monochromeBitmap = null,
                dominantSeedColor = sampledColor
            )
        }
    }

    /**
     * Checks whether a foreground bitmap is genuinely a single-color vector glyph with transparent
     * background & interior cutouts, rather than a multi-color bitmap or MIUI boxed icon.
     */
    private fun isTrueSingleColorGlyph(bitmap: Bitmap): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 16 || h < 16) return false
            if (Color.alpha(bitmap.getPixel(4, 4)) > 20 ||
                Color.alpha(bitmap.getPixel(w - 5, 4)) > 20 ||
                Color.alpha(bitmap.getPixel(4, h - 5)) > 20 ||
                Color.alpha(bitmap.getPixel(w - 5, h - 5)) > 20
            ) {
                return false
            }

            val start = (w * 0.22f).toInt()
            val end = (w * 0.78f).toInt()
            val step = (w / 10).coerceAtLeast(1)
            var totalSamples = 0
            var opaqueSamples = 0
            var firstR = -1
            var firstG = -1
            var firstB = -1
            var colorDiffSum = 0

            for (y in start..end step step) {
                for (x in start..end step step) {
                    totalSamples++
                    val px = bitmap.getPixel(x, y)
                    val a = Color.alpha(px)
                    if (a > 180) {
                        opaqueSamples++
                        val r = Color.red(px)
                        val g = Color.green(px)
                        val b = Color.blue(px)
                        if (firstR == -1) {
                            firstR = r
                            firstG = g
                            firstB = b
                        } else {
                            colorDiffSum += abs(r - firstR) + abs(g - firstG) + abs(b - firstB)
                        }
                    }
                }
            }

            if (totalSamples == 0 || opaqueSamples == 0) return false
            val opaqueRatio = opaqueSamples.toFloat() / totalSamples.toFloat()
            val avgColorDiff = colorDiffSum.toFloat() / opaqueSamples.toFloat()

            opaqueRatio in 0.08f..0.68f && avgColorDiff < 35f
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Detects whether a non-adaptive bitmap is already a full-bleed square or squircle tile (like MIUI icons).
     */
    private fun isFilledSquareOrSquircleTile(bitmap: Bitmap): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 16 || h < 16) return false
            val inset = (w * 0.18f).toInt()
            val p1 = Color.alpha(bitmap.getPixel(inset, inset))
            val p2 = Color.alpha(bitmap.getPixel(w - inset, inset))
            val p3 = Color.alpha(bitmap.getPixel(inset, h - inset))
            val p4 = Color.alpha(bitmap.getPixel(w - inset, h - inset))
            val center = Color.alpha(bitmap.getPixel(w / 2, h / 2))
            p1 > 200 && p2 > 200 && p3 > 200 && p4 > 200 && center > 200
        } catch (_: Throwable) {
            false
        }
    }

    private fun sampleDominantColor(bitmap: Bitmap, fallbackColor: Int): Int {
        return try {
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            val step = (bitmap.width / 6).coerceAtLeast(1)
            for (y in step until bitmap.height step step) {
                for (x in step until bitmap.width step step) {
                    val px = bitmap.getPixel(x, y)
                    val a = Color.alpha(px)
                    if (a > 160) {
                        val r = Color.red(px)
                        val g = Color.green(px)
                        val b = Color.blue(px)
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
        } catch (_: Throwable) {
            fallbackColor
        }
    }

    private fun renderDrawableToBitmapSafe(
        drawable: Drawable,
        size: Int,
        outset: Int,
        forceWhiteMask: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                drawable is BitmapDrawable &&
                drawable.bitmap?.config == Bitmap.Config.HARDWARE
            ) {
                val swBitmap = drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (swBitmap != null) {
                    val dst = RectF(
                        -outset.toFloat(),
                        -outset.toFloat(),
                        (size + outset).toFloat(),
                        (size + outset).toFloat()
                    )
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    if (forceWhiteMask) {
                        paint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawBitmap(swBitmap, null, dst, paint)
                    return bitmap
                }
            }

            synchronized(drawable) {
                val copy = runCatching {
                    drawable.constantState?.newDrawable(context.resources)?.mutate()
                }.getOrNull() ?: runCatching { drawable.mutate() }.getOrDefault(drawable)

                copy.setBounds(-outset, -outset, size + outset, size + outset)
                if (forceWhiteMask) {
                    copy.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                }
                copy.draw(canvas)
            }
        } catch (_: Throwable) {
            // If an OEM drawable contains a nested HARDWARE bitmap or throws during draw,
            // keep bitmap valid rather than crashing the launcher.
        }
        return bitmap
    }

    private fun extractAlphaSilhouette(source: Bitmap): Bitmap? {
        return try {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            val margin = (source.width * 0.12f)
            val dst = RectF(margin, margin, source.width - margin, source.height - margin)
            canvas.drawBitmap(source, null, dst, paint)
            out
        } catch (_: Throwable) {
            null
        }
    }

    private fun createFallbackLayers(app: AppInfo): CachedAppIconLayers {
        val fgBitmap = createMonogramBitmap(app.label, Color.WHITE)
        val compositeBmp = Bitmap.createBitmap(iconPixelSize, iconPixelSize, Bitmap.Config.ARGB_8888)
        val compositeCanvas = Canvas(compositeBmp)
        compositeCanvas.drawColor(app.accentTintSeed)
        compositeCanvas.drawBitmap(fgBitmap, 0f, 0f, null)

        val fgImage = fgBitmap.asImageBitmap()
        return CachedAppIconLayers(
            componentKey = app.componentKey,
            isAdaptive = true,
            hasRealMonochrome = true,
            isFullBleedLegacy = false,
            compositeBitmap = compositeBmp.asImageBitmap(),
            backgroundBitmap = null,
            foregroundBitmap = fgImage,
            monochromeBitmap = fgImage,
            dominantSeedColor = app.accentTintSeed
        )
    }

    private fun createEmergencyLayers(app: AppInfo): CachedAppIconLayers {
        return CachedAppIconLayers(
            componentKey = app.componentKey,
            isAdaptive = true,
            hasRealMonochrome = false,
            isFullBleedLegacy = true,
            compositeBitmap = emergencySharedBitmap,
            backgroundBitmap = null,
            foregroundBitmap = emergencySharedBitmap,
            monochromeBitmap = null,
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
