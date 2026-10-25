package com.habitgame.domain.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.habitgame.MainActivity
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.tracker.TrackerManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Evening check-in: how much of today's 100-day circle is filled, and a nudge to write in the journal. */
@HiltWorker
class DailyCheckInWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackerRepo: TrackerRepository,
    private val trackerManager: TrackerManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        trackerManager.snapshotToday()
        val challenge = trackerRepo.getChallenge() ?: return Result.success()
        val day = TrackerManager.dayNumber(challenge.startDate, today)
        val progress = trackerRepo.observeDayProgress().first().firstOrNull { it.date == today.toString() }
        val journal = trackerRepo.getJournalEntry(today.toString())
        val wroteJournal = journal != null && (journal.text.isNotBlank() || journal.mood != null)

        val left = progress?.let { it.total - it.done } ?: 0
        if (left <= 0 && wroteJournal) return Result.success()

        val parts = buildList {
            if (left > 0) add("$left of ${progress?.total} tasks left today")
            if (!wroteJournal) add("Your journal is waiting: how did today go?")
        }
        notify(applicationContext, "🌙 Day $day check-in", parts.joinToString(". ") + ".")
        return Result.success()
    }

    private fun notify(context: Context, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CHECK_IN)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "DailyCheckInWorker"
        const val CHANNEL_CHECK_IN = "habitgame_check_in"
        private const val NOTIFICATION_ID = 99_001
        private val CHECK_IN_TIME: LocalTime = LocalTime.of(21, 0)

        fun schedule(workManager: WorkManager) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(CHECK_IN_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyCheckInWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
