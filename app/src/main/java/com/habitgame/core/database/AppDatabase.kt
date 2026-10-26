package com.habitgame.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habitgame.core.database.dao.*
import com.habitgame.core.database.entity.*

@Database(
    entities = [
        UserEntity::class,
        TaskEntity::class,
        FocusSessionEntity::class,
        CoinTransactionEntity::class,
        RewardItemEntity::class,
        StreakDayEntity::class,
        LocationZoneEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        AppLockEntity::class,
        ChallengeEntity::class,
        DayProgressEntity::class,
        JournalEntryEntity::class,
        SavingsEntryEntity::class,
        XpLogEntity::class,
        NoteEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun coinTransactionDao(): CoinTransactionDao
    abstract fun rewardItemDao(): RewardItemDao
    abstract fun streakDayDao(): StreakDayDao
    abstract fun locationZoneDao(): LocationZoneDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun appLockDao(): AppLockDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun dayProgressDao(): DayProgressDao
    abstract fun journalDao(): JournalDao
    abstract fun savingsDao(): SavingsDao
    abstract fun xpLogDao(): XpLogDao
    abstract fun transferDao(): TransferDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val DATABASE_NAME = "habitgame_db"

        /** Adds the 100-day tracker tables without wiping existing tasks, habits and coins. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN savingAmount REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN trackerKey TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS challenge (id INTEGER NOT NULL, startDate TEXT NOT NULL, " +
                        "savingsGoal REAL NOT NULL, currency TEXT NOT NULL, seeded INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS day_progress (date TEXT NOT NULL, done INTEGER NOT NULL, " +
                        "total INTEGER NOT NULL, PRIMARY KEY(date))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS journal_entries (date TEXT NOT NULL, mood INTEGER, text TEXT NOT NULL, " +
                        "weightKg REAL, updatedAt INTEGER NOT NULL, PRIMARY KEY(date))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "amount REAL NOT NULL, note TEXT NOT NULL, date TEXT NOT NULL, createdAt INTEGER NOT NULL, taskId INTEGER)"
                )
            }
        }

        /** Repeat end dates for tasks and habits, plus habit groups (weight gain moves to habits). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatEndDate TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN repeatEndDate TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN groupName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN trackerKey TEXT")
            }
        }

        /** Habits can show their description as a card on the Home screen. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN showOnHome INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * App Unlock time now counts only while locked apps are open, the XP graph gets a history
         * table (filled from what was already earned), and No Restriction Day costs 600.
         */
        /** All-time coins earned, for the stats (the balance goes down when coins are spent). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN totalCoinsEarned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** The Notes tab: notes written in HabitGame or shared in from Samsung Notes and other apps. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, " +
                        "body TEXT NOT NULL, source TEXT NOT NULL, pinned INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Shopping list tasks (price range and optional deadline), and reminders at several
         * times instead of one (the old single time carries over).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN priceMin REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN priceMax REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN deadlineAt INTEGER")
                db.execSQL("ALTER TABLE user ADD COLUMN reminderLeadTimes TEXT NOT NULL DEFAULT '5'")
                db.execSQL("ALTER TABLE user ADD COLUMN deadlineReminderTimes TEXT NOT NULL DEFAULT '60,15,5'")
                db.execSQL("UPDATE user SET reminderLeadTimes = CAST(notificationLeadMinutes AS TEXT)")
            }
        }

        /** A song or album can be attached to each journal day. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("musicUrl", "musicPlatform", "musicKind", "musicTitle", "musicArtist", "musicCoverUrl").forEach {
                    db.execSQL("ALTER TABLE journal_entries ADD COLUMN $it TEXT")
                }
            }
        }

        /** Glass becomes an add-on that works with any theme, with an optional background photo. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN glassEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user ADD COLUMN backgroundImagePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE user SET glassEnabled = 1, selectedTheme = 'default' WHERE selectedTheme = 'glass'")
                db.execSQL(
                    "UPDATE reward_items SET description = 'Frosted glass look that works on top of any theme. Can use your own photo as the background' " +
                        "WHERE themeKey = 'glass'"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reward_items ADD COLUMN usedMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE reward_items SET cost = 600 WHERE boosterEffect = 'NO_RESTRICTION'")
                db.execSQL(
                    "UPDATE reward_items SET description = 'Use your locked apps for ' || durationMinutes || " +
                        "' minutes. Time only counts while they are open' WHERE subcategory = 'APP_UNLOCK'"
                )
                db.execSQL(
                    "UPDATE reward_items SET description = description || '. Time only counts while your apps are open' " +
                        "WHERE subcategory = 'ENTERTAINMENT'"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS xp_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date TEXT NOT NULL, amount INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO xp_log (date, amount, createdAt) SELECT date(completedAt / 1000, 'unixepoch', 'localtime'), " +
                        "xpReward, completedAt FROM tasks WHERE completedAt IS NOT NULL AND xpReward > 0"
                )
                db.execSQL(
                    "INSERT INTO xp_log (date, amount, createdAt) SELECT c.date, h.xpReward, c.completedAt " +
                        "FROM habit_completions c JOIN habits h ON h.id = c.habitId WHERE h.xpReward > 0"
                )
                db.execSQL(
                    "INSERT INTO xp_log (date, amount, createdAt) SELECT date(completedAt / 1000, 'unixepoch', 'localtime'), " +
                        "xpEarned, completedAt FROM focus_sessions WHERE completedAt IS NOT NULL AND xpEarned > 0"
                )
            }
        }
    }
}
