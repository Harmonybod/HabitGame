package com.habitgame.feature.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.domain.engine.ActiveSession
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.ui.components.CoinBalanceChip
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.SessionCountdown
import com.habitgame.ui.components.rememberNow
import com.habitgame.ui.theme.*
import kotlinx.coroutines.launch

private data class ShopCategory(val key: String, val emoji: String, val label: String)

/** The three groups inside Cosmetic. */
private val COSMETIC_GROUPS = listOf(
    ShopCategory("THEME", "🎨", "Themes"),
    ShopCategory("BADGE", "🏅", "Badges"),
    ShopCategory("ICON_PACK", "🖼️", "Icon Packs")
)

private val CATEGORIES = listOf(
    ShopCategory("APP_UNLOCK", "📱", "App Unlock"),
    ShopCategory("ENTERTAINMENT", "🎬", "Entertainment"),
    ShopCategory("BOOSTER", "⚡", "Boosters"),
    ShopCategory("COSMETIC", "🎨", "Cosmetic")
)

@Composable
fun ShopScreen(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    var cosmeticGroup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("THEME") }

    // Swipe left/right between the categories; tapping a tile jumps to it
    val pagerState = rememberPagerState(
        initialPage = CATEGORIES.indexOfFirst { it.key == state.selectedTab }.coerceAtLeast(0),
        pageCount = { CATEGORIES.size }
    )
    LaunchedEffect(pagerState.currentPage) { viewModel.setTab(CATEGORIES[pagerState.currentPage].key) }

    // A countdown reached zero: lock apps again / end the booster
    val finishedCount = state.sessions.values.count { it.isFinished(now) }
    LaunchedEffect(finishedCount) {
        if (finishedCount > 0) viewModel.endFinishedSessions()
    }

    state.purchaseMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            viewModel.dismissMessage()
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reward Shop", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            CoinBalanceChip(state.user.totalCoins)
        }
        Spacer(Modifier.height(16.dp))

        // Categories: big icon with the name underneath, all four visible at once
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATEGORIES.forEach { category ->
                val index = CATEGORIES.indexOf(category)
                CategoryTile(
                    category = category,
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Purchase message
        state.purchaseMessage?.let { msg ->
            val isSuccess = msg.startsWith("✅") || msg.startsWith("⚡")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSuccess) SuccessGreen.copy(0.15f) else DangerRed.copy(0.15f))
                    .border(1.dp, if (isSuccess) SuccessGreen.copy(0.4f) else DangerRed.copy(0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) { Text(msg, color = (if (isSuccess) SuccessGreen else DangerRed).readable(), fontSize = 14.sp) }
        }
        Spacer(Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top
        ) { page ->
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val key = CATEGORIES[page].key
                var list = rewardsFor(key, state.allRewards)
                if (key == "COSMETIC") {
                    // Themes, Badges or Icon Packs
                    item(key = "groups") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            COSMETIC_GROUPS.forEach { group ->
                                FilterChip(
                                    selected = cosmeticGroup == group.key,
                                    onClick = { cosmeticGroup = group.key },
                                    label = { Text("${group.emoji} ${group.label}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                    list = list.filter { it.subcategory.name == cosmeticGroup }
                }
                items(list, key = { it.id }) { item ->
                    ShopItemCard(
                        item = item,
                        user = state.user,
                        session = state.sessions[item.id],
                        now = now,
                        onBuy = { viewModel.purchase(item) },
                        onActivate = { viewModel.activateReward(item) },
                        onApply = { viewModel.applyCosmetic(item) },
                        onRemove = { viewModel.resetCosmetic(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: ShopCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.habitgame.ui.PackEmoji(category.emoji, 38.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            category.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ShopItemCard(
    item: RewardItemEntity,
    user: UserEntity,
    session: ActiveSession?,
    now: Long,
    onBuy: () -> Unit,
    onActivate: () -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit
) {
    val canAfford = user.totalCoins >= item.cost
    val isCosmeticPack = item.themeKey.isNotEmpty() || item.iconPackKey.isNotEmpty() || item.badgeKey.isNotEmpty()
    val inUse = (item.themeKey == "glass" && user.glassEnabled) ||
        (item.themeKey.isNotEmpty() && user.selectedTheme == item.themeKey) ||
        (item.iconPackKey.isNotEmpty() && user.selectedIconPack == item.iconPackKey) ||
        (item.badgeKey.isNotEmpty() && user.selectedBadge == item.badgeKey)

    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Themes show their colours; everything else its emoji
            val theme = item.themeKey.takeIf { it.isNotEmpty() }?.let { com.habitgame.ui.theme.ThemeCatalog.byId(it) }
            if (theme != null) com.habitgame.ui.components.ThemeSwatch(theme, 46.dp)
            else Text(item.iconEmoji, fontSize = 36.sp)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    item.description.replace(" — unlock in Settings after purchase", ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!item.isOwned) {
                        Text("🪙 ${item.cost}", color = GoldCoin.readable(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (item.durationMinutes > 0) {
                        val hours = item.durationMinutes / 60
                        val mins = item.durationMinutes % 60
                        val durationText = if (hours > 0 && mins > 0) "${hours}h ${mins}m" else if (hours > 0) "${hours}h" else "${mins}m"
                        Text("⏱ $durationText", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    when {
                        inUse -> Tag("IN USE", MaterialTheme.colorScheme.primary)
                        session != null -> Tag("RUNNING", MaterialTheme.colorScheme.primary)
                        item.isOwned -> Tag("OWNED", SuccessGreen.readable())
                    }
                }
                // Live countdown while it runs
                if (session != null) {
                    Spacer(Modifier.height(8.dp))
                    SessionCountdown(session, now)
                }
            }
            when {
                session != null -> Unit  // running: nothing to buy or press until it ends
                !item.isOwned -> Button(
                    onClick = onBuy, enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDim, contentColor = BgDark, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Buy", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                isCosmeticPack && inUse -> OutlinedButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

                isCosmeticPack -> Button(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                BoosterManager.isTimed(item) -> OutlinedButton(
                    onClick = onActivate,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) { Text("Use", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
