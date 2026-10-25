package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.ChallengeDao
import com.habitgame.core.database.dao.DayProgressDao
import com.habitgame.core.database.dao.JournalDao
import com.habitgame.core.database.dao.SavingsDao
import com.habitgame.core.database.dao.DailyXp
import com.habitgame.core.database.dao.XpLogDao
import com.habitgame.core.database.entity.ChallengeEntity
import com.habitgame.core.database.entity.DayProgressEntity
import com.habitgame.core.database.entity.JournalEntryEntity
import com.habitgame.core.database.entity.SavingsEntryEntity
import com.habitgame.core.database.entity.XpLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val dayProgressDao: DayProgressDao,
    private val journalDao: JournalDao,
    private val savingsDao: SavingsDao,
    private val xpLogDao: XpLogDao
) {
    // Challenge
    fun observeChallenge(): Flow<ChallengeEntity?> = challengeDao.observe()
    suspend fun getChallenge(): ChallengeEntity? = challengeDao.get()
    suspend fun saveChallenge(challenge: ChallengeEntity) = challengeDao.upsert(challenge)

    // Day progress (the 100-day board)
    fun observeDayProgress(): Flow<List<DayProgressEntity>> = dayProgressDao.observeAll()
    suspend fun saveDayProgress(progress: DayProgressEntity) = dayProgressDao.upsert(progress)

    // Journal
    fun observeJournal(): Flow<List<JournalEntryEntity>> = journalDao.observeAll()
    suspend fun getJournalEntry(date: String): JournalEntryEntity? = journalDao.get(date)
    suspend fun saveJournalEntry(entry: JournalEntryEntity) = journalDao.upsert(entry)

    // Savings
    fun observeSavings(): Flow<List<SavingsEntryEntity>> = savingsDao.observeAll()
    suspend fun addSaving(entry: SavingsEntryEntity): Long = savingsDao.insert(entry)
    suspend fun deleteSaving(entry: SavingsEntryEntity) = savingsDao.delete(entry)

    // XP history (the weekly graph)
    fun observeDailyXp(from: String, to: String): Flow<List<DailyXp>> = xpLogDao.observeDaily(from, to)
    suspend fun logXp(date: String, amount: Int) = xpLogDao.insert(XpLogEntity(date = date, amount = amount))

    suspend fun deleteAll() {
        challengeDao.deleteAll()
        dayProgressDao.deleteAll()
        journalDao.deleteAll()
        savingsDao.deleteAll()
        xpLogDao.deleteAll()
    }
}
