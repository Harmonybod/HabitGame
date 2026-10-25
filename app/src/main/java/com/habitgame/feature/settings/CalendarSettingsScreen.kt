package com.habitgame.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitgame.domain.calendar.CalendarSync
import com.habitgame.domain.calendar.PhoneCalendar
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.theme.DangerRed
import com.habitgame.ui.theme.readable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarEntryPoint {
    fun calendarSync(): CalendarSync
}

/** Settings → Calendar: puts tasks, habits and shopping deadlines into Google Calendar. */
@Composable
fun CalendarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { EntryPointAccessors.fromApplication(context.applicationContext, CalendarEntryPoint::class.java).calendarSync() }
    val state by sync.state.collectAsState()
    var allowed by remember { mutableStateOf(sync.hasPermission) }
    var calendars by remember { mutableStateOf(sync.calendars()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = sync.hasPermission
        calendars = sync.calendars()
    }

    SettingsPage("Calendar", onBack) {
        item {
            Text(
                "Your tasks, habits and shopping deadlines go into your calendar, so they show in Google Calendar on every device " +
                    "and your calendar app reminds you with its own alerts. HabitGame only ever changes the events it added.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }

        if (!allowed) {
            item {
                GameCard {
                    Text("📅 Allow calendar access", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("HabitGame needs it to add and update its events.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text("Allow") }
                }
            }
            return@SettingsPage
        }

        item {
            SettingsSectionHeader("Add to this calendar")
            GameCard {
                if (calendars.isEmpty()) {
                    Text(
                        "No calendars on this phone yet. Add your Google account in the phone's Settings → Accounts, or open Google Calendar once.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
                calendars.forEach { cal ->
                    CalendarRow(cal, selected = state.enabled && state.calendarId == cal.id) { sync.enable(cal.id) }
                }
            }
        }

        if (state.enabled) {
            item {
                SettingsSectionHeader("🔔 Reminders")
                GameCard {
                    SwitchRow(
                        "Calendar reminders",
                        "Your calendar app alerts you before each event, at the times set in Notifications.",
                        state.calendarReminders, sync::setCalendarReminders
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.6f), modifier = Modifier.padding(vertical = 10.dp))
                    SwitchRow(
                        "HabitGame reminders too",
                        "HabitGame's own alerts, with a Done ✓ button. Off means only the calendar reminds you. " +
                            "The “still to do” nudge 30 min later still comes from HabitGame.",
                        state.appReminders, sync::setAppReminders
                    )
                }
            }
            item {
                GameCard {
                    Text(
                        "📅 ${state.events} event${if (state.events == 1) "" else "s"} in your calendar" +
                            if (state.lastSynced > 0) " · updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(state.lastSynced)) else "",
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium
                    )
                    state.error?.let { Text(it, color = DangerRed.readable(), style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "It updates by itself whenever you change a task or habit. Turn this on on one phone: your Google account shows the events everywhere.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = sync::syncNow, modifier = Modifier.weight(1f)) { Text("Update now") }
                        OutlinedButton(onClick = sync::disable, modifier = Modifier.weight(1f)) { Text("Turn off", color = DangerRed.readable()) }
                    }
                    Text(
                        "Turning off removes HabitGame's events from the calendar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(cal: PhoneCalendar, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(cal.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(
                cal.account + if (cal.isGoogle) " · Google" else " · this phone only",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
