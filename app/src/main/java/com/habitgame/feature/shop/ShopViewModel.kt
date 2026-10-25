package com.habitgame.feature.shop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.RewardCategory
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.entity.RewardSubcategory
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.EconomyRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.ActiveSession
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.notification.SessionEndWorker
import com.habitgame.domain.restriction.AppRestrictionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val user: UserEntity = UserEntity(),
    val rewards: List<RewardItemEntity> = emptyList(),
    val allRewards: List<RewardItemEntity> = emptyList(),
    val sessions: Map<Long, ActiveSession> = emptyMap(),  // running timers by item id
    val selectedTab: String = "APP_UNLOCK",
    val purchaseMessage: String? = null
)

/** The Shop items listed under one category tab. */
fun rewardsFor(tab: String, rewards: List<RewardItemEntity>): List<RewardItemEntity> = when (tab) {
    "APP_UNLOCK"    -> rewards.filter { it.subcategory == RewardSubcategory.APP_UNLOCK }
    "ENTERTAINMENT" -> rewards.filter { it.subcategory == RewardSubcategory.ENTERTAINMENT }
    "BOOSTER"       -> rewards.filter { it.category == RewardCategory.BOOSTER }
    "COSMETIC"      -> rewards.filter { it.category == RewardCategory.COSMETIC }
    else            -> rewards
}

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val economyRepo: EconomyRepository,
    private val economyManager: EconomyManager,
    private val boosterManager: BoosterManager,
    private val appRestrictionManager: AppRestrictionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _tab = MutableStateFlow("APP_UNLOCK")
    private val _message = MutableStateFlow<String?>(null)

    val state: StateFlow<ShopUiState> = combine(
        userRepo.observeUser(),
        economyRepo.observeAllRewards(),
        boosterManager.observeActiveSessions(),
        _tab,
        _message
    ) { user, rewards, sessions, tab, message ->
        ShopUiState(
            user = user ?: UserEntity(),
            rewards = rewardsFor(tab, rewards),
            allRewards = rewards,
            sessions = sessions.associateBy { it.item.id },
            selectedTab = tab,
            purchaseMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopUiState())

    init {
        viewModelScope.launch { economyRepo.seedDefaultShop() }
        viewModelScope.launch { endFinishedSessions() }
    }

    fun setTab(tab: String) { _tab.value = tab }

    fun purchase(item: RewardItemEntity) {
        viewModelScope.launch {
            val balance = state.value.user.totalCoins
            if (balance < item.cost) {
                _message.value = "❌ Not enough coins! Need ${item.cost - balance} more."
                return@launch
            }
            val success = economyManager.spendCoins(item.cost, "Purchased: ${item.name}")
            if (!success) {
                _message.value = "❌ Purchase failed. Try again."
                return@launch
            }
            economyRepo.purchaseReward(item)
            when {
                // Unlocks, entertainment and boosters start counting down right away
                BoosterManager.isTimed(item) -> {
                    start(item)
                    _message.value = "✅ ${item.name} started!"
                }
                // Badge → auto-equip to profile
                item.badgeKey.isNotEmpty() -> {
                    val user = userRepo.getUser() ?: return@launch
                    userRepo.updateUser(user.copy(selectedBadge = item.badgeKey))
                    _message.value = "✅ ${item.badgeKey} ${item.name} equipped to your profile!"
                }
                // Theme / icon pack → apply straight away (can be switched back in the Shop or Settings)
                item.themeKey.isNotEmpty() || item.iconPackKey.isNotEmpty() -> {
                    applyCosmetic(item)
                    _message.value = "✅ ${item.name} unlocked and applied!"
                }
                else -> _message.value = "✅ ${item.name} purchased!"
            }
        }
    }

    /** Starts an owned timed item that isn't running yet (e.g. bought with an older version). */
    fun activateReward(item: RewardItemEntity) {
        viewModelScope.launch {
            if (!item.isOwned || item.isActive) return@launch
            start(item)
            _message.value = "✅ ${item.name} started!"
        }
    }

    private suspend fun start(item: RewardItemEntity) {
        boosterManager.startSession(item)
        val now = System.currentTimeMillis()
        val session = ActiveSession(item.copy(isActive = true, activatedAt = now, usedMillis = 0), now, BoosterManager.durationMillis(item))
        SessionEndWorker.schedule(context, session)
        // The lock service shows the countdown bubble and re-locks apps when time is up
        if (session.unlocksApps) appRestrictionManager.startMonitorIfNeeded()
    }

    /** Called by the screen when a countdown reaches zero. */
    fun endFinishedSessions() {
        viewModelScope.launch {
            SessionEndWorker.notifyEnded(context, boosterManager.endFinishedSessions())
        }
    }

    fun dismissMessage() { _message.value = null }

    /** Switches the whole app to an owned theme or icon pack. */
    fun applyCosmetic(item: RewardItemEntity) {
        viewModelScope.launch {
            val user = userRepo.getUser() ?: return@launch
            when {
                item.themeKey == "glass" -> userRepo.updateUser(user.copy(glassEnabled = true))
                item.themeKey.isNotEmpty() -> userRepo.updateUser(user.copy(selectedTheme = item.themeKey))
                item.iconPackKey.isNotEmpty() -> userRepo.updateUser(user.copy(selectedIconPack = item.iconPackKey))
                item.badgeKey.isNotEmpty() -> userRepo.updateUser(user.copy(selectedBadge = item.badgeKey))
            }
        }
    }

    /** Back to the free default look (or no badge). */
    fun resetCosmetic(item: RewardItemEntity) {
        viewModelScope.launch {
            val user = userRepo.getUser() ?: return@launch
            userRepo.updateUser(
                when {
                    item.themeKey == "glass" -> user.copy(glassEnabled = false)
                    item.themeKey.isNotEmpty() -> user.copy(selectedTheme = "default")
                    item.iconPackKey.isNotEmpty() -> user.copy(selectedIconPack = "default")
                    else -> user.copy(selectedBadge = "")
                }
            )
        }
    }
}
