package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.UserDao
import com.habitgame.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val dao: UserDao) {
    fun observeUser(): Flow<UserEntity?> = dao.observeUser()
    suspend fun getUser(): UserEntity? = dao.getUser()
    suspend fun initUser() {
        if (dao.getUser() == null) dao.insertUser(UserEntity())
    }
    suspend fun updateUser(user: UserEntity) = dao.updateUser(user)
    suspend fun addXp(xp: Int) = dao.addXp(xp)
    suspend fun addCoins(coins: Long) = dao.addCoins(coins)
    suspend fun countTaskDone() = dao.countTaskDone()
    suspend fun raiseLifetimeTotals(tasks: Int, coins: Long) = dao.raiseLifetimeTotals(tasks, coins)
    suspend fun spendCoins(coins: Long): Boolean = dao.spendCoins(coins) > 0
    suspend fun addFocusMinutes(minutes: Long) = dao.addFocusMinutes(minutes)
    // Phase 2
    suspend fun deductXp(xp: Int) = dao.deductXp(xp)
    suspend fun deleteAll() {
        dao.deleteAll()
        dao.insertUser(UserEntity()) // Re-create fresh user
    }
}
