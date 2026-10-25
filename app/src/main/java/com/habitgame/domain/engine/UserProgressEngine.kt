package com.habitgame.domain.engine

import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class LevelUpResult(
    val didLevelUp: Boolean,
    val newLevel: Int,
    val newTitle: String,
    val xpEarned: Int
)

@Singleton
class UserProgressEngine @Inject constructor(
    private val userRepo: UserRepository,
    private val boosterManager: BoosterManager,
    private val trackerRepo: TrackerRepository
) {
    companion object {
        val TITLES = listOf("Beginner", "Focused", "Consistent", "Disciplined", "Elite", "Legendary")

        fun xpRequired(level: Int): Long = (100L * level)

        fun titleForLevel(level: Int): String = when {
            level >= 50 -> TITLES[5]
            level >= 30 -> TITLES[4]
            level >= 20 -> TITLES[3]
            level >= 10 -> TITLES[2]
            level >= 5  -> TITLES[1]
            else        -> TITLES[0]
        }
    }

    /**
     * Awards XP to the user, handles level-up, returns result.
     */
    suspend fun awardXp(baseXp: Int, multiplier: Float = 1.0f): LevelUpResult {
        // Double XP booster from the Shop stacks on top of streak multipliers
        val earnedXp = (baseXp * multiplier * boosterManager.xpMultiplier()).toInt()
        if (earnedXp > 0) trackerRepo.logXp(java.time.LocalDate.now().toString(), earnedXp)
        val user = userRepo.getUser() ?: UserEntity()
        var currentXp = user.currentXp + earnedXp
        var currentLevel = user.level
        var didLevelUp = false

        while (currentXp >= xpRequired(currentLevel)) {
            currentXp -= xpRequired(currentLevel)
            currentLevel++
            didLevelUp = true
        }

        val newTitle = titleForLevel(currentLevel)
        userRepo.updateUser(
            user.copy(
                currentXp = currentXp,
                level = currentLevel,
                title = newTitle
            )
        )
        return LevelUpResult(didLevelUp, currentLevel, newTitle, earnedXp)
    }

    fun progressFraction(user: UserEntity): Float {
        val needed = xpRequired(user.level)
        return if (needed == 0L) 0f else (user.currentXp.toFloat() / needed).coerceIn(0f, 1f)
    }
}
