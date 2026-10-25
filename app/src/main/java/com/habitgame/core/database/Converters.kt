package com.habitgame.core.database

import androidx.room.TypeConverter
import com.habitgame.core.database.entity.*

class Converters {
    @TypeConverter fun fromRepetitionType(value: RepetitionType): String = value.name
    @TypeConverter fun toRepetitionType(value: String): RepetitionType = RepetitionType.valueOf(value)

    @TypeConverter fun fromTaskCategoryType(value: TaskCategoryType): String = value.name
    @TypeConverter fun toTaskCategoryType(value: String): TaskCategoryType = TaskCategoryType.valueOf(value)

    @TypeConverter fun fromActivityCategory(value: ActivityCategory): String = value.name
    @TypeConverter fun toActivityCategory(value: String): ActivityCategory = ActivityCategory.valueOf(value)

    @TypeConverter fun fromDifficulty(value: Difficulty): String = value.name
    @TypeConverter fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)

    @TypeConverter fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter fun fromRewardCategory(value: RewardCategory): String = value.name
    @TypeConverter fun toRewardCategory(value: String): RewardCategory = RewardCategory.valueOf(value)

    @TypeConverter fun fromRewardSubcategory(value: RewardSubcategory): String = value.name
    @TypeConverter fun toRewardSubcategory(value: String): RewardSubcategory = RewardSubcategory.valueOf(value)

    @TypeConverter fun fromHabitFrequency(value: HabitFrequency): String = value.name
    @TypeConverter fun toHabitFrequency(value: String): HabitFrequency = HabitFrequency.valueOf(value)
}
