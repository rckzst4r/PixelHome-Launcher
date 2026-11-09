package com.example.launcher.ui.components

import android.graphics.Rect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.launcher.icon.CachedAppIconLayers
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.IconPackManager
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AppColorPaletteMode
import com.example.launcher.model.AppInfo
import com.example.launcher.model.BadgeStyle
import com.example.launcher.model.FolderPreviewStyle
import com.example.launcher.model.LabelColorMode
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.MiuiIconFallbackMode
import com.example.ui.theme.parseHexColorSafe
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Plain non-observable holder for [LayoutCoordinates] so scrolling in `LazyVerticalGrid` or
 * `HorizontalPager` never triggers Snapshot state writes or recompositions on `onGloballyPositioned`.
 * Screen bounds are computed lazily only when the user actually taps, long-presses, or drags an icon.
 */
private class IconLayoutCoordsHolder(var coords: LayoutCoordinates? = null) {
    fun resolveScreenRect(): Rect {
        val c = coords
        if (c == null || !c.isAttached) return Rect(200, 400, 360, 560)
        return try {
            val b = c.boundsInWindow()
            if (!b.left.isFinite() || !b.top.isFinite() || !b.right.isFinite() || !b.bottom.isFinite()) {
                Rect(200, 400, 360, 560)
            } else {
                Rect(
                    b.left.roundToInt(),
                    b.top.roundToInt(),
                    b.right.roundToInt(),
                    b.bottom.roundToInt()
                )
            }
        } catch (_: Throwable) {
            Rect(200, 400, 360, 560)
        }
    }
}

/**
 * Builds a GPU-accelerated Duotone [ColorFilter] that maps dark luminance pixels of any multi-color
 * or Xiaomi/MIUI icon to [plateColor] and bright luminance pixels (the logo/glyph) to [glyphColor].
 * This preserves all internal contrast, lines, and details of MIUI/non-monochrome icons instead of
 * flattening them into a solid square.
 */
