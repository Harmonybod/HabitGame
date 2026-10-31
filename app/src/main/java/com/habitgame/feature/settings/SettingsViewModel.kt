package com.habitgame.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.repository.AppLockRepository
import com.habitgame.core.database.repository.EconomyRepository
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shop themes, icon packs and badges: listed in Settings whether bought (selectable) or not (locked). */
data class SettingsUiState(
    val user: UserEntity = UserEntity(),
    val themes: List<RewardItemEntity> = emptyList(),
    val iconPacks: List<RewardItemEntity> = emptyList(),
    val badges: List<RewardItemEntity> = emptyList(),
    val lockedApps: Int = 0
)

/** The transfer code section: a code made here, or a pasted one waiting to be confirmed. */
data class TransferUiState(
    val code: String? = null,
    val pending: com.habitgame.domain.transfer.TransferPayload? = null,
    val working: Boolean = false,
    val error: String? = null,
    val done: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val economyRepo: EconomyRepository,
    private val appLockRepo: AppLockRepository,
    private val trackerRepo: com.habitgame.core.database.repository.TrackerRepository,
    private val trackerManager: com.habitgame.domain.tracker.TrackerManager,
    private val progressTransfer: com.habitgame.domain.transfer.ProgressTransfer,
    private val cloudSync: com.habitgame.domain.sync.CloudSync,
    private val noteDao: com.habitgame.core.database.dao.NoteDao,
    private val rewardClaimDao: com.habitgame.core.database.dao.RewardClaimDao,
    private val daySkipDao: com.habitgame.core.database.dao.DaySkipDao,
    private val areaDao: com.habitgame.core.database.dao.AreaDao,
    private val checklistDao: com.habitgame.core.database.dao.ChecklistDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        userRepo.observeUser(),
        economyRepo.observeBySubcategory("THEME"),
        economyRepo.observeBySubcategory("ICON_PACK"),
        economyRepo.observeBySubcategory("BADGE"),
        appLockRepo.observeAllApps()
    ) { user, themes, iconPacks, badges, locked ->
        SettingsUiState(user = user ?: UserEntity(), themes = themes, iconPacks = iconPacks, badges = badges, lockedApps = locked.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // ─── Transfer codes ─────────────────────────────────────────────────────────
    private val _transfer = MutableStateFlow(TransferUiState())
    val transfer: StateFlow<TransferUiState> = _transfer.asStateFlow()

    /** Packs this phone's progress into a code to paste on another phone. */
    fun createTransferCode() = viewModelScope.launch {
        _transfer.update { it.copy(working = true, error = null, done = null) }
        val version = runCatching {
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
        }.getOrDefault(0)
        val code = runCatching { progressTransfer.createCode(version) }.getOrNull()
        _transfer.update {
            if (code == null) it.copy(working = false, error = "Couldn't create a code. Try again.")
            else it.copy(working = false, code = code)
        }
    }

    /** Checks a pasted code and shows what's in it before anything changes. */
    fun previewTransferCode(code: String) {
        _transfer.update { it.copy(error = null, done = null) }
        runCatching { progressTransfer.read(code) }
            .onSuccess { payload -> _transfer.update { it.copy(pending = payload) } }
            .onFailure { e -> _transfer.update { it.copy(error = e.message ?: "That code didn't work.") } }
    }

    fun cancelTransfer() = _transfer.update { it.copy(pending = null) }

    /** This phone's progress from before a sync replaced it (newest first). */
    private val _backups = MutableStateFlow(cloudSync.backups())
    val backups: StateFlow<List<com.habitgame.domain.sync.SyncBackup>> = _backups.asStateFlow()

    fun refreshBackups() { _backups.value = cloudSync.backups() }

    /** Shows what a backup holds; confirming loads it like a transfer code. */
    fun previewBackup(backup: com.habitgame.domain.sync.SyncBackup) = viewModelScope.launch {
        val code = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { backup.file.readText() }.getOrNull() }
        if (code == null) _transfer.update { it.copy(error = "That backup couldn't be read.") }
        else previewTransferCode(code)
    }

    /** Replaces this phone's progress with the previewed code. */
    fun confirmTransfer() = viewModelScope.launch {
        val payload = _transfer.value.pending ?: return@launch
        _transfer.update { it.copy(pending = null, working = true) }
        val ok = runCatching { progressTransfer.load(payload) }.isSuccess
        _transfer.update {
            if (ok) it.copy(working = false, done = "✅ Progress loaded: level ${payload.user.level}, ${payload.user.totalCoins} coins.")
            else it.copy(working = false, error = "Loading failed. Nothing was changed.")
        }
    }

    init {
        // New Shop items (e.g. themes added in an update) show up here even before the Shop is opened
        viewModelScope.launch { economyRepo.seedDefaultShop() }
    }

    fun setTheme(theme: String) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(selectedTheme = theme))
    }

    fun setIconPack(pack: String) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(selectedIconPack = pack))
    }

    /** Glass works on top of whichever theme is picked. */
    fun setGlass(enabled: Boolean) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(glassEnabled = enabled))
    }

    /** A photo from the gallery as the Glass background. */
    fun setBackgroundPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.habitgame.ui.theme.BackgroundPhoto.decode(context, uri)
        } ?: return@launch
        saveBackground(photo)
    }

    /** A photo just taken with the camera as the Glass background. */
    fun setBackgroundPhoto(photo: android.graphics.Bitmap) = viewModelScope.launch { saveBackground(photo) }

    private suspend fun saveBackground(photo: android.graphics.Bitmap) {
        val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.habitgame.ui.theme.BackgroundPhoto.save(context, photo)
        } ?: return
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(backgroundImagePath = path))
    }

    fun removeBackgroundPhoto() = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.habitgame.ui.theme.BackgroundPhoto.clear(context) }
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(backgroundImagePath = ""))
    }

    fun setBadge(badge: String) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(selectedBadge = badge))
    }

    /** Turns one "minutes before" reminder for tasks and habits on or off. */
    fun toggleTaskReminder(minutes: Int) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        val times = com.habitgame.domain.notification.ReminderTimes.toggle(user.reminderLeadTimes, minutes)
        // The old single setting follows the latest reminder, for older versions reading a transfer code
        val first = com.habitgame.domain.notification.ReminderTimes.parse(times).lastOrNull() ?: user.notificationLeadMinutes
        userRepo.updateUser(user.copy(reminderLeadTimes = times, notificationLeadMinutes = first))
        trackerManager.rescheduleAllReminders()
    }

    /** Turns one "minutes before the deadline" reminder for shopping items on or off. */
    fun toggleDeadlineReminder(minutes: Int) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(deadlineReminderTimes = com.habitgame.domain.notification.ReminderTimes.toggle(user.deadlineReminderTimes, minutes)))
        trackerManager.rescheduleAllReminders()
    }

    fun setAccentColor(hex: String) = viewModelScope.launch {
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(accentColorHex = hex))
    }

    fun setDisplayName(name: String) = viewModelScope.launch {
        val trimmed = name.trim().take(20)
        if (trimmed.isBlank()) return@launch
        val user = userRepo.getUser() ?: UserEntity()
        userRepo.updateUser(user.copy(displayName = trimmed))
    }

    /** Permanently delete ALL data and re-seed the shop */
    fun resetAllData() = viewModelScope.launch {
        taskRepo.getAllTasks().forEach { com.habitgame.domain.notification.TaskReminderWorker.cancelReminder(context, it.id) }
        taskRepo.deleteAll()
        trackerRepo.deleteAll()
        habitRepo.deleteAll()
        economyRepo.deleteAllRewards()
        appLockRepo.deleteAll()
        noteDao.deleteAll()
        rewardClaimDao.deleteAll()
        daySkipDao.deleteAll()
        areaDao.deleteAll()
        checklistDao.deleteAll()
        userRepo.deleteAll()  // Also re-creates a fresh UserEntity
        economyRepo.seedDefaultShop()
        trackerManager.runDailyMaintenance()  // fresh 100-day challenge starting today
    }
}
