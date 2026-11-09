package com.example.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    @Query("SELECT * FROM home_items ORDER BY container ASC, page ASC, cellY ASC, cellX ASC")
    fun observeAllHomeItems(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    suspend fun getAllHomeItemsOnce(): List<HomeItemEntity>

    @Query("SELECT COUNT(*) FROM home_items")
    suspend fun getHomeItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeItem(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeItems(items: List<HomeItemEntity>)

    @Update
    suspend fun updateHomeItem(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun deleteHomeItemById(id: Long)

    @Query("DELETE FROM home_items")
    suspend fun clearAllHomeItems()

    @Query("SELECT * FROM app_usage_stats")
    fun observeAppUsageStats(): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage_stats WHERE componentKey = :componentKey LIMIT 1")
    suspend fun getAppUsage(componentKey: String): AppUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppUsage(entity: AppUsageEntity)
}
