package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.entity.RewardSubcategory
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardItemDao {
    @Query("SELECT * FROM reward_items ORDER BY category ASC, cost ASC")
    fun observeAllRewards(): Flow<List<RewardItemEntity>>

    @Query("SELECT * FROM reward_items WHERE subcategory = :subcategory ORDER BY cost ASC")
    fun observeBySubcategory(subcategory: String): Flow<List<RewardItemEntity>>

    @Query("SELECT * FROM reward_items WHERE id = :id")
    suspend fun getById(id: Long): RewardItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReward(reward: RewardItemEntity): Long

    @Update
    suspend fun updateReward(reward: RewardItemEntity)

    @Query("UPDATE reward_items SET isOwned = 1, isLocked = 0 WHERE id = :id")
    suspend fun markOwned(id: Long)

    @Query("UPDATE reward_items SET isActive = 1, activatedAt = :activatedAt, usedMillis = 0 WHERE id = :id")
    suspend fun activateReward(id: Long, activatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reward_items SET isActive = 0 WHERE id = :id")
    suspend fun deactivateReward(id: Long)

    /** Timed unlocks are used up when bought, so they can be bought again. */
    @Query("UPDATE reward_items SET isOwned = 0, isActive = 0, usedMillis = 0 WHERE id = :id")
    suspend fun consumeReward(id: Long)

    /** Counts time spent in locked apps against an App Unlock / Entertainment purchase. */
    @Query("UPDATE reward_items SET usedMillis = usedMillis + :millis WHERE id = :id")
    suspend fun addUsage(id: Long, millis: Long)

    @Query("SELECT COUNT(*) FROM reward_items")
    suspend fun getRewardCount(): Int

    @Query("SELECT COUNT(*) FROM reward_items WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("SELECT * FROM reward_items WHERE boosterEffect = :effect AND isOwned = 1 AND isActive = 1 LIMIT 1")
    suspend fun getActiveBooster(effect: String): RewardItemEntity?

    @Query("SELECT * FROM reward_items WHERE subcategory = :sub AND isOwned = 1")
    suspend fun getOwnedBySubcategory(sub: String): List<RewardItemEntity>

    // Phase 2
    @Query("DELETE FROM reward_items")
    suspend fun deleteAll()
}
