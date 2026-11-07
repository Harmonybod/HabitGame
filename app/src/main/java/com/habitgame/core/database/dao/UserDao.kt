package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user WHERE id = 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("SELECT * FROM user WHERE id = 1")
    suspend fun getUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE user SET currentXp = currentXp + :xp, totalTasksDone = totalTasksDone + 1 WHERE id = 1")
    suspend fun addXp(xp: Int)

    @Query("UPDATE user SET totalCoins = totalCoins + :coins, totalCoinsEarned = totalCoinsEarned + :coins WHERE id = 1")
    suspend fun addCoins(coins: Long)

    @Query("UPDATE user SET totalTasksDone = totalTasksDone + 1 WHERE id = 1")
    suspend fun countTaskDone()

    /** Raises the all-time totals to at least these (never lowers them). */
    @Query("UPDATE user SET totalTasksDone = MAX(totalTasksDone, :tasks), totalCoinsEarned = MAX(totalCoinsEarned, :coins) WHERE id = 1")
    suspend fun raiseLifetimeTotals(tasks: Int, coins: Long)

    @Query("UPDATE user SET totalCoins = totalCoins - :coins WHERE id = 1 AND totalCoins >= :coins")
    suspend fun spendCoins(coins: Long): Int

    @Query("UPDATE user SET totalFocusMinutes = totalFocusMinutes + :minutes WHERE id = 1")
    suspend fun addFocusMinutes(minutes: Long)

    // Phase 2
    @Query("DELETE FROM user")
    suspend fun deleteAll()

    /** Takes back coins a mistaken completion paid (never below zero). */
    @Query("UPDATE user SET totalCoins = MAX(0, totalCoins - :coins), totalCoinsEarned = MAX(0, totalCoinsEarned - :coins) WHERE id = 1")
    suspend fun takeBackCoins(coins: Long)

    @Query("UPDATE user SET totalTasksDone = MAX(0, totalTasksDone - 1) WHERE id = 1")
    suspend fun uncountTaskDone()

    @Query("UPDATE user SET currentXp = MAX(0, currentXp - :xp) WHERE id = 1")
    suspend fun deductXp(xp: Int)
}
