package com.example.launcher.icon

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.launcher.model.IconShapePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * High-performance Path cache for [SquircleShape].
 * Eliminates thousands of per-second Path allocations and trigonometric/power calculations
 * during animations, scrolling, and gestures.
 */
internal object SquirclePathCache {
    private val pathCache = object : LruCache<String, Path>(180) {}
    private val lock = Any()

    fun getSuperellipsePath(width: Float, height: Float, n: Float): Path {
        val safeW = if (width.isFinite() && width > 1f) width else 1f
        val safeH = if (height.isFinite() && height > 1f) height else 1f
        val safeN = if (n.isFinite()) n.coerceIn(2.0f, 10.0f) else 4.0f

        val wKey = (safeW * 2f).roundToInt()
        val hKey = (safeH * 2f).roundToInt()
        val nKey = (safeN * 10f).roundToInt()
        val key = "sq_${wKey}_${hKey}_$nKey"

        synchronized(lock) {
            pathCache.get(key)?.let { return it }
        }

        // Analytical 4-quadrant cubic Bézier superellipse:
        // Strictly convex, C2-smooth, 18x fewer segments than a 72-line polygon.
        val path = Path()
        val a = safeW / 2f
        val b = safeH / 2f
        val cx = a
        val cy = b
        val diagRatio = 2.0.pow(-1.0 / safeN.toDouble()).toFloat()
        val k = ((diagRatio - 0.5f) / 0.375f).coerceIn(0.5522848f, 0.965f)
        val kx = a * k
        val ky = b * k

        // Start at top-center (cx, 0) and sweep clockwise through 4 quadrants
        path.moveTo(cx, 0f)
        // Top-Right quadrant: (cx, 0) -> (safeW, cy)
        path.cubicTo(cx + kx, 0f, safeW, cy - ky, safeW, cy)
        // Bottom-Right quadrant: (safeW, cy) -> (cx, safeH)
        path.cubicTo(safeW, cy + ky, cx + kx, safeH, cx, safeH)
        // Bottom-Left quadrant: (cx, safeH) -> (0, cy)
        path.cubicTo(cx - kx, safeH, 0f, cy + ky, 0f, cy)
        // Top-Left quadrant: (0, cy) -> (cx, 0)
        path.cubicTo(0f, cy - ky, cx - kx, 0f, cx, 0f)
        path.close()

        synchronized(lock) {
            pathCache.put(key, path)
        }
        return path
    }

    fun getFlowerScallopPath(width: Float, height: Float): Path {
        val safeW = if (width.isFinite() && width > 1f) width else 1f
        val safeH = if (height.isFinite() && height > 1f) height else 1f
        val wKey = (safeW * 2f).roundToInt()
        val hKey = (safeH * 2f).roundToInt()
        val key = "fl_${wKey}_$hKey"

        synchronized(lock) {
            pathCache.get(key)?.let { return it }
        }

        val path = Path()
        val cx = safeW / 2f
        val cy = safeH / 2f
        val baseR = min(safeW, safeH) * 0.45f
        val waveAmp = min(safeW, safeH) * 0.048f
        val petals = 8
        val steps = 48
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseR + waveAmp * cos(petals * theta).toFloat()
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        synchronized(lock) {
            pathCache.put(key, path)
        }
        return path
    }

    fun getRoundedHexagonPath(width: Float, height: Float): Path {
        val safeW = if (width.isFinite() && width > 1f) width else 1f
        val safeH = if (height.isFinite() && height > 1f) height else 1f
        val wKey = (safeW * 2f).roundToInt()
        val hKey = (safeH * 2f).roundToInt()
        val key = "hex_${wKey}_$hKey"

        synchronized(lock) {
            pathCache.get(key)?.let { return it }
        }

        val path = Path()
        val cx = safeW / 2f
        val cy = safeH / 2f
        val rx = safeW * 0.48f
        val ry = safeH * 0.48f
        val steps = 36
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val hexMod = 0.93f + 0.07f * cos(6.0 * theta).toFloat()
            val x = cx + rx * hexMod * cos(theta).toFloat()
            val y = cy + ry * hexMod * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        synchronized(lock) {
            pathCache.put(key, path)
        }
        return path
    }

