package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.CoinTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoinTransactionDao {
    @Query("SELECT * FROM coin_transactions ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentTransactions(): Flow<List<CoinTransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: CoinTransactionEntity)

    @Query("SELECT SUM(CASE WHEN type = 'EARN' THEN amount ELSE -amount END) FROM coin_transactions")
    suspend fun getBalance(): Long?

    /** Rewards paid for finishing tasks, each one a task done (TaskCompletionManager's "Completed: …"). */
    @Query("SELECT COUNT(*) FROM coin_transactions WHERE type = 'EARN' AND reason LIKE 'Completed: %'")
    suspend fun countTaskRewards(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_transactions WHERE type = 'EARN'")
    suspend fun totalEarned(): Long
}
