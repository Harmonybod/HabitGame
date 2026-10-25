package com.habitgame.feature.settings

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.AppLockEntity
import com.habitgame.core.database.repository.AppLockRepository
import com.habitgame.domain.restriction.AppRestrictionManager
import com.habitgame.domain.restriction.LockableApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppLockUiState(
    val locked: List<AppLockEntity> = emptyList(),
    val hasUsageStats: Boolean = true,
    val hasOverlay: Boolean = true,
    val installed: List<LockableApp>? = null      // loaded when the "add apps" list is opened
) {
    val hasAllPermissions: Boolean get() = hasUsageStats && hasOverlay
}

/** Settings → App lock: the locked apps, unlocking one for good, and adding more. */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockRepo: AppLockRepository,
    private val appRestrictionManager: AppRestrictionManager
) : ViewModel() {

    private val permissions = MutableStateFlow(true to true)
    private val installed = MutableStateFlow<List<LockableApp>?>(null)
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    val state: StateFlow<AppLockUiState> = combine(appLockRepo.observeAllApps(), permissions, installed) { locked, (usage, overlay), apps ->
        AppLockUiState(locked, usage, overlay, apps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLockUiState())

    init { refreshPermissions() }

    /** Called when the screen comes back, e.g. after allowing a permission in the phone's settings. */
    fun refreshPermissions() {
        permissions.value = appRestrictionManager.hasUsageStatsPermission() to appRestrictionManager.hasOverlayPermission()
    }

    fun requestUsageStats() = appRestrictionManager.openUsageStatsSettings()
    fun requestOverlay() = appRestrictionManager.openOverlaySettings()

    fun loadInstalledApps() {
        if (installed.value != null) return
        viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { appRestrictionManager.getInstalledApps() }
        }
    }

    fun lock(app: LockableApp) = viewModelScope.launch {
        appLockRepo.insertApp(AppLockEntity(packageName = app.packageName, appName = app.name))
        appRestrictionManager.startMonitorService()
    }

    /** Takes an app off the locked list for good (not a timed unlock from the Shop). */
    fun unlock(app: AppLockEntity) = viewModelScope.launch {
        appLockRepo.removeApp(app.packageName)
        if (appLockRepo.getAll().isEmpty()) appRestrictionManager.stopMonitorService()
    }

    /** The app's launcher icon, loaded once and kept while the screen is open. */
    suspend fun iconFor(packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        iconCache[packageName] = bitmap
        return bitmap
    }
}
