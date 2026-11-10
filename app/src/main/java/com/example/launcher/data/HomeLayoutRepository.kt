package com.example.launcher.data

import com.example.launcher.data.db.AppUsageEntity
import com.example.launcher.data.db.HomeCellItemType
import com.example.launcher.data.db.HomeContainerType
import com.example.launcher.data.db.HomeDao
import com.example.launcher.data.db.HomeItemEntity
import com.example.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HomeLayoutRepository(
    private val homeDao: HomeDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val homeItemsFlow: Flow<List<HomeItemEntity>> = homeDao.observeAllHomeItems()
    val appUsageFlow: Flow<List<AppUsageEntity>> = homeDao.observeAppUsageStats()

    /**
     * Populates a Pixel-like initial home layout (4-icon hotseat + curated workspace icons & sample folder)
     * on first run when the Room database is empty.
     */
    suspend fun ensureSeededWithApps(apps: List<AppInfo>, dockCount: Int = 4, columns: Int = 5) {
        if (apps.isEmpty()) return
        withContext(ioDispatcher) {
            if (homeDao.getHomeItemCount() > 0) return@withContext

            val personalApps = apps.filter { !it.isWorkProfile }.ifEmpty { apps }
            val seeded = mutableListOf<HomeItemEntity>()

            // 1. Hotseat (Dock)
            val preferredDockKeywords = listOf(
                listOf("dialer", "phone", "contacts", "telecom"),
                listOf("messaging", "mms", "messages", "chat", "sms"),
                listOf("chrome", "browser", "webview", "internet"),
                listOf("camera", "photos", "gallery", "music")
            )

            val chosenDockApps = mutableListOf<AppInfo>()
            for (keywords in preferredDockKeywords) {
                val match = personalApps.firstOrNull { app ->
                    app !in chosenDockApps && keywords.any { kw ->
                        app.packageName.contains(kw, ignoreCase = true) ||
                            app.label.contains(kw, ignoreCase = true)
                    }
                }
                if (match != null) {
                    chosenDockApps.add(match)
                }
            }
            for (app in personalApps) {
                if (chosenDockApps.size >= dockCount) break
                if (app !in chosenDockApps) chosenDockApps.add(app)
            }

            chosenDockApps.take(dockCount).forEachIndexed { index, app ->
                seeded.add(
                    HomeItemEntity(
                        container = HomeContainerType.HOTSEAT.name,
                        page = 0,
                        cellX = index,
                        cellY = 0,
                        itemType = HomeCellItemType.APP.name,
                        componentKey = app.componentKey,
                        title = app.label
                    )
                )
            }

            // 2. Workspace Page 0
            val remainingApps = personalApps.filter { it !in chosenDockApps }
            val page0Row = 4
            remainingApps.take(columns - 1).forEachIndexed { colIndex, app ->
                seeded.add(
                    HomeItemEntity(
                        container = HomeContainerType.WORKSPACE.name,
                        page = 0,
                        cellX = colIndex,
                        cellY = page0Row,
                        itemType = HomeCellItemType.APP.name,
                        componentKey = app.componentKey,
                        title = app.label
                    )
                )
            }

            val folderCandidates = remainingApps.drop(columns - 1).take(4)
            if (folderCandidates.size >= 2) {
                seeded.add(
                    HomeItemEntity(
                        container = HomeContainerType.WORKSPACE.name,
                        page = 0,
                        cellX = columns - 1,
                        cellY = page0Row,
                        itemType = HomeCellItemType.FOLDER.name,
                        title = "Essentials",
                        folderMemberKeysCsv = folderCandidates.joinToString(",") { it.componentKey }
                    )
                )
            } else if (folderCandidates.size == 1) {
                val single = folderCandidates.first()
                seeded.add(
                    HomeItemEntity(
                        container = HomeContainerType.WORKSPACE.name,
                        page = 0,
                        cellX = columns - 1,
                        cellY = page0Row,
                        itemType = HomeCellItemType.APP.name,
                        componentKey = single.componentKey,
                        title = single.label
                    )
                )
            }

            homeDao.insertHomeItems(seeded)
        }
    }

    suspend fun recordAppLaunch(componentKey: String) {
        withContext(ioDispatcher) {
            val current = homeDao.getAppUsage(componentKey)
            val updated = AppUsageEntity(
                componentKey = componentKey,
                launchCount = (current?.launchCount ?: 0) + 1,
                lastLaunchTimestamp = System.currentTimeMillis()
            )
            homeDao.upsertAppUsage(updated)
        }
    }

    suspend fun addAppToHome(app: AppInfo, page: Int, cellX: Int, cellY: Int) {
        withContext(ioDispatcher) {
            homeDao.insertHomeItem(
                HomeItemEntity(
                    container = HomeContainerType.WORKSPACE.name,
                    page = page,
                    cellX = cellX,
                    cellY = cellY,
                    itemType = HomeCellItemType.APP.name,
                    componentKey = app.componentKey,
                    title = app.label
                )
            )
        }
    }

    /**
     * Commits a drag-and-drop placement onto the workspace or hotseat, including
     * optional neighbor reflow displacement.
     */
    suspend fun commitDragDropMove(
        draggedApp: AppInfo,
        sourceHomeItemId: Long?,
        targetContainer: HomeContainerType,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
        reflowNeighborMoves: Map<Long, Pair<Int, Int>> = emptyMap()
    ) {
        withContext(ioDispatcher) {
            val allItems = homeDao.getAllHomeItemsOnce()

            // 1. Apply any neighbor reflow displacements first
            for ((neighborId, newCoords) in reflowNeighborMoves) {
                val neighbor = allItems.firstOrNull { it.id == neighborId } ?: continue
                homeDao.updateHomeItem(
                    neighbor.copy(
                        cellX = newCoords.first,
                        cellY = newCoords.second
                    )
                )
            }

            // 2. If dropping onto a hotseat slot that is already occupied, swap or replace
            if (targetContainer == HomeContainerType.HOTSEAT) {
                val existingHotseatItem = allItems.firstOrNull {
                    it.container == HomeContainerType.HOTSEAT.name &&
                        it.cellX == targetCellX &&
                        it.id != sourceHomeItemId
                }
                val sourceItem = sourceHomeItemId?.let { id -> allItems.firstOrNull { it.id == id } }
                if (existingHotseatItem != null) {
                    if (sourceItem != null) {
                        homeDao.updateHomeItem(
                            existingHotseatItem.copy(
                                container = sourceItem.container,
                                page = sourceItem.page,
                                cellX = sourceItem.cellX,
                                cellY = sourceItem.cellY
                            )
                        )
                    } else {
                        homeDao.deleteHomeItemById(existingHotseatItem.id)
                    }
                }
            }

            // 3. Move existing item or insert new item from drawer
            val existingSource = sourceHomeItemId?.let { id -> allItems.firstOrNull { it.id == id } }
            if (existingSource != null) {
                homeDao.updateHomeItem(
                    existingSource.copy(
                        container = targetContainer.name,
                        page = if (targetContainer == HomeContainerType.HOTSEAT) 0 else targetPage,
                        cellX = targetCellX,
                        cellY = if (targetContainer == HomeContainerType.HOTSEAT) 0 else targetCellY
                    )
                )
            } else {
                homeDao.insertHomeItem(
                    HomeItemEntity(
                        container = targetContainer.name,
                        page = if (targetContainer == HomeContainerType.HOTSEAT) 0 else targetPage,
                        cellX = targetCellX,
                        cellY = if (targetContainer == HomeContainerType.HOTSEAT) 0 else targetCellY,
                        itemType = HomeCellItemType.APP.name,
                        componentKey = draggedApp.componentKey,
                        title = draggedApp.label
                    )
                )
            }
        }
    }

    /**
     * Merges a dragged app with an existing workspace icon or folder to form or expand a Folder.
     */
    suspend fun mergeIntoFolder(
        draggedApp: AppInfo,
        sourceHomeItemId: Long?,
        targetItem: HomeItemEntity
    ) {
        withContext(ioDispatcher) {
            if (sourceHomeItemId != null && sourceHomeItemId == targetItem.id) return@withContext

            // Remove the source item if it came from the workspace/hotseat
            if (sourceHomeItemId != null) {
                homeDao.deleteHomeItemById(sourceHomeItemId)
            }

            if (targetItem.itemType == HomeCellItemType.FOLDER.name) {
                val updatedMembers = (targetItem.folderMemberKeys + draggedApp.componentKey).distinct()
                homeDao.updateHomeItem(
                    targetItem.copy(
                        folderMemberKeysCsv = updatedMembers.joinToString(",")
                    )
                )
            } else if (targetItem.itemType == HomeCellItemType.APP.name) {
                val members = listOf(targetItem.componentKey, draggedApp.componentKey).distinct()
                homeDao.updateHomeItem(
                    targetItem.copy(
                        itemType = HomeCellItemType.FOLDER.name,
                        title = draggedApp.category.displayName,
                        folderMemberKeysCsv = members.joinToString(",")
                    )
                )
            }
        }
    }

    suspend fun renameFolder(folderId: Long, newTitle: String) {
        withContext(ioDispatcher) {
            val item = homeDao.getAllHomeItemsOnce().firstOrNull { it.id == folderId } ?: return@withContext
            homeDao.updateHomeItem(item.copy(title = newTitle.trim().ifEmpty { "Folder" }))
        }
    }

    suspend fun updateFolderMembers(folderId: Long, memberKeys: List<String>) {
        withContext(ioDispatcher) {
            val item = homeDao.getAllHomeItemsOnce().firstOrNull { it.id == folderId } ?: return@withContext
            val distinct = memberKeys.distinct()
            if (distinct.isEmpty()) {
                homeDao.deleteHomeItemById(folderId)
            } else if (distinct.size == 1) {
                // Unpack single remaining app back to a regular workspace icon
                homeDao.updateHomeItem(
                    item.copy(
                        itemType = HomeCellItemType.APP.name,
                        componentKey = distinct.first(),
                        folderMemberKeysCsv = ""
                    )
                )
            } else {
                homeDao.updateHomeItem(
                    item.copy(folderMemberKeysCsv = distinct.joinToString(","))
                )
            }
        }
    }

    suspend fun addWidgetToWorkspace(
        componentKey: String,
        appWidgetId: Int,
        title: String,
        page: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    ) {
        withContext(ioDispatcher) {
            homeDao.insertHomeItem(
                HomeItemEntity(
                    container = HomeContainerType.WORKSPACE.name,
                    page = page,
                    cellX = cellX,
                    cellY = cellY,
                    spanX = spanX,
                    spanY = spanY,
                    itemType = HomeCellItemType.WIDGET.name,
                    componentKey = componentKey,
                    title = title,
                    appWidgetId = appWidgetId
                )
            )
        }
    }

    suspend fun resizeWidgetItem(widgetItemId: Long, newSpanX: Int, newSpanY: Int) {
        withContext(ioDispatcher) {
            val item = homeDao.getAllHomeItemsOnce().firstOrNull { it.id == widgetItemId } ?: return@withContext
            homeDao.updateHomeItem(
                item.copy(
                    spanX = newSpanX.coerceIn(1, 5),
                    spanY = newSpanY.coerceIn(1, 4)
                )
            )
        }
    }

    suspend fun removeHomeItem(id: Long) {
        withContext(ioDispatcher) {
            homeDao.deleteHomeItemById(id)
        }
    }

    suspend fun resetHomeLayout(apps: List<AppInfo>, dockCount: Int, columns: Int) {
        withContext(ioDispatcher) {
            homeDao.clearAllHomeItems()
        }
        ensureSeededWithApps(apps, dockCount, columns)
    }
}