internal fun createDuotoneColorFilter(plateColor: Color, glyphColor: Color): ColorFilter {
    val dr = (glyphColor.red - plateColor.red)
    val dg = (glyphColor.green - plateColor.green)
    val db = (glyphColor.blue - plateColor.blue)

    val lr = 0.299f
    val lg = 0.587f
    val lb = 0.114f

    val matrix = ColorMatrix(
        floatArrayOf(
            dr * lr, dr * lg, dr * lb, 0f, plateColor.red * 255f,
            dg * lr, dg * lg, dg * lb, 0f, plateColor.green * 255f,
            db * lr, db * lg, db * lb, 0f, plateColor.blue * 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorFilter.colorMatrix(matrix)
}

/**
 * Renders an application icon inside the mathematical [SquircleShape] engine.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquircleAppIcon(
    app: AppInfo,
    iconCache: IconCache,
    settings: LauncherSettings,
    showLabel: Boolean,
    useThemedIcon: Boolean,
    baseIconSize: Dp = 56.dp,
    enableLabelShadow: Boolean = true,
    isBeingDragged: Boolean = false,
    isFolderMergeHighlighted: Boolean = false,
    onClick: (AppInfo, Rect) -> Unit,
    onLongClick: ((AppInfo, Rect) -> Unit)? = null,
    onDragStartFromIcon: ((AppInfo, Rect, Offset) -> Unit)? = null,
    onDragMoveWindowOffset: ((Offset) -> Unit)? = null,
    onDragFinish: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPressed: Boolean
    val pressScale: Float
    val interactionSource = remember { MutableInteractionSource() }

    if (enableLabelShadow || isFolderMergeHighlighted) {
        val pressedState by interactionSource.collectIsPressedAsState()
        isPressed = pressedState
        val targetScale = when {
            isFolderMergeHighlighted -> 1.16f
            pressedState -> 0.90f
            else -> 1.0f
        }
        val animatedScale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = spring(
                dampingRatio = (settings.springDampingRatio * 0.72f).coerceIn(0.50f, 0.88f),
                stiffness = (settings.springStiffness * 1.45f).coerceIn(320f, 1600f)
            ),
            label = "IconPressSpring"
        )
        pressScale = animatedScale
    } else {
        // Fast path for App Drawer scrolling: zero coroutine allocations per grid item
        isPressed = false
        pressScale = 1.0f
    }

    var iconLayers by remember(app.componentKey, settings.iconPackPackage) {
        mutableStateOf(iconCache.getCachedIfPresent(app.componentKey, settings.iconPackPackage))
    }

    if (iconLayers == null) {
        LaunchedEffect(app.componentKey, settings.iconPackPackage) {
            iconLayers = iconCache.getOrLoadIcon(app, settings.iconPackPackage)
        }
    }

    val coordsHolder = remember { IconLayoutCoordsHolder() }

    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(
            exponent = settings.squircleExponent,
            preset = settings.iconShape
        )
    }

    val effectiveIconSize = (baseIconSize * settings.iconSizeScale.coerceIn(0.5f, 1.3f))
    val customOverride = settings.customAppOverrides[app.componentKey]
    val displayLabel = customOverride?.customLabel?.takeIf { it.isNotBlank() } ?: app.label

    val labelColor = if (!enableLabelShadow) {
        MaterialTheme.colorScheme.onSurface
    } else {
        when (settings.labelColorMode) {
            LabelColorMode.WALLPAPER_ADAPTIVE,
            LabelColorMode.LIGHT -> Color(0xFFF8F9FA)
            LabelColorMode.DARK -> Color(0xFF1A1C1E)
            LabelColorMode.DYNAMIC_ACCENT -> MaterialTheme.colorScheme.primary
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .widthIn(min = 48.dp)
            .then(
                if (isBeingDragged) {
                    Modifier.graphicsLayer { alpha = 0.22f }
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = displayLabel }
            .testTag("app_icon_${app.packageName}")
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(app, coordsHolder.resolveScreenRect()) },
                onLongClick = {
                    onLongClick?.invoke(app, coordsHolder.resolveScreenRect())
                }
            )
            .then(
                if (onDragStartFromIcon != null && onDragMoveWindowOffset != null && onDragFinish != null) {
                    Modifier.pointerInput(app.componentKey, settings.lockHomeLayout) {
                        var dragEngaged = false
                        var accumulatedDrag = Offset.Zero
                        var currentWindowPos = Offset.Zero
                        var startRect = Rect(200, 400, 360, 560)

                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                dragEngaged = false
                                accumulatedDrag = Offset.Zero
                                startRect = coordsHolder.resolveScreenRect()
                                currentWindowPos = Offset(
                                    startRect.left + localOffset.x,
                                    startRect.top + localOffset.y
                                )
                                onLongClick?.invoke(app, startRect)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount
                                currentWindowPos += dragAmount
                                if (!dragEngaged && hypot(accumulatedDrag.x, accumulatedDrag.y) > 14.dp.toPx()) {
                                    dragEngaged = true
                                    onDragStartFromIcon(app, startRect, currentWindowPos)
                                }
                                if (dragEngaged) {
                                    onDragMoveWindowOffset(currentWindowPos)
                                }
                            },
                            onDragEnd = {
                                if (dragEngaged) {
                                    onDragFinish(false)
                                }
                                dragEngaged = false
                            },
                            onDragCancel = {
                                if (dragEngaged) {
                                    onDragFinish(true)
                                }
                                dragEngaged = false
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(effectiveIconSize)
                .onGloballyPositioned { coords ->
                    coordsHolder.coords = coords
                }
        ) {
            if (isFolderMergeHighlighted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                            clip = true
                            shape = squircleShape
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (settings.iconShadowEnabled) {
                            Modifier.shadow(
                                elevation = 4.dp,
                                shape = squircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.35f),
                                spotColor = Color.Black.copy(alpha = 0.35f)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                        clip = true
                        shape = squircleShape
                    }
                    .then(
                        if (isPressed) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.45f),
                                shape = squircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                IconLayerContent(
                    layers = iconLayers,
                    app = app,
                    settings = settings,
                    useThemedIcon = useThemedIcon,
                    customColorHex = customOverride?.customIconColorHex,
                    customGlyphHex = customOverride?.customGlyphColorHex
                )
            }

            // Notification Badge (top-right squircle dot or count)
            if (settings.badgeStyle != BadgeStyle.OFF && app.notificationCount > 0) {
                val badgeShape = remember(settings.squircleExponent) {
                    SquircleShape(exponent = settings.squircleExponent)
                }
                val isCount = settings.badgeStyle == BadgeStyle.COUNT
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(if (isCount) 20.dp else 13.dp)
                        .graphicsLayer {
                            clip = true
                            shape = badgeShape
                        }
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .testTag("notification_badge_${app.packageName}")
                ) {
                    if (isCount) {
                        Text(
                            text = app.notificationCount.coerceAtMost(99).toString(),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                    }
                }
            }

            // Work profile briefcase badge (bottom-right)
            if (app.isWorkProfile) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(18.dp)
                        .graphicsLayer {
                            clip = true
                            shape = squircleShape
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Work,
                        contentDescription = "Work profile app",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = settings.labelFontSizeSp.sp,
                    color = labelColor,
                    shadow = if (enableLabelShadow && (
                            settings.labelColorMode == LabelColorMode.WALLPAPER_ADAPTIVE ||
                                settings.labelColorMode == LabelColorMode.LIGHT
                            )
                    ) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 4f
                        )
                    } else {
                        null
                    }
                ),
                maxLines = settings.labelMaxLines.coerceIn(1, 2),
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StyledAppIconBox(
    layers: CachedAppIconLayers,
    plateColor: Color,
    glyphColor: Color,
    fallbackMode: MiuiIconFallbackMode
) {
    val hasRealMono = layers.hasRealMonochrome && layers.monochromeBitmap != null
    val duotoneFilter = remember(plateColor, glyphColor) {
        createDuotoneColorFilter(plateColor, glyphColor)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(plateColor)
    ) {
        if (hasRealMono) {
            Image(
                bitmap = layers.monochromeBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(glyphColor),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            when (fallbackMode) {
                MiuiIconFallbackMode.DUOTONE_TINT -> {
                    Image(
                        bitmap = layers.compositeBitmap,
                        contentDescription = null,
                        contentScale = if (layers.isAdaptive || layers.isFullBleedLegacy) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        },
                        colorFilter = duotoneFilter,
                        modifier = Modifier.fillMaxSize(
                            if (layers.isAdaptive || layers.isFullBleedLegacy) 1f else 0.78f
                        )
                    )
                }

                MiuiIconFallbackMode.SAFE_PLATE -> {
                    Image(
                        bitmap = layers.foregroundBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.76f)
                    )
                }

                MiuiIconFallbackMode.FULL_BLEED_ORIGINAL -> {
                    Image(
                        bitmap = layers.compositeBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
internal fun IconLayerContent(
    layers: CachedAppIconLayers?,
    app: AppInfo,
    settings: LauncherSettings,
    useThemedIcon: Boolean,
    customColorHex: String? = null,
    customGlyphHex: String? = null
) {
    if (layers == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(app.accentTintSeed))
        )
        return
    }

    val iconPackPackage = settings.iconPackPackage
    val legacyPlateHex = settings.legacyIconPlateHex

    // 1. Per-app custom color override (Background Plate and/or Foreground Glyph Tint)
    if (!customColorHex.isNullOrBlank() || !customGlyphHex.isNullOrBlank()) {
        val customBg = if (!customColorHex.isNullOrBlank()) {
            parseHexColorSafe(customColorHex, Color(layers.dominantSeedColor))
        } else {
            Color(layers.dominantSeedColor)
        }
        val customGlyph = if (!customGlyphHex.isNullOrBlank()) {
            parseHexColorSafe(customGlyphHex, Color.White)
        } else {
            Color.White
        }
        StyledAppIconBox(
            layers = layers,
            plateColor = customBg,
            glyphColor = customGlyph,
            fallbackMode = settings.miuiIconFallbackMode
        )
        return
    }

    // 2. Global App Color Palette Modes (`CUSTOM_PALETTE`, `ACCENT_HARMONIZED`, `MATERIAL_YOU`)
    when (settings.appColorPaletteMode) {
        AppColorPaletteMode.CUSTOM_PALETTE -> {
            val globalPlate = parseHexColorSafe(settings.globalAppPlateColorHex, Color(0xFF1B2234))
            val globalGlyph = parseHexColorSafe(settings.globalAppGlyphColorHex, Color(0xFF7AB8FF))
            StyledAppIconBox(
                layers = layers,
                plateColor = globalPlate,
                glyphColor = globalGlyph,
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }

        AppColorPaletteMode.ACCENT_HARMONIZED -> {
            val targetAccent = parseHexColorSafe(settings.accentColorHex, MaterialTheme.colorScheme.primary)
            val appSeed = Color(layers.dominantSeedColor)
            val blendFactor = settings.appColorHarmonyIntensity.coerceIn(0.15f, 0.95f)
            val harmonizedPlate = lerp(appSeed, targetAccent, blendFactor)
            StyledAppIconBox(
                layers = layers,
                plateColor = harmonizedPlate,
                glyphColor = Color.White,
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }

        AppColorPaletteMode.MATERIAL_YOU -> {
            val plateColor = MaterialTheme.colorScheme.secondaryContainer
            val tintColor = MaterialTheme.colorScheme.onSecondaryContainer
            StyledAppIconBox(
                layers = layers,
                plateColor = plateColor,
                glyphColor = tintColor,
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }

        AppColorPaletteMode.ORIGINAL -> Unit
    }

    // 3. Built-in Studio Icon Packs (Neon, Pastel, Obsidian)
    when (iconPackPackage) {
        IconPackManager.BUILTIN_NEON_PACK -> {
            val neonAccent = Color(app.accentTintSeed)
            StyledAppIconBox(
                layers = layers,
                plateColor = Color(0xFF0E1118),
                glyphColor = neonAccent,
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }
        IconPackManager.BUILTIN_PASTEL_PACK -> {
            val seed = Color(app.accentTintSeed)
            val pastelPlate = Color(
                red = (seed.red * 0.35f + 0.65f).coerceIn(0f, 1f),
                green = (seed.green * 0.35f + 0.65f).coerceIn(0f, 1f),
                blue = (seed.blue * 0.35f + 0.65f).coerceIn(0f, 1f)
            )
            StyledAppIconBox(
                layers = layers,
                plateColor = pastelPlate,
                glyphColor = Color(0xFF181C24),
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }
        IconPackManager.BUILTIN_OBSIDIAN_PACK -> {
            StyledAppIconBox(
                layers = layers,
                plateColor = Color(0xFF1A1D24),
                glyphColor = Color(0xFFF1F4F9),
                fallbackMode = settings.miuiIconFallbackMode
            )
            return
        }
    }

    // 4. Android 13+ Material You Themed Icon toggle
    if (useThemedIcon) {
        val plateColor = MaterialTheme.colorScheme.secondaryContainer
        val tintColor = MaterialTheme.colorScheme.onSecondaryContainer
        StyledAppIconBox(
            layers = layers,
            plateColor = plateColor,
            glyphColor = tintColor,
            fallbackMode = settings.miuiIconFallbackMode
        )
    } else if (layers.isAdaptive || layers.isFullBleedLegacy) {
        // Single pre-composited Image draw node for adaptive and full-bleed icons
        Image(
            bitmap = layers.compositeBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Universal Android 7.0+ & Multi-OEM Legacy Icon Normalization
        val plateColor = if (settings.universalLegacyIconNormalize) {
            lerp(Color(layers.dominantSeedColor), Color.White, 0.82f)
        } else {
            parseHexColorSafe(legacyPlateHex, Color(0xFFF3F5FA))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(plateColor)
        ) {
            Image(
                bitmap = layers.foregroundBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquircleFolderTile(
    title: String,
    memberApps: List<AppInfo>,
    iconCache: IconCache,
    settings: LauncherSettings,
    baseIconSize: Dp = 56.dp,
    isFolderMergeHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isFolderMergeHighlighted -> 1.15f
            isPressed -> 0.89f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = (settings.springDampingRatio * 0.68f).coerceIn(0.45f, 0.85f),
            stiffness = Spring.StiffnessMedium
        ),
        label = "FolderPressSpring"
    )

    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(
            exponent = settings.squircleExponent,
            preset = settings.iconShape
        )
    }

    val effectiveSize = baseIconSize * settings.iconSizeScale.coerceIn(0.5f, 1.3f)
    val folderBgColor = parseHexColorSafe(
        settings.folderBackgroundColorHex,
        MaterialTheme.colorScheme.surfaceContainerHigh
    ).copy(alpha = settings.folderBackgroundOpacity.coerceIn(0.3f, 1f))

    val totalNotifications = remember(memberApps) {
        memberApps.sumOf { it.notificationCount }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 48.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("folder_tile_$title")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(effectiveSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            if (isFolderMergeHighlighted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                            clip = true
                            shape = squircleShape
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = squircleShape
                    }
                    .background(folderBgColor)
                    .padding(7.dp)
            ) {
                if (settings.folderPreviewStyle == FolderPreviewStyle.STACK) {
                    val stackApps = memberApps.take(3)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        stackApps.forEachIndexed { idx, app ->
                            val offsetDp = ((idx - 1) * 6).dp
                            MiniFolderPreviewIcon(
                                app = app,
                                iconCache = iconCache,
                                settings = settings,
                                squircleShape = squircleShape,
                                sizeDp = 24.dp,
                                modifier = Modifier.offset(x = offsetDp, y = offsetDp)
                            )
                        }
                    }
                } else {
                    val gridCount = if (settings.folderPreviewStyle == FolderPreviewStyle.GRID_3X3) 3 else 2
                    val previewApps = memberApps.take(gridCount * gridCount)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0 until gridCount) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (c in 0 until gridCount) {
                                    val idx = r * gridCount + c
                                    val app = previewApps.getOrNull(idx)
                                    if (app != null) {
                                        MiniFolderPreviewIcon(
                                            app = app,
                                            iconCache = iconCache,
                                            settings = settings,
                                            squircleShape = squircleShape,
                                            sizeDp = if (gridCount == 2) 17.dp else 11.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(if (gridCount == 2) 17.dp else 11.dp)
                                                .graphicsLayer {
                                                    clip = true
                                                    shape = squircleShape
                                                }
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawCircle(Color.White.copy(alpha = 0.12f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (settings.badgeStyle != BadgeStyle.OFF && totalNotifications > 0) {
                val isCount = settings.badgeStyle == BadgeStyle.COUNT
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(if (isCount) 20.dp else 13.dp)
                        .graphicsLayer {
                            clip = true
                            shape = squircleShape
                        }
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    if (isCount) {
                        Text(
                            text = totalNotifications.coerceAtMost(99).toString(),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        if (settings.showHomeLabels && settings.showFolderTitles) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = settings.labelFontSizeSp.sp,
                    color = Color(0xFFF8F9FA),
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.65f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 4f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MiniFolderPreviewIcon(
    app: AppInfo,
    iconCache: IconCache,
    settings: LauncherSettings,
    squircleShape: SquircleShape,
    sizeDp: Dp,
    modifier: Modifier = Modifier
) {
    var layers by remember(app.componentKey, settings.iconPackPackage) {
        mutableStateOf(iconCache.getCachedIfPresent(app.componentKey, settings.iconPackPackage))
    }
    if (layers == null) {
        LaunchedEffect(app.componentKey, settings.iconPackPackage) {
            layers = iconCache.getOrLoadIcon(app, settings.iconPackPackage)
        }
    }
    val customOverride = settings.customAppOverrides[app.componentKey]
    Box(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer {
                clip = true
                shape = squircleShape
            }
    ) {
        IconLayerContent(
            layers = layers,
            app = app,
            settings = settings,
            useThemedIcon = settings.themedIconsEnabled,
            customColorHex = customOverride?.customIconColorHex,
            customGlyphHex = customOverride?.customGlyphColorHex
        )
    }
}
