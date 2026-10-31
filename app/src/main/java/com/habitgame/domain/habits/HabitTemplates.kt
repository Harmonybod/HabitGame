package com.habitgame.domain.habits

import com.habitgame.core.database.entity.Difficulty
import com.habitgame.domain.tracker.TrackerPlan
import java.time.LocalTime

/** A ready-made habit: tapping it opens the habit form already filled in. */
data class HabitTemplate(
    val name: String,
    val emoji: String,
    val goal: String = "",                      // shown on the card and used as the description
    val time: LocalTime? = null,                // reminder time
    val days: Set<String> = emptySet(),         // empty = every day
    val difficulty: Difficulty = Difficulty.EASY,
    val checklist: List<String> = emptyList()
)

/** A folder of ready-made habits. Adding one of them puts the habit in the area of the same name. */
data class TemplateFolder(
    val name: String,
    val emoji: String,
    val color: Long,                            // ARGB
    val blurb: String,
    val templates: List<HabitTemplate>
) {
    val colorHex: String get() = "#%06X".format(color and 0xFFFFFF)
}

/** The folders on the "New habit" page. */
object HabitTemplates {
    private fun at(hour: Int, minute: Int = 0) = LocalTime.of(hour, minute)
    private val MWF = setOf("MON", "WED", "FRI")

