package com.habitgame.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.domain.engine.HabitManager
import com.habitgame.domain.engine.TaskCompletionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Done ✓" button on task and habit reminder notifications. */
@AndroidEntryPoint
class TaskActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskCompletionManager: TaskCompletionManager
    @Inject lateinit var habitManager: HabitManager
    @Inject lateinit var habitRepo: HabitRepository
    @Inject lateinit var economyManager: com.habitgame.domain.engine.EconomyManager
    @Inject lateinit var boosterManager: com.habitgame.domain.engine.BoosterManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId < 0) return
                runAsync {
                    taskCompletionManager.complete(taskId)
                    TaskReminderWorker.dismissNotifications(context, taskId)
                }
            }
            ACTION_COMPLETE_HABIT -> {
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
                if (habitId < 0) return
                runAsync {
                    val habit = habitRepo.getHabitById(habitId)
                    if (habit != null && !habitRepo.isHabitCompletedToday(habitId)) {
                        habitManager.completeHabit(habit)
                    }
                    HabitReminderWorker.dismissNotifications(context, habitId)
                }
            }
            ACTION_EXTEND_SESSION -> {
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
                if (itemId < 0) return
                runAsync {
                    val session = boosterManager.activeSessions().firstOrNull { it.item.id == itemId } ?: return@runAsync
                    val message = SessionEndWorker.extend(context, economyManager, boosterManager, session)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.habitgame.action.COMPLETE_TASK"
        const val ACTION_COMPLETE_HABIT = "com.habitgame.action.COMPLETE_HABIT"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_HABIT_ID = "habit_id"
        const val ACTION_EXTEND_SESSION = "com.habitgame.action.EXTEND_SESSION"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
