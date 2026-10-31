package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE ownerKind = :kind AND ownerId = :ownerId ORDER BY sortOrder, id")
    suspend fun forOwner(kind: String, ownerId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun get(id: Long): ChecklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChecklistItemEntity): Long

    @Query("UPDATE checklist_items SET checkedDate = :date WHERE id = :id")
    suspend fun setChecked(id: Long, date: String?)

    /** Ticks every step, e.g. when the task or habit itself is ticked. */
    @Query("UPDATE checklist_items SET checkedDate = :date WHERE ownerKind = :kind AND ownerId = :ownerId")
    suspend fun checkAll(kind: String, ownerId: Long, date: String)

    /** Unticks the steps ticked on [date], e.g. when a habit is unticked. */
    @Query("UPDATE checklist_items SET checkedDate = NULL WHERE ownerKind = :kind AND ownerId = :ownerId AND checkedDate = :date")
    suspend fun uncheckOn(kind: String, ownerId: Long, date: String)

    @Query("DELETE FROM checklist_items WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM checklist_items WHERE ownerKind = :kind AND ownerId = :ownerId")
    suspend fun deleteForOwner(kind: String, ownerId: Long)

    /** Steps whose task or habit was deleted. */
    @Query(
        "DELETE FROM checklist_items WHERE (ownerKind = 'task' AND ownerId NOT IN (SELECT id FROM tasks)) " +
            "OR (ownerKind = 'habit' AND ownerId NOT IN (SELECT id FROM habits))"
    )
    suspend fun deleteOrphans()

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAll()
}
