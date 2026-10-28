package com.habitgame.core.di

import android.content.Context
import androidx.room.Room
import com.habitgame.core.database.AppDatabase
import com.habitgame.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideFocusSessionDao(db: AppDatabase): FocusSessionDao = db.focusSessionDao()
    @Provides fun provideCoinTransactionDao(db: AppDatabase): CoinTransactionDao = db.coinTransactionDao()
    @Provides fun provideRewardItemDao(db: AppDatabase): RewardItemDao = db.rewardItemDao()
    @Provides fun provideStreakDayDao(db: AppDatabase): StreakDayDao = db.streakDayDao()
    @Provides fun provideLocationZoneDao(db: AppDatabase): LocationZoneDao = db.locationZoneDao()
    @Provides fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()
    @Provides fun provideHabitCompletionDao(db: AppDatabase): HabitCompletionDao = db.habitCompletionDao()
    @Provides fun provideAppLockDao(db: AppDatabase): AppLockDao = db.appLockDao()
    @Provides fun provideChallengeDao(db: AppDatabase): ChallengeDao = db.challengeDao()
    @Provides fun provideDayProgressDao(db: AppDatabase): DayProgressDao = db.dayProgressDao()
    @Provides fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()
    @Provides fun provideSavingsDao(db: AppDatabase): SavingsDao = db.savingsDao()
    @Provides fun provideXpLogDao(db: AppDatabase): XpLogDao = db.xpLogDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideRewardClaimDao(db: AppDatabase): RewardClaimDao = db.rewardClaimDao()
    @Provides fun provideDaySkipDao(db: AppDatabase): DaySkipDao = db.daySkipDao()
}
