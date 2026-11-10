package com.example.launcher.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class HomeContainerType {
    WORKSPACE,
    HOTSEAT
}

enum class HomeCellItemType {
    APP,
    FOLDER,
    WIDGET,
    SHORTCUT
}

@Immutable
@Entity(tableName = "home_items")
@JsonClass(generateAdapter = true)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val container: String = HomeContainerType.WORKSPACE.name, // WORKSPACE or HOTSEAT
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val itemType: String = HomeCellItemType.APP.name,
    val componentKey: String = "",
    val title: String = "",
    val folderMemberKeysCsv: String = "", // Comma-separated componentKeys when itemType == FOLDER
    val appWidgetId: Int = -1
) {
    val folderMemberKeys: List<String>
        get() = if (folderMemberKeysCsv.isBlank()) emptyList()
        else folderMemberKeysCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Immutable
@Entity(tableName = "app_usage_stats")
@JsonClass(generateAdapter = true)
data class AppUsageEntity(
    @PrimaryKey val componentKey: String,
    val launchCount: Int = 0,
    val lastLaunchTimestamp: Long = 0L
)
