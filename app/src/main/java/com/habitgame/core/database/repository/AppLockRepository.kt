package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.AppLockDao
import com.habitgame.core.database.entity.AppLockEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockRepository @Inject constructor(private val dao: AppLockDao) {
    fun observeAllApps(): Flow<List<AppLockEntity>> = dao.observeAllApps()
    fun observeLockedApps(): Flow<List<AppLockEntity>> = dao.observeLockedApps()
    fun observeUnlockedApps(): Flow<List<AppLockEntity>> = dao.observeUnlockedApps()
    suspend fun getApp(packageName: String): AppLockEntity? = dao.getApp(packageName)
    suspend fun insertApp(app: AppLockEntity) = dao.insertApp(app)
    suspend fun updateApp(app: AppLockEntity) = dao.updateApp(app)
    suspend fun unlockApp(packageName: String, minutes: Int, cost: Long) =
        dao.unlockApp(packageName, minutes, cost)
    suspend fun lockApp(packageName: String) = dao.lockApp(packageName)
    suspend fun insertAll(apps: List<AppLockEntity>) = dao.insertAll(apps)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getAll(): List<AppLockEntity> = dao.getAll()
    suspend fun unlockAll(minutes: Int) = dao.unlockAll(minutes)
    suspend fun removeApp(packageName: String) = dao.deleteApp(packageName)

    /** Makes exactly [apps] the locked set: adds new ones, keeps existing ones (and their unlock timers), removes the rest. */
    suspend fun replaceLockedApps(apps: List<AppLockEntity>) {
        val keep = apps.map { it.packageName }.toSet()
        dao.getAll().filter { it.packageName !in keep }.forEach { dao.deleteApp(it.packageName) }
        dao.insertAll(apps)
    }
}

