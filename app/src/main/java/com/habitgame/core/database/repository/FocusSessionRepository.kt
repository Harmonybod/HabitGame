package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.FocusSessionDao
import com.habitgame.core.database.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusSessionRepository @Inject constructor(private val dao: FocusSessionDao) {
    fun observeAllSessions(): Flow<List<FocusSessionEntity>> = dao.observeAllSessions()
    fun observeCompletedSessions(): Flow<List<FocusSessionEntity>> = dao.observeCompletedSessions()
    suspend fun insertSession(session: FocusSessionEntity): Long = dao.insertSession(session)
    suspend fun updateSession(session: FocusSessionEntity) = dao.updateSession(session)
    suspend fun getTotalFocusSeconds(): Long = dao.getTotalFocusSeconds() ?: 0L
    suspend fun getCompletedSessionCount(): Int = dao.getCompletedSessionCount()
    fun observeDailyFocus(from: String, to: String) = dao.observeDailyFocus(from, to)
}
