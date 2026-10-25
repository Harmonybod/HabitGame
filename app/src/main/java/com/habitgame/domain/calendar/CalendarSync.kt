package com.habitgame.domain.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import com.habitgame.core.database.AppDatabase
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.notification.deadlineReminderLeads
import com.habitgame.domain.notification.taskReminderLeads
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** A calendar on the phone that HabitGame can add events to. */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val isGoogle: Boolean, val color: Int)

data class CalendarSyncState(
    val enabled: Boolean = false,
    val calendarId: Long = -1,
    val calendarReminders: Boolean = true,   // the calendar app reminds before each event
    val appReminders: Boolean = true,        // HabitGame's own reminders as well
    val events: Int = 0,                     // HabitGame events in the calendar after the last sync
    val lastSynced: Long = 0,
    val error: String? = null
)

/**
 * Puts tasks, habits and shopping deadlines into a calendar on the phone, so they show in
 * Google Calendar (or Samsung Calendar) and the calendar app reminds with its own alerts.
 * Uses the phone's calendar storage, which Google Calendar syncs to the Google account by itself:
 * free, no sign-in or API key. HabitGame's events carry a marker, so it only ever changes its own.
 */
@Singleton
class CalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val userRepo: UserRepository,
    private val trackerManager: com.habitgame.domain.tracker.TrackerManager,
    private val db: AppDatabase
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var scope: CoroutineScope

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<CalendarSyncState> = _state.asStateFlow()

    private fun readState() = CalendarSyncState(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        calendarId = prefs.getLong(KEY_CALENDAR, -1),
        calendarReminders = prefs.getBoolean(KEY_CAL_REMINDERS, true),
        appReminders = prefs.getBoolean(KEY_APP_REMINDERS, true),
        events = prefs.getInt(KEY_EVENTS, 0),
        lastSynced = prefs.getLong(KEY_LAST, 0)
    )

    val hasPermission: Boolean
        get() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Keeps the calendar up to date as tasks, habits and reminder times change. */
    @OptIn(FlowPreview::class)
    fun start(appScope: CoroutineScope) {
        scope = appScope
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("tasks", "habits", "user")) {
            override fun onInvalidated(tables: Set<String>) { if (_state.value.enabled) changes.tryEmit(Unit) }
        })
        scope.launch { changes.debounce(3_000).collect { syncAll() } }
        if (_state.value.enabled) changes.tryEmit(Unit)
    }

    /** Writable calendars on the phone, Google ones first. */
    fun calendars(): List<PhoneCalendar> {
        if (!hasPermission) return emptyList()
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL)
        val result = mutableListOf<PhoneCalendar>()
        runCatching {
            context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) < Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    result += PhoneCalendar(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty().ifBlank { "Calendar" },
                        account = c.getString(2).orEmpty(),
                        isGoogle = c.getString(3) == "com.google",
                        color = c.getInt(4)
                    )
                }
            }
        }
        return result.sortedWith(compareByDescending<PhoneCalendar> { it.isGoogle }.thenBy { it.account }.thenBy { it.name })
    }

    // ─── Settings ───────────────────────────────────────────────────────────────

    /** Starts syncing to [calendarId]. HabitGame's own reminders are switched off, since the calendar reminds now. */
    fun enable(calendarId: Long) {
        val previous = _state.value.calendarId
        if (previous != calendarId && previous >= 0) scope.launch { removeAll(previous) }
        prefs.edit().putBoolean(KEY_ENABLED, true).putLong(KEY_CALENDAR, calendarId).putBoolean(KEY_APP_REMINDERS, false).apply()
        _state.value = readState()
        scope.launch { syncAll(); trackerManager.rescheduleAllReminders() }
    }

    /** Stops syncing and takes HabitGame's events out of the calendar. HabitGame's own reminders come back. */
    fun disable() {
        val calendar = _state.value.calendarId
        prefs.edit().putBoolean(KEY_ENABLED, false).putBoolean(KEY_APP_REMINDERS, true).apply()
        _state.value = readState()
        scope.launch {
            if (calendar >= 0) removeAll(calendar)
            trackerManager.rescheduleAllReminders()
        }
    }

    fun setCalendarReminders(on: Boolean) {
        prefs.edit().putBoolean(KEY_CAL_REMINDERS, on).apply()
        _state.value = readState()
        scope.launch { syncAll() }
    }

    fun setAppReminders(on: Boolean) {
        prefs.edit().putBoolean(KEY_APP_REMINDERS, on).apply()
        _state.value = readState()
        scope.launch { trackerManager.rescheduleAllReminders() }
    }

    fun syncNow() { if (::scope.isInitialized) scope.launch { syncAll() } }

    // ─── Syncing ────────────────────────────────────────────────────────────────

    /** Makes the calendar match HabitGame: adds new events, updates changed ones, removes old ones. */
    suspend fun syncAll() = lock.withLock {
        val s = _state.value
        if (!s.enabled || s.calendarId < 0) return@withLock
        if (!hasPermission) {
            _state.update { it.copy(error = "Calendar access is off. Allow it to keep syncing.") }
            return@withLock
        }
        runCatching {
            val user = userRepo.getUser() ?: UserEntity()
            val leads = if (s.calendarReminders) user.taskReminderLeads else emptyList()
            val deadlineLeads = if (s.calendarReminders) user.deadlineReminderLeads else emptyList()
            val wanted = (taskRepo.getAllTasks().mapNotNull { CalendarEvents.forTask(it, leads, deadlineLeads) } +
                habitRepo.getAllHabits().mapNotNull { CalendarEvents.forHabit(it, leads) }).associateBy { it.key }

            val existing = existingEvents(s.calendarId)
            existing.forEach { (key, ids) -> ids.drop(1).forEach(::deleteEvent).also { if (ids.size > 1) forget(key) } }  // duplicates from two phones

            wanted.values.forEach { spec ->
                val id = existing[spec.key]?.firstOrNull()
                when {
                    id == null -> insert(s.calendarId, spec)?.let { remember(spec) }
                    prefs.getString(FP + spec.key, null) != spec.fingerprint -> { update(id, spec); remember(spec) }
                }
            }
            existing.keys.filter { it !in wanted }.forEach { key -> existing[key]?.forEach(::deleteEvent); forget(key) }
            wanted.size
        }.onSuccess { count ->
            prefs.edit().putInt(KEY_EVENTS, count).putLong(KEY_LAST, System.currentTimeMillis()).apply()
            _state.value = readState().copy(error = null)
        }.onFailure { e ->
            _state.update { it.copy(error = "Couldn't update the calendar: ${e.message}") }
        }
    }

    /** Takes every HabitGame event out of [calendarId]. */
    private suspend fun removeAll(calendarId: Long) = lock.withLock {
        if (!hasPermission) return@withLock
        runCatching { existingEvents(calendarId).values.flatten().forEach(::deleteEvent) }
        prefs.all.keys.filter { it.startsWith(FP) }.let { keys -> prefs.edit().apply { keys.forEach(::remove) }.putInt(KEY_EVENTS, 0).apply() }
        _state.value = readState()
    }

    /** HabitGame's events in the calendar, by key (several ids when two phones both added one). */
    private fun existingEvents(calendarId: Long): Map<String, List<Long>> {
        val found = mutableMapOf<String, MutableList<Long>>()
        context.contentResolver.query(
            Events.CONTENT_URI, arrayOf(Events._ID, Events.DESCRIPTION),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), "%${CalendarEvents.MARKER_PREFIX}%"), "${Events._ID} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val key = CalendarEvents.keyIn(c.getString(1)) ?: continue
                found.getOrPut(key) { mutableListOf() } += c.getLong(0)
            }
        }
        return found
    }

    private fun values(spec: EventSpec) = ContentValues().apply {
        put(Events.TITLE, spec.title)
        put(Events.DESCRIPTION, spec.description)
        put(Events.DTSTART, spec.start)
        put(Events.ALL_DAY, if (spec.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (spec.allDay) "UTC" else TimeZone.getDefault().id)
        put(Events.HAS_ALARM, if (spec.reminders.isEmpty()) 0 else 1)
        if (spec.rrule != null) {
            // Repeating events give their length instead of an end
            put(Events.RRULE, spec.rrule)
            put(Events.DURATION, CalendarEvents.duration(spec))
            putNull(Events.DTEND)
        } else {
            putNull(Events.RRULE)
            putNull(Events.DURATION)
            put(Events.DTEND, spec.start + spec.durationMinutes * 60_000L)
        }
    }

    private fun insert(calendarId: Long, spec: EventSpec): Long? {
        val uri = context.contentResolver.insert(Events.CONTENT_URI, values(spec).apply { put(Events.CALENDAR_ID, calendarId) }) ?: return null
        val id = ContentUris.parseId(uri)
        setReminders(id, spec.reminders)
        return id
    }

    private fun update(id: Long, spec: EventSpec) {
        context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(spec), null, null)
        setReminders(id, spec.reminders)
    }

    private fun setReminders(eventId: Long, minutes: List<Int>) {
        context.contentResolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        minutes.distinct().forEach { m ->
            context.contentResolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, m)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
    }

    private fun deleteEvent(id: Long) {
        runCatching { context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) }
    }

    private fun remember(spec: EventSpec) = prefs.edit().putString(FP + spec.key, spec.fingerprint).apply()
    private fun forget(key: String) = prefs.edit().remove(FP + key).apply()

    companion object {
        private const val PREFS = "calendar_sync"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CALENDAR = "calendar_id"
        private const val KEY_CAL_REMINDERS = "calendar_reminders"
        private const val KEY_APP_REMINDERS = "app_reminders"
        private const val KEY_EVENTS = "events"
        private const val KEY_LAST = "last_synced"
        private const val FP = "fp_"

        /** Whether HabitGame sends its own reminders before tasks, habits and deadlines (the calendar may do it instead). */
        fun appRemindersOn(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_APP_REMINDERS, true)
    }
}
