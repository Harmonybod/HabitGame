package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.ActivityCategory
import com.habitgame.core.database.entity.Difficulty

/** Content of the "My 100 Days" tracker: phases, coding plan, journal prompts and the default checklist. */
object TrackerPlan {
    const val TOTAL_DAYS = 100

    val MOODS = listOf("😞", "😕", "😐", "🙂", "😄")

    val MILESTONES = setOf(1, 7, 14, 30, 50, 75, 100)

    data class Phase(val lastDay: Int, val name: String, val tip: String)

    val PHASES = listOf(
        Phase(10, "Days 1–10: Show up", "Don't aim for perfect. Aim to check in every single day."),
        Phase(30, "Days 11–30: Build the rhythm", "Habits stick around now. Keep the same time each morning."),
        Phase(50, "Days 31–50: Push a little", "Add five more minutes to coding or reading."),
        Phase(75, "Days 51–75: The quiet middle", "Motivation dips here. Reread your early journal entries."),
        Phase(99, "Days 76–99: Finish strong", "Start a small coding project you can show off on day 100."),
        Phase(100, "Day 100: You did it", "Read your whole journal and write a letter to your future self.")
    )

    val PROMPTS = listOf(
        "What's one thing you're proud of today?",
        "What felt hard today, and what helped?",
        "Describe your energy today in three words.",
        "What did you learn about yourself this week?",
        "What would make tomorrow a good day?",
        "Who or what are you grateful for right now?",
        "What's one worry you can let go of tonight?",
        "When did you feel most like yourself today?",
        "What's something small that made you smile?",
        "If day 100 you could read this, what would you want them to know?",
        "What habit felt easiest today? Which felt hardest?",
        "What are you avoiding, and why?",
        "How did you take care of your body today?",
        "What's one thing you'd tell your day 1 self?"
    )

    fun phaseFor(day: Int): Phase = PHASES.first { day <= it.lastDay }
    fun promptFor(day: Int): String = PROMPTS[(day.coerceAtLeast(1) - 1) % PROMPTS.size]

    /** A checklist item from the web tracker, turned into a repeating game task. */
    data class SeedTask(
        val key: String,
        val name: String,
        val hour: Int,
        val minute: Int,
        val category: ActivityCategory,
        val difficulty: Difficulty = Difficulty.EASY,
        val focusMinutes: Int = 0,
        val description: String = ""
    )

    val SEED_TASKS = listOf(
        // Daily habits
        SeedTask("habit_bed", "Make my bed", 7, 30, ActivityCategory.OTHER),
        SeedTask("habit_water", "Drink water", 10, 0, ActivityCategory.OTHER),
        SeedTask("habit_coding", "Practice coding", 17, 0, ActivityCategory.CODING, Difficulty.MEDIUM, focusMinutes = 30),
        SeedTask("habit_read", "Read", 21, 0, ActivityCategory.READING, focusMinutes = 20),
        SeedTask("habit_body", "Take care of my body", 21, 45, ActivityCategory.OTHER),
        // Savings
        SeedTask("save_daily", "Put money aside today", 20, 0, ActivityCategory.SAVINGS,
            description = "Set your savings goal in the Savings tab.")
    )

    /** The weight gain schedule lives on the Habits tab. */
    data class SeedHabit(
        val key: String,
        val name: String,
        val emoji: String,
        val hour: Int,
        val minute: Int,
        val difficulty: Difficulty = Difficulty.EASY
    )

    const val WEIGHT_GAIN_GROUP = "Weight gain"

    val SEED_HABITS = listOf(
        SeedHabit("wg_breakfast", "Breakfast with protein (eggs, milk, oats)", "🍳", 8, 0),
        SeedHabit("wg_snack1", "Snack: banana, nuts or peanut butter toast", "🍌", 10, 30),
        SeedHabit("wg_lunch", "Lunch: full plate, add rice or bread", "🍛", 13, 0),
        SeedHabit("wg_smoothie", "Smoothie or yogurt with fruit", "🥤", 16, 0),
        SeedHabit("wg_workout", "Strength workout, 20–30 min", "🏋️", 18, 0, Difficulty.MEDIUM),
        SeedHabit("wg_dinner", "Dinner with protein and carbs", "🍽️", 19, 30),
        SeedHabit("wg_bedtime", "Small bedtime snack (milk, nuts)", "🥛", 21, 30)
    )

    const val CODING_TASK_KEY = "habit_coding"
    const val SAVINGS_TASK_KEY = "save_daily"
}
