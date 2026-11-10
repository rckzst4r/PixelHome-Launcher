package com.example.launcher.drag

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.launcher.animation.LauncherPhysics
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.icon.IconCache
import com.example.launcher.icon.SquircleShape
import com.example.launcher.model.AppInfo
import com.example.launcher.model.LauncherSettings
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class TopDropTarget {
    NONE,
    REMOVE,
    APP_INFO,
    UNINSTALL
}

enum class WorkspaceHoverMode {
    EMPTY_CELL,
    REFLOW_NEIGHBOR,
    MERGE_FOLDER,
    HOTSEAT_SLOT
}

/**
 * Immutable snapshot of an active workspace or drawer drag-and-drop session.
 *
 * Drag & Drop Geometry and Reflow Math:
 * 1. When a long-press moves past 14dp, the shortcut popup dismisses and `DragSessionState` activates.
 * 2. The finger position `fingerWindowOffset` is mapped to either:
 *    - Top Drop Target Bar (`y < topDropZoneBottomPx`): selects `REMOVE`, `APP_INFO`, or `UNINSTALL`
 *      based on horizontal thirds of the screen width.
 *    - Hotseat Dock (`y > hotseatTopPx`): maps `x` into `0 until dockIconCount`.
 *    - Workspace Grid (`cellX in 0 until cols`, `cellY in startRow until rows`):
 *      - If the target cell is occupied by another icon/folder, we compute the normalized radial distance
 *        from the cell's center:
 *        `r = hypot(dx, dy) / cellRadius`
 *        - Inner core (`r < 0.46f`): triggers `MERGE_FOLDER` (creating a folder or adding to it).
 *        - Outer ring (`r >= 0.46f`): triggers `REFLOW_NEIGHBOR` (calculates the nearest unoccupied
 *          cell on the page via spiral Manhattan distance search and animates the neighbor into that cell).
 */
@Immutable
data class DragSessionState(
    val draggedApp: AppInfo,
    val sourceHomeItemId: Long? = null, // null if dragged out of App Drawer
    val sourcePage: Int = 0,
    val sourceCellX: Int = -1,
    val sourceCellY: Int = -1,
    val initialIconCenter: Offset = Offset.Zero,
    val fingerWindowOffset: Offset = Offset.Zero,
    val hoveredTopTarget: TopDropTarget = TopDropTarget.NONE,
    val hoveredCellX: Int = -1,
    val hoveredCellY: Int = -1,
    val hoveredHotseatIndex: Int = -1,
    val hoverMode: WorkspaceHoverMode = WorkspaceHoverMode.EMPTY_CELL,
    val reflowPreviewOffsets: Map<Long, IntOffset> = emptyMap() // HomeItemEntity.id -> target (cellX, cellY)
)

object DragDropMath {

    /**
     * Finds the nearest unoccupied `(cellX, cellY)` on the current workspace page to displace
     * an existing neighbor when the dragged icon hovers over its outer ring.
     */
    fun findNearestFreeCell(
        targetX: Int,
        targetY: Int,
        cols: Int,
        rows: Int,
        startRow: Int,
        occupiedCells: Set<Pair<Int, Int>>,
        freedSourceCell: Pair<Int, Int>?
    ): Pair<Int, Int>? {
        var bestCell: Pair<Int, Int>? = null
        var bestDist = Float.MAX_VALUE

        for (r in startRow until rows) {
            for (c in 0 until cols) {
                if (c == targetX && r == targetY) continue
                val candidate = c to r
                val isFree = candidate !in occupiedCells || candidate == freedSourceCell
                if (isFree) {
                    val dist = hypot((c - targetX).toFloat(), (r - targetY).toFloat())
                    if (dist < bestDist) {
                        bestDist = dist
                        bestCell = candidate
                    }
                }
            }
        }
        return bestCell
    }

    /**
     * Computes live neighbor reflow target coordinates `(cellX, cellY)` for each item on the page
     * while the user hovers in `REFLOW_NEIGHBOR` mode.
     */
    fun computeReflowMap(
        pageItems: List<HomeItemEntity>,
        draggedItemId: Long?,
        sourceCell: Pair<Int, Int>?,
        hoveredCellX: Int,
        hoveredCellY: Int,
        hoverMode: WorkspaceHoverMode,
        cols: Int,
        rows: Int,
        startRow: Int
    ): Map<Long, IntOffset> {
        if (hoverMode != WorkspaceHoverMode.REFLOW_NEIGHBOR || hoveredCellX < 0 || hoveredCellY < 0) {
            return emptyMap()
        }
        val occupant = pageItems.firstOrNull {
            it.id != draggedItemId && it.cellX == hoveredCellX && it.cellY == hoveredCellY
        } ?: return emptyMap()

        val occupiedSet = pageItems
            .filter { it.id != draggedItemId }
            .map { it.cellX to it.cellY }
            .toSet()

        val displacedCell = findNearestFreeCell(
            targetX = hoveredCellX,
            targetY = hoveredCellY,
            cols = cols,
            rows = rows,
            startRow = startRow,
            occupiedCells = occupiedSet,
            freedSourceCell = sourceCell
        ) ?: return emptyMap()

        return mapOf(occupant.id to IntOffset(displacedCell.first, displacedCell.second))
    }
}

