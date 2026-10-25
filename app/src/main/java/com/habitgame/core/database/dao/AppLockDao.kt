package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.AppLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_locks ORDER BY appName ASC")
    fun observeAllApps(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_locks WHERE isUnlocked = 0 ORDER BY appName ASC")
    fun observeLockedApps(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_locks WHERE isUnlocked = 1")
    fun observeUnlockedApps(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_locks WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): AppLockEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: AppLockEntity)

    @Update
    suspend fun updateApp(app: AppLockEntity)

    @Query("UPDATE app_locks SET isUnlocked = 1, unlockedAt = :unlockedAt, unlockDurationMinutes = :minutes, coinCost = :cost WHERE packageName = :packageName")
    suspend fun unlockApp(packageName: String, minutes: Int, cost: Long, unlockedAt: Long = System.currentTimeMillis())

    @Query("UPDATE app_locks SET isUnlocked = 0, unlockedAt = NULL, unlockDurationMinutes = 0 WHERE packageName = :packageName")
    suspend fun lockApp(packageName: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<AppLockEntity>)

    @Query("DELETE FROM app_locks")
    suspend fun deleteAll()

    @Query("SELECT * FROM app_locks")
    suspend fun getAll(): List<AppLockEntity>

    @Query("DELETE FROM app_locks WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    /** Unlocks every locked app for a while (Shop "App Unlock" / "Entertainment"). */
    @Query("UPDATE app_locks SET isUnlocked = 1, unlockedAt = :unlockedAt, unlockDurationMinutes = :minutes")
    suspend fun unlockAll(minutes: Int, unlockedAt: Long = System.currentTimeMillis())
}

