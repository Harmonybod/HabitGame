package com.habitgame.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.ui.components.*
import kotlinx.coroutines.launch

private data class Step(val tag: String, val title: String, val body: String)

private val STEPS = listOf(
    Step("Welcome", "Your next 100 days, as a game",
        "HabitGame turns the habits you want — reading, coding, eating well, saving — into a game you play every day."),
    Step("Step 1", "Tick off your day",
        "Your tasks and habits remind you at their time. Every one you finish fills today's circle on the 100-day board."),
    Step("Step 2", "Earn XP and coins",
        "Finishing things earns XP to level up and coins to spend. Keep your streak going for bonus rewards."),
    Step("Step 3", "Journal, weight and savings",
        "Write a few lines each day, log your weight, and put money aside with savings tasks."),
    Step("Step 4", "Lock distracting apps",
        "Pick the apps that pull you away. They stay locked until you spend coins in the Shop to open them — with a timer.")
)

/** The guide shown on first launch (and from Settings → Show intro again). */
@Composable
fun OnboardingScreen(onFinish: (lockApps: Boolean) -> Unit) {
    val pagerState = rememberPagerState { STEPS.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == STEPS.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLast) TextButton(onClick = { onFinish(false) }) { Text("Skip") }
            else Spacer(Modifier.height(48.dp))
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val step = STEPS[page]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    StepIllustration(page)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    step.tag.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    step.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    step.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Dots
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(STEPS.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 22.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        if (isLast) {
            Button(
                onClick = { onFinish(true) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Choose apps to lock", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) { Text("Maybe later") }
        } else {
            Button(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (pagerState.currentPage == 0) "Show me how" else "Next", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** A small preview of the real thing for each step. */
@Composable
private fun StepIllustration(page: Int) {
    when (page) {
        0 -> {
            // A board that's a few days in
            val demo = remember { List(100) { i -> when { i < 6 -> 1f; i == 6 -> 0.6f; else -> 0f } } }
            HundredDayBoard(scores = demo, currentDay = 7, selectedDay = null, onDayClick = {}, modifier = Modifier.width(260.dp))
        }
        1 -> GameCard(Modifier.width(300.dp)) {
            listOf("7:30 AM" to "Make my bed", "8:00 AM" to "Breakfast with protein", "5:00 PM" to "Practice coding")
                .forEachIndexed { i, (time, name) ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundCheck(checked = i < 2, onClick = {}, enabled = false)
                        Spacer(Modifier.width(12.dp))
                        Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(66.dp))
                        Text(name, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
        }
        2 -> GameCard(Modifier.width(300.dp)) {
            XpProgressBar(level = 4, title = "Beginner", progress = 0.65f, currentXp = 260, xpRequired = 400)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoinBalanceChip(1250)
                StreakBadge(12)
            }
        }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("📓", "⚖️", "💰").forEach { emoji ->
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 40.sp) }
            }
        }
        else -> Box(contentAlignment = Alignment.Center) {
            ProgressRing(progress = 0.7f, modifier = Modifier.size(170.dp), strokeWidth = 10.dp) {
                Text("🔒", fontSize = 64.sp)
            }
        }
    }
}