    fun getOrganicPebblePath(width: Float, height: Float): Path {
        val safeW = if (width.isFinite() && width > 1f) width else 1f
        val safeH = if (height.isFinite() && height > 1f) height else 1f
        val wKey = (safeW * 2f).roundToInt()
        val hKey = (safeH * 2f).roundToInt()
        val key = "peb_${wKey}_$hKey"

        synchronized(lock) {
            pathCache.get(key)?.let { return it }
        }

        val path = Path()
        val cx = safeW / 2f
        val cy = safeH / 2f
        val rx = safeW * 0.47f
        val ry = safeH * 0.47f
        val steps = 36
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val asym = 1.0f + 0.045f * sin(2.0 * theta + 0.6).toFloat() - 0.025f * cos(3.0 * theta).toFloat()
            val x = cx + rx * asym * cos(theta).toFloat()
            val y = cy + ry * asym * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        synchronized(lock) {
            pathCache.put(key, path)
        }
        return path
    }
}

/**
 * Hardware-Accelerated Squircle Shape:
 *
 * Uses native GPU RenderNode RoundRect clipping (`Outline.Rounded`) for `SQUIRCLE`, `CIRCLE`,
 * `ROUNDED_SQUARE`, `SQUARE`, and `TEARDROP` so scrolling in `LazyVerticalGrid` has zero
 * software/stencil `clipPath` cost and zero per-frame allocations.
 */
@Immutable
class SquircleShape(
    val exponent: Float = 5.4f,
    val preset: IconShapePreset = IconShapePreset.SQUIRCLE
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }

        return when (preset) {
            IconShapePreset.CIRCLE -> {
                val radius = min(size.width, size.height) / 2f
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                )
            }

            IconShapePreset.SQUARE -> {
                val r = min(size.width, size.height) * 0.08f
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }

            IconShapePreset.ROUNDED_SQUARE -> {
                val safeExp = if (exponent.isFinite()) exponent.coerceIn(2f, 10f) else 5.4f
                val normalized = ((safeExp - 2f) / 8f)
                val fraction = (0.26f - normalized * 0.14f).coerceIn(0.12f, 0.26f)
                val r = min(size.width, size.height) * fraction
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }

            IconShapePreset.TEARDROP -> {
                val rLarge = min(size.width, size.height) * 0.44f
                val rSmall = min(size.width, size.height) * 0.12f
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        topLeft = CornerRadius(rLarge, rLarge),
                        topRight = CornerRadius(rLarge, rLarge),
                        bottomLeft = CornerRadius(rLarge, rLarge),
                        bottomRight = CornerRadius(rSmall, rSmall)
                    )
                )
            }

            IconShapePreset.FLOWER -> {
                Outline.Generic(SquirclePathCache.getFlowerScallopPath(size.width, size.height))
            }

            IconShapePreset.HEXAGON -> {
                Outline.Generic(SquirclePathCache.getRoundedHexagonPath(size.width, size.height))
            }

            IconShapePreset.PEBBLE -> {
                Outline.Generic(SquirclePathCache.getOrganicPebblePath(size.width, size.height))
            }

            IconShapePreset.SQUIRCLE -> {
                val safeExp = if (exponent.isFinite()) exponent.coerceIn(2.0f, 10.0f) else 5.4f
                val normalized = ((safeExp - 2.0f) / 8.0f)
                val fraction = (0.32f - normalized * 0.18f).coerceIn(0.14f, 0.32f)
                val r = min(size.width, size.height) * fraction
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return exponent == other.exponent && preset == other.preset
    }

    override fun hashCode(): Int {
        var result = exponent.hashCode()
        result = 31 * result + preset.hashCode()
        return result
    }
}
