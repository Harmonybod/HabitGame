package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.RewardClaimEntity

@Dao
interface RewardClaimDao {
    /** Records the claim; returns -1 when it was already claimed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(claim: RewardClaimEntity): Long

    @Query("SELECT COUNT(*) > 0 FROM reward_claims WHERE `key` = :key")
    suspend fun isClaimed(key: String): Boolean

    @Query("DELETE FROM reward_claims")
    suspend fun deleteAll()
}