/**
 * Top bar with `Remove`, `App info`, and `Uninstall` drop targets that slides in from the top
 * of the screen with spring physics during an active drag session.
 */
@Composable
fun TopDragDropTargetBar(
    visible: Boolean,
    hoveredTarget: TopDropTarget,
    showRemoveTarget: Boolean,
    settings: LauncherSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = LauncherPhysics.springSpec(settings, context),
            initialOffsetY = { -it }
        ) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = LauncherPhysics.springSpec(settings, context),
            targetOffsetY = { -it }
        ) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("top_drag_drop_target_bar")
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 10.dp)
            ) {
                if (showRemoveTarget) {
                    DropTargetPill(
                        icon = Icons.Filled.Close,
                        label = "Remove",
                        isHovered = hoveredTarget == TopDropTarget.REMOVE,
                        activeColor = MaterialTheme.colorScheme.secondaryContainer,
                        onActiveColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        settings = settings,
                        modifier = Modifier.weight(1f)
                    )
                }

                DropTargetPill(
                    icon = Icons.Filled.Info,
                    label = "App info",
                    isHovered = hoveredTarget == TopDropTarget.APP_INFO,
                    activeColor = MaterialTheme.colorScheme.primaryContainer,
                    onActiveColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    settings = settings,
                    modifier = Modifier.weight(1f)
                )

                DropTargetPill(
                    icon = Icons.Filled.Delete,
                    label = "Uninstall",
                    isHovered = hoveredTarget == TopDropTarget.UNINSTALL,
                    activeColor = MaterialTheme.colorScheme.errorContainer,
                    onActiveColor = MaterialTheme.colorScheme.onErrorContainer,
                    settings = settings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DropTargetPill(
    icon: ImageVector,
    label: String,
    isHovered: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    settings: LauncherSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1.0f,
        animationSpec = LauncherPhysics.springSpec(settings, context),
        label = "DropTargetHoverScale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isHovered) {
            activeColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isHovered) onActiveColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isHovered) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isHovered) onActiveColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Renders the floating lifted squircle icon (`scale = 1.18f` + dynamic shadow) following the user's
 * finger in `graphicsLayer` with zero layout recomposition.
 */
@Composable
fun FloatingDraggedIconOverlay(
    dragSession: DragSessionState?,
    iconCache: IconCache,
    settings: LauncherSettings
) {
    if (dragSession == null) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val app = dragSession.draggedApp

    var iconLayers by remember(app.componentKey) {
        mutableStateOf(iconCache.getCachedIfPresent(app.componentKey))
    }
    LaunchedEffect(app.componentKey) {
        if (iconLayers == null) {
            iconLayers = iconCache.getOrLoadIcon(app)
        }
    }

    val liftScale by animateFloatAsState(
        targetValue = 1.18f,
        animationSpec = LauncherPhysics.springSpec(settings, context),
        label = "DragLiftScale"
    )

    val squircleShape = remember(settings.squircleExponent, settings.iconShape) {
        SquircleShape(
            exponent = settings.squircleExponent,
            preset = settings.iconShape
        )
    }

    val iconSizeDp = (56.dp * settings.iconSizeScale.coerceIn(0.5f, 1.3f))
    val halfSizePx = with(density) { (iconSizeDp / 2f).toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSizeDp)
                .graphicsLayer {
                    translationX = dragSession.fingerWindowOffset.x - halfSizePx
                    translationY = dragSession.fingerWindowOffset.y - halfSizePx
                    scaleX = liftScale
                    scaleY = liftScale
                }
                .shadow(
                    elevation = 16.dp,
                    shape = squircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .graphicsLayer {
                    clip = true
                    shape = squircleShape
                }
                .background(Color(app.accentTintSeed))
                .testTag("floating_dragged_icon")
        ) {
            iconLayers?.compositeBitmap?.let { composite ->
                Image(
                    bitmap = composite,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
