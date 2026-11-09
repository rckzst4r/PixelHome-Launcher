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

    fun getSuperellipsePath(width: Float, height: Float, n: Float): Path {
        val wKey = (width * 2f).roundToInt()
        val hKey = (height * 2f).roundToInt()
        val nKey = (n * 10f).roundToInt()
        val key = "sq_${wKey}_${hKey}_$nKey"

        pathCache.get(key)?.let { return it }

        val path = Path()
        val a = width / 2f
        val b = height / 2f
        val cx = a
        val cy = b
        val power = 2.0 / n.toDouble()
        val steps = 72

        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val cosT = cos(theta)
            val sinT = sin(theta)
            val x = cx + a * sign(cosT).toFloat() * abs(cosT).pow(power).toFloat()
            val y = cy + b * sign(sinT).toFloat() * abs(sinT).pow(power).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        pathCache.put(key, path)
        return path
    }

    fun getFlowerScallopPath(width: Float, height: Float): Path {
        val wKey = (width * 2f).roundToInt()
        val hKey = (height * 2f).roundToInt()
        val key = "fl_${wKey}_$hKey"

        pathCache.get(key)?.let { return it }

        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        val baseR = min(width, height) * 0.45f
        val waveAmp = min(width, height) * 0.048f
        val petals = 8
        val steps = 80
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseR + waveAmp * cos(petals * theta).toFloat()
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        pathCache.put(key, path)
        return path
    }

    fun getRoundedHexagonPath(width: Float, height: Float): Path {
        val wKey = (width * 2f).roundToInt()
        val hKey = (height * 2f).roundToInt()
        val key = "hex_${wKey}_$hKey"

        pathCache.get(key)?.let { return it }

        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        val rx = width * 0.48f
        val ry = height * 0.48f
        val steps = 72
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val hexMod = 0.93f + 0.07f * cos(6.0 * theta).toFloat()
            val x = cx + rx * hexMod * cos(theta).toFloat()
            val y = cy + ry * hexMod * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        pathCache.put(key, path)
        return path
    }

    fun getOrganicPebblePath(width: Float, height: Float): Path {
        val wKey = (width * 2f).roundToInt()
        val hKey = (height * 2f).roundToInt()
        val key = "peb_${wKey}_$hKey"

        pathCache.get(key)?.let { return it }

        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        val rx = width * 0.47f
        val ry = height * 0.47f
        val steps = 72
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val asym = 1.0f + 0.045f * sin(2.0 * theta + 0.6).toFloat() - 0.025f * cos(3.0 * theta).toFloat()
            val x = cx + rx * asym * cos(theta).toFloat()
            val y = cy + ry * asym * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        pathCache.put(key, path)
        return path
    }
}

/**
 * True Superellipse Squircle Shape:
 *
 * Implements the Lamé curve / superellipse equation:
 *   |x / a|^n + |y / b|^n = 1
 *
 * Fully optimized with hardware acceleration and [SquirclePathCache] to achieve
 * lock-solid 120fps animations without Garbage Collection pressure.
 */
@Immutable
class SquircleShape(
    val exponent: Float = 4.0f,
    val preset: IconShapePreset = IconShapePreset.SQUIRCLE
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
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
                val normalized = ((exponent.coerceIn(2f, 10f) - 2f) / 8f)
                val fraction = (0.45f - normalized * 0.27f).coerceIn(0.14f, 0.48f)
                val r = min(size.width, size.height) * fraction
                Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }

            IconShapePreset.TEARDROP -> {
                val rLarge = min(size.width, size.height) * 0.48f
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
                Outline.Generic(
                    SquirclePathCache.getSuperellipsePath(
                        size.width,
                        size.height,
                        exponent.coerceIn(2.0f, 10.0f)
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