    val FOLDERS = listOf(
        TemplateFolder(
            "Take care of body", "🧼", 0xFF26A69A, "Small daily care that adds up",
            listOf(
                HabitTemplate("Brush teeth", "🪥", "2 minutes, morning and night", at(7)),
                HabitTemplate("Take a shower", "🚿", "Start the day fresh", at(7, 15)),
                HabitTemplate("Wash your face", "🧼", "Morning and before bed", at(7, 10)),
                HabitTemplate("Floss", "🦷", "Before bed", at(21, 30)),
                HabitTemplate("Skincare routine", "🧴", "Evening routine", at(21, 45), checklist = listOf("Cleanse", "Tone", "Moisturise")),
                HabitTemplate("Drink water after waking up", "💧", "One glass", at(6, 45)),
                HabitTemplate("Stretch", "🤸", "5 minutes", at(7, 30)),
                HabitTemplate("Do your hair", "💇", "Comb or style", at(7, 20)),
                HabitTemplate("Trim your nails", "💅", "Once a week", at(19), setOf("SUN"))
            )
        ),
        TemplateFolder(
            TrackerPlan.WEIGHT_GAIN_GROUP, "🍽️", 0xFFFF9800, "Eat more, lift and sleep well",
            TrackerPlan.SEED_HABITS.map { seed ->
                HabitTemplate(seed.name, seed.emoji, time = at(seed.hour, seed.minute), difficulty = seed.difficulty)
            } + listOf(
                HabitTemplate("Drink a protein shake", "🥤", "After training or between meals", at(17)),
                HabitTemplate("Weigh yourself", "⚖️", "Same time each week", at(8), setOf("SUN"))
            )
        ),
        TemplateFolder(
            "Most popular", "⭐", 0xFF7C4DFF, "What most people start with",
            listOf(
                HabitTemplate("Meditate", "🧘", "20 minutes a day", at(6, 30)),
                HabitTemplate("Go for a run", "🏃", "3 km", at(6, 30), difficulty = Difficulty.MEDIUM),
                HabitTemplate("Read books", "📚", "30 minutes", at(21, 30)),
                HabitTemplate("Write in your journal", "📝", "A few lines about the day", at(22)),
                HabitTemplate("Plan tomorrow's to-dos", "🗒️", "Top 3 things", at(22), checklist = listOf("Task 1", "Task 2", "Task 3"))
            )
        ),
        TemplateFolder(
            "Stay fit", "💪", 0xFFE53935, "Move every day",
            listOf(
                HabitTemplate("Hit the gym", "🏋️", "3 times a week", at(18), MWF, Difficulty.MEDIUM),
                HabitTemplate("Swimming", "🏊", "1 hour", at(15), setOf("SAT"), Difficulty.MEDIUM),
                HabitTemplate("Core training", "🔥", "30 minutes", at(17, 30), difficulty = Difficulty.MEDIUM),
                HabitTemplate("Yoga", "🧘‍♀️", "30 minutes", at(6)),
                HabitTemplate("HIIT cardio", "⚡", "20 minutes", at(7, 30), difficulty = Difficulty.HARD),
                HabitTemplate("Cycling", "🚴", "5 km", at(7)),
                HabitTemplate("Walk 10,000 steps", "👟", "Through the day", at(20)),
                HabitTemplate("Jump rope", "🪢", "10 minutes", at(17)),
                HabitTemplate("Push-ups", "💪", "3 sets", at(7, 45), checklist = listOf("Set 1", "Set 2", "Set 3"))
            )
        ),
        TemplateFolder(
            "Stay healthy", "❤️", 0xFF43A047, "Feel good, sleep well",
            listOf(
                HabitTemplate("Drink water", "💧", "2 litres a day", at(10)),
                HabitTemplate("Go for a walk", "🚶", "3 km", at(18, 30)),
                HabitTemplate("Sleep 8 hours", "😴", "In bed by 22:30", at(22, 30)),
                HabitTemplate("Take a cold shower", "🥶", "3 times a week", at(6, 30), MWF),
                HabitTemplate("Power nap", "💤", "20 minutes", at(13)),
                HabitTemplate("Get some sunlight", "☀️", "10 minutes outside", at(8)),
                HabitTemplate("Take vitamins", "💊", "With breakfast", at(7, 30))
            )
        ),
        TemplateFolder(
            "Watch your diet", "🥗", 0xFF8BC34A, "Eat better, one meal at a time",
            listOf(
                HabitTemplate("Eat fruit", "🍎", "Two pieces", at(16)),
                HabitTemplate("Eat vegetables", "🥦", "With lunch and dinner", at(13)),
                HabitTemplate("Limit sugar", "🍬", "No sweets today", at(12, 30)),
                HabitTemplate("Track calories", "🔢", "Log every meal", at(21, 30)),
                HabitTemplate("Limit caffeine", "☕", "No coffee after 2 PM", at(14)),
                HabitTemplate("Cook at home", "🍳", "Skip the takeaway", at(19)),
                HabitTemplate("No late-night snacks", "🌙", "Kitchen closes at 9", at(21))
            )
        ),
        TemplateFolder(
            "Learn something new", "🎓", 0xFF1E88E5, "A little every day",
            listOf(
                HabitTemplate("Learn a language", "🗣️", "30 minutes", at(12)),
                HabitTemplate("Practice coding", "💻", "One small exercise", at(20), difficulty = Difficulty.MEDIUM),
                HabitTemplate("Read 10 pages", "📖", "Any book", at(21)),
                HabitTemplate("Watch a lesson", "🎬", "One video course lesson", at(19)),
                HabitTemplate("Try a new recipe", "🧑‍🍳", "Once a week", at(18, 30), setOf("SAT")),
                HabitTemplate("Practice typing", "⌨️", "10 minutes", at(20, 30))
            )
        ),
        TemplateFolder(
            "Live a passion", "🎨", 0xFFD81B60, "Make time for what you love",
            listOf(
                HabitTemplate("Play an instrument", "🎸", "20 minutes", at(20)),
                HabitTemplate("Take a photo", "📷", "One you like", at(17)),
                HabitTemplate("Draw or paint", "🖌️", "Anything", at(20)),
                HabitTemplate("Dance", "💃", "20 minutes", at(17, 30)),
                HabitTemplate("Write a page", "✍️", "Story, poem or ideas", at(21)),
                HabitTemplate("Sing", "🎤", "A few songs", at(18))
            )
        ),
        TemplateFolder(
            "Home & organisation", "🏠", 0xFF8D6E63, "A tidy home, a clear head",
            listOf(
                HabitTemplate("Make your bed", "🛏️", "Right after getting up", at(7, 30)),
                HabitTemplate("Tidy up", "🧹", "10 minutes", at(20)),
                HabitTemplate("Wash the dishes", "🧽", "Empty sink at night", at(21)),
                HabitTemplate("Do the laundry", "🧺", "Once a week", at(10), setOf("SAT")),
                HabitTemplate("Water the plants", "🪴", "Twice a week", at(9), setOf("MON", "THU")),
                HabitTemplate("Plan the week", "🗓️", "Sunday evening", at(19), setOf("SUN"))
            )
        ),
        TemplateFolder(
            "Mind & mood", "🧠", 0xFF5E35B1, "Look after your head too",
            listOf(
                HabitTemplate("Gratitude", "🙏", "Write 3 good things", at(22), checklist = listOf("Good thing 1", "Good thing 2", "Good thing 3")),
                HabitTemplate("Breathe", "🌬️", "2 slow minutes", at(12)),
                HabitTemplate("Phone-free hour", "📵", "Before bed", at(21)),
                HabitTemplate("Call a friend", "📞", "Once a week", at(18), setOf("SUN")),
                HabitTemplate("Go outside", "🌳", "15 minutes", at(16))
            )
        ),
        TemplateFolder(
            "Money", "💰", 0xFF00897B, "Know where it goes",
            listOf(
                HabitTemplate("Track spending", "💵", "Write down what you spent", at(21)),
                HabitTemplate("No impulse buys", "🛍️", "Wait a day before buying", at(12)),
                HabitTemplate("Check your budget", "📊", "Once a week", at(19), setOf("SUN")),
                HabitTemplate("Put money aside", "🐷", "Any amount", at(20))
            )
        ),
        TemplateFolder(
            "Break bad habits", "🚫", 0xFF757575, "Tick it each day you stayed clean",
            listOf(
                HabitTemplate("No smoking", "🚭", "Not one", at(21)),
                HabitTemplate("No alcohol", "🍺", "Not today", at(21)),
                HabitTemplate("Less junk food", "🍟", "Skip it today", at(21)),
                HabitTemplate("Less social media", "📱", "Under 1 hour", at(21)),
                HabitTemplate("No staying up late", "🌙", "In bed by 23:00", at(23)),
                HabitTemplate("Don't skip meals", "🍽️", "Three meals today", at(20)),
                HabitTemplate("Less gaming", "🎮", "Under 1 hour", at(21)),
                HabitTemplate("No nail biting", "💅", "All day", at(21))
            )
        )
    )

    fun folder(name: String): TemplateFolder? = FOLDERS.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Icon for an area: its folder's, or a plain folder. */
    fun emojiFor(area: String): String = folder(area)?.emoji ?: "📁"

    fun colorHexFor(area: String): String = folder(area)?.colorHex ?: "#7C4DFF"

    /** Colours offered for a new area. */
    val AREA_COLORS = listOf("#7C4DFF", "#26A69A", "#FF9800", "#E53935", "#43A047", "#1E88E5", "#D81B60", "#8D6E63", "#5E35B1", "#00897B", "#FFB300", "#757575")

    /** Icons offered for a new area. */
    val AREA_EMOJI = listOf("📁", "🧼", "🍽️", "⭐", "💪", "❤️", "🥗", "🎓", "🎨", "🏠", "🧠", "💰", "🚫", "💼", "📚", "🎯", "🌱", "🧘", "🏃", "🛒")
}
