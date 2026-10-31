package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.AreaDao
import com.habitgame.core.database.dao.HabitDao
import com.habitgame.core.database.dao.TaskDao
import com.habitgame.core.database.entity.AreaEntity
import com.habitgame.domain.habits.HabitTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AreaRepository @Inject constructor(
    private val dao: AreaDao,
    habitDao: HabitDao,
    taskDao: TaskDao
) {
    /**
     * Every area: the saved ones, plus any area a habit or task names that has no row yet
     * (e.g. "Weight gain" on a fresh install, or from an older transfer code).
     */
    val areas: Flow<List<AreaEntity>> = combine(dao.observeAll(), habitDao.observeAllHabits(), taskDao.observeAllTasks()) { saved, habits, tasks ->
        val known = saved.map { it.name }.toSet()
        val named = (habits.map { it.groupName } + tasks.map { it.groupName })
            .filter { it.isNotBlank() && it !in known }
            .distinct()
            .map { AreaEntity(name = it, iconEmoji = HabitTemplates.emojiFor(it), colorHex = HabitTemplates.colorHexFor(it), sortOrder = Int.MAX_VALUE) }
        saved + named
    }

    /** Adds the area if it doesn't exist yet. */
    suspend fun ensure(name: String, emoji: String = HabitTemplates.emojiFor(name), colorHex: String = HabitTemplates.colorHexFor(name)) {
        if (name.isBlank()) return
        dao.insertIfMissing(AreaEntity(name = name, iconEmoji = emoji, colorHex = colorHex, sortOrder = dao.getAll().size))
    }

    /** Saves an area; renaming it moves its habits and tasks along. */
    suspend fun save(area: AreaEntity, oldName: String? = null) {
        if (oldName != null && oldName != area.name) {
            dao.delete(oldName)
            dao.moveHabits(oldName, area.name)
            dao.moveTasks(oldName, area.name)
        }
        dao.upsert(area)
    }

    /** Removes the area. Its habits and tasks stay, with no area. */
    suspend fun delete(name: String) {
        dao.delete(name)
        dao.moveHabits(name, "")
        dao.moveTasks(name, "")
    }
}
