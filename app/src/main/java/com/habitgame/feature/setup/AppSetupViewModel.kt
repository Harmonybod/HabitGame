package com.habitgame.feature.setup

import android.content.Context
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

object SetupPage {
    const val WELCOME = 0
    const val PERMISSIONS = 1
    const val APPS = 2
}

data class AppSetupState(
    val page: Int? = null,                 // null while working out where to start
    val hasUsageStats: Boolean = false,
    val hasOverlay: Boolean = false,
    val installedApps: List<LockableApp> = emptyList(),
    val loadingApps: Boolean = true,
    val selectedPackages: Set<String> = emptySet(),
    val alreadyConfigured: Boolean = false,
    val query: String = ""
) {
    val hasAllPermissions: Boolean get() = hasUsageStats && hasOverlay
    val visibleApps: List<LockableApp>
        get() = if (query.isBlank()) installedApps
        else installedApps.filter { it.name.contains(query.trim(), ignoreCase = true) }
}

@HiltViewModel
class AppSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRestrictionManager: AppRestrictionManager,
    private val appLockRepo: AppLockRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    // "guide" when opened from the first-launch guide, which already explained app locking
    private val fromGuide = savedStateHandle.get<String>("start") == "guide"

    private val _state = MutableStateFlow(AppSetupState())
    val state: StateFlow<AppSetupState> = _state.asStateFlow()

    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    init {
        viewModelScope.launch {
            val locked = appLockRepo.getAll().map { it.packageName }.toSet()
            refreshPermissions()
            val s = _state.value
            // Already set up → go straight to the app list (or to permissions if one was revoked)
            val start = when {
                locked.isEmpty() && !fromGuide -> SetupPage.WELCOME
                s.hasAllPermissions -> SetupPage.APPS
                else -> SetupPage.PERMISSIONS
            }
            _state.update { it.copy(page = start, selectedPackages = locked, alreadyConfigured = locked.isNotEmpty()) }

            val apps = withContext(Dispatchers.IO) { appRestrictionManager.getInstalledApps() }
            _state.update { it.copy(installedApps = apps, loadingApps = false) }
        }
    }

    /** Called whenever the screen comes back into view, e.g. after granting a permission in system settings. */
    fun onResume() {
        val before = _state.value
        refreshPermissions()
        val after = _state.value
        // Both permissions now granted → move on to choosing apps
        if (after.page == SetupPage.PERMISSIONS && after.hasAllPermissions && !before.hasAllPermissions) {
            goTo(SetupPage.APPS)
        }
    }

    private fun refreshPermissions() {
        _state.update {
            it.copy(
                hasUsageStats = appRestrictionManager.hasUsageStatsPermission(),
                hasOverlay = appRestrictionManager.hasOverlayPermission()
            )
        }
    }

    fun goTo(page: Int) = _state.update { it.copy(page = page) }

    fun requestUsageStats() = appRestrictionManager.openUsageStatsSettings()
    fun requestOverlay() = appRestrictionManager.openOverlaySettings()

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun toggleApp(packageName: String) {
        _state.update { state ->
            val current = state.selectedPackages.toMutableSet()
            if (packageName in current) current.remove(packageName) else current.add(packageName)
            state.copy(selectedPackages = current)
        }
    }

    /** The app's launcher icon, loaded once and kept for the session. */
    suspend fun iconFor(packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                drawable.toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        iconCache[packageName] = bitmap
        return bitmap
    }

    fun saveLockedApps(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val apps = s.selectedPackages.map { pkg ->
                val name = s.installedApps.find { it.packageName == pkg }?.name ?: pkg
                AppLockEntity(packageName = pkg, appName = name, isUnlocked = false)
            }
            appLockRepo.replaceLockedApps(apps)
            if (apps.isNotEmpty()) appRestrictionManager.startMonitorService()
            else appRestrictionManager.stopMonitorService()
            onSaved()
        }
    }
}
