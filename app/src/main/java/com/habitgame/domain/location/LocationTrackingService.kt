package com.habitgame.domain.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.habitgame.core.database.repository.LocationZoneRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.engine.StreakManager
import com.habitgame.domain.engine.UserProgressEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var locationValidator: LocationValidator
    @Inject lateinit var taskRepo: TaskRepository
    @Inject lateinit var locationZoneRepo: LocationZoneRepository
    @Inject lateinit var userRepo: UserRepository
    @Inject lateinit var progressEngine: UserProgressEngine
    @Inject lateinit var economyManager: EconomyManager
    @Inject lateinit var streakManager: StreakManager
    @Inject lateinit var taskCompletionManager: com.habitgame.domain.engine.TaskCompletionManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val channelId = "LocationTrackingChannel"

    // Maps task ID to the timestamp (in ms) when the user FIRST entered the zone.
    private val entryTimes = mutableMapOf<Long, Long>()

    private var isForeground = false
    private var trackingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isForeground = tryStartForeground()
    }

    /** Android refuses a location foreground service without location permission; stop instead of crashing. */
    private fun tryStartForeground(): Boolean {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasLocation) return false
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HabitGame Location Tracking")
            .setContentText("Monitoring location for task completion...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        return try {
            ServiceCompat.startForeground(
                this, 1337, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
            true
        } catch (e: Exception) {
            // SecurityException / ForegroundServiceStartNotAllowedException
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForeground) {
            stopSelf()
            return START_NOT_STICKY
        }
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        // The app starts this service on every launch; keep a single polling loop
        if (trackingJob?.isActive == true) return
        trackingJob = serviceScope.launch {
            while (isActive) {
                val tasks = taskRepo.observePendingTasks().firstOrNull()?.filter { it.locationZoneId != null }
                if (tasks.isNullOrEmpty()) {
                    delay(30_000)
                    continue
                }
                
                val currentLoc = locationValidator.getCurrentLocation()
                if (currentLoc == null) {
                    delay(10_000)
                    continue
                }

                val now = System.currentTimeMillis()

                for (task in tasks) {
                    val zone = locationZoneRepo.getById(task.locationZoneId!!) ?: continue
                    
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        currentLoc.latitude, currentLoc.longitude,
                        zone.latitude, zone.longitude,
                        results
                    )
                    val distance = results[0]
                    
                    if (distance <= zone.radiusMeters) {
                        // In zone
                        val entryTime = entryTimes.getOrPut(task.id) { now }
                        val timeInZoneMinutes = (now - entryTime) / (1000 * 60)
                        
                        if (timeInZoneMinutes >= zone.requiredDurationMinutes) {
                            // Validated!
                            completeTask(task)
                            entryTimes.remove(task.id)
                        }
                    } else {
                        // Left zone before duration met — reset
                        entryTimes.remove(task.id)
                    }
                }
                delay(15_000) // Poll every 15 seconds
            }
        }
    }
    
    private suspend fun completeTask(task: com.habitgame.core.database.entity.TaskEntity) {
        // Same rewards as ticking it off by hand, and it counts on the 100-day board
        taskCompletionManager.complete(task) ?: return
        val coinBase = EconomyManager.coinsForDifficulty(task.difficulty)

        // Give a completion notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Task Completed!")
            .setContentText("You reached ${task.name} and earned ${coinBase} coins!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(task.id.toInt(), notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Location Tasks",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
