package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The 100-day challenge: when it started and the savings goal. Single row (id = 1). */
@Entity(tableName = "challenge")
data class ChallengeEntity(
    @PrimaryKey val id: Int = 1,
    val startDate: String,              // ISO date YYYY-MM-DD
    val savingsGoal: Double = 0.0,
    val currency: String = "",
    val seeded: Boolean = false         // tracker tasks created
)

/** How much of a day's checklist was done; drives the circles on the 100-day board. */
@Entity(tableName = "day_progress")
data class DayProgressEntity(
    @PrimaryKey val date: String,       // ISO date YYYY-MM-DD
    val done: Int,
    val total: Int
) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/** One journal entry per day. */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val date: String,       // ISO date YYYY-MM-DD
    val mood: Int? = null,              // 0..4, index into the mood emoji list
    val text: String = "",
    val weightKg: Double? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    // The day's song or album (see MusicInfo); title etc. stay null until looked up
    val musicUrl: String? = null,
    val musicPlatform: String? = null,
    val musicKind: String? = null,
    val musicTitle: String? = null,
    val musicArtist: String? = null,
    val musicCoverUrl: String? = null
) {
    fun withMusic(info: com.habitgame.domain.music.MusicInfo?) = copy(
        musicUrl = info?.url,
        musicPlatform = info?.platform?.name,
        musicKind = info?.kind?.name,
        musicTitle = info?.title,
        musicArtist = info?.artist,
        musicCoverUrl = info?.coverUrl
    )
}

/** XP earned, one row per award; feeds the weekly XP graph on Home. */
@Entity(tableName = "xp_log")
data class XpLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                   // ISO date YYYY-MM-DD
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** Money put aside, either logged by hand or by completing a savings task. */
@Entity(tableName = "savings_entries")
data class SavingsEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val note: String = "",
    val date: String,                   // ISO date YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis(),
    val taskId: Long? = null
)
