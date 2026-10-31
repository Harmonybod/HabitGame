package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.*

/** Reads and replaces everything a transfer code carries from one phone to another. */
@Dao
interface TransferDao {
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_completions") suspend fun habitCompletions(): List<HabitCompletionEntity>
    @Query("SELECT * FROM streak_days") suspend fun streakDays(): List<StreakDayEntity>
    @Query("SELECT * FROM location_zones") suspend fun zones(): List<LocationZoneEntity>
    @Query("SELECT * FROM day_progress") suspend fun dayProgress(): List<DayProgressEntity>
    @Query("SELECT * FROM journal_entries") suspend fun journal(): List<JournalEntryEntity>
    @Query("SELECT * FROM savings_entries") suspend fun savings(): List<SavingsEntryEntity>
    @Query("SELECT * FROM xp_log") suspend fun xpLog(): List<XpLogEntity>
    @Query("SELECT * FROM focus_sessions WHERE wasCompleted = 1") suspend fun focusSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM reward_claims") suspend fun rewardClaims(): List<RewardClaimEntity>
    @Query("SELECT * FROM day_skips") suspend fun daySkips(): List<DaySkipEntity>
    @Query("SELECT * FROM areas") suspend fun areas(): List<AreaEntity>
    @Query("SELECT * FROM checklist_items") suspend fun checklistItems(): List<ChecklistItemEntity>
    @Query("SELECT name FROM reward_items WHERE category = 'COSMETIC' AND isOwned = 1") suspend fun ownedCosmetics(): List<String>

    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM habit_completions") suspend fun clearHabitCompletions()
    @Query("DELETE FROM streak_days") suspend fun clearStreakDays()
    @Query("DELETE FROM location_zones") suspend fun clearZones()
    @Query("DELETE FROM day_progress") suspend fun clearDayProgress()
    @Query("DELETE FROM journal_entries") suspend fun clearJournal()
    @Query("DELETE FROM savings_entries") suspend fun clearSavings()
    @Query("DELETE FROM xp_log") suspend fun clearXpLog()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM reward_claims") suspend fun clearRewardClaims()
    @Query("DELETE FROM day_skips") suspend fun clearDaySkips()
    @Query("DELETE FROM areas") suspend fun clearAreas()
    @Query("DELETE FROM checklist_items") suspend fun clearChecklistItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(items: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabits(items: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitCompletions(items: List<HabitCompletionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStreakDays(items: List<StreakDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putZones(items: List<LocationZoneEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDayProgress(items: List<DayProgressEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putJournal(items: List<JournalEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSavings(items: List<SavingsEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putXpLog(items: List<XpLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFocusSessions(items: List<FocusSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNotes(items: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRewardClaims(items: List<RewardClaimEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDaySkips(items: List<DaySkipEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAreas(items: List<AreaEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChecklistItems(items: List<ChecklistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChallenge(challenge: ChallengeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUser(user: UserEntity)

    /** Cosmetics (themes, badges, icon packs) end up owned exactly as on the other phone. */
    @Query("UPDATE reward_items SET isOwned = (name IN (:owned)), isLocked = NOT (name IN (:owned)) WHERE category = 'COSMETIC'")
    suspend fun setOwnedCosmetics(owned: List<String>)
}
