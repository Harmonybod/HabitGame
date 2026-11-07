package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.CompletionReceiptEntity

@Dao
interface CompletionReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(receipt: CompletionReceiptEntity)

    @Query("SELECT * FROM completion_receipts WHERE `key` = :key")
    suspend fun get(key: String): CompletionReceiptEntity?

    @Query("DELETE FROM completion_receipts WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Receipts older than [before] can't be undone any more. */
    @Query("DELETE FROM completion_receipts WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM completion_receipts")
    suspend fun deleteAll()
}
