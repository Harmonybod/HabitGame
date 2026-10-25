package com.habitgame.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.JournalEntryEntity
import com.habitgame.domain.music.music
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.domain.tracker.TrackerPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PastEntry(val day: Int, val date: LocalDate, val entry: JournalEntryEntity)

data class JournalUiState(
    val startDate: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val mood: Int? = null,
    val text: String = "",
    val weight: String = "",
    val saveNote: String = "",
    val pastEntries: List<PastEntry> = emptyList(),
    val weightNote: String = "",
    val music: com.habitgame.domain.music.MusicInfo? = null,   // this day's song or album
    val musicLoading: Boolean = false,
    val musicMessage: String? = null
) {
    val day: Int get() = TrackerManager.dayNumber(startDate.toString(), selectedDate)
    val isToday: Boolean get() = selectedDate == LocalDate.now()
    val canGoBack: Boolean get() = selectedDate.isAfter(startDate)
    val canGoForward: Boolean get() = selectedDate.isBefore(LocalDate.now())
    val prompt: String get() = TrackerPlan.promptFor(day)
}

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val trackerRepo: TrackerRepository,
    private val trackerManager: TrackerManager,
    private val musicResolver: com.habitgame.domain.music.MusicResolver
) : ViewModel() {

    private val _state = MutableStateFlow(JournalUiState())
    val state: StateFlow<JournalUiState> = _state.asStateFlow()

    private var saveJob: Job? = null
    // The first day's entry has been read; anything that changes the entry waits for this
    private val firstLoad = kotlinx.coroutines.CompletableDeferred<Unit>()
    @Volatile private var hasUnsavedChanges = false

    init {
        viewModelScope.launch {
            val challenge = trackerManager.ensureChallenge()
            val start = runCatching { LocalDate.parse(challenge.startDate) }.getOrDefault(LocalDate.now())
            _state.update { it.copy(startDate = minOf(start, LocalDate.now())) }
            load(LocalDate.now())
            firstLoad.complete(Unit)
        }
        viewModelScope.launch {
            combine(trackerRepo.observeJournal(), trackerRepo.observeChallenge()) { entries, challenge ->
                val start = challenge?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                entries to start
            }.collect { (entries, start) ->
                val withDates = entries.mapNotNull { e ->
                    runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { PastEntry(TrackerManager.dayNumber(start.toString(), it), it, e) }
                }
                _state.update {
                    it.copy(
                        startDate = minOf(start, LocalDate.now()),
                        pastEntries = withDates.filter { p -> p.entry.text.isNotBlank() || p.entry.mood != null || p.entry.musicUrl != null },
                        weightNote = weightNote(withDates)
                    )
                }
            }
        }
    }

    private suspend fun load(date: LocalDate) {
        val entry = trackerRepo.getJournalEntry(date.toString())
        _state.update {
            it.copy(
                selectedDate = date,
                mood = entry?.mood,
                text = entry?.text.orEmpty(),
                weight = entry?.weightKg?.let { w -> if (w % 1.0 == 0.0) w.toLong().toString() else w.toString() }.orEmpty(),
                saveNote = "",
                music = entry?.music,
                musicLoading = false,
                musicMessage = null
            )
        }
        // A song saved while offline: look up its cover and name now
        entry?.music?.takeIf { it.title == null }?.let { fillInMusic(date, it.url) }
    }

    // ─── Song or album of the day ──────────────────────────────────────────────

    /**
     * Attaches a song or album from a pasted or shared link. Shared links ([forToday]) always
     * go on today's entry; pasted ones on the day being viewed. One per day: a new one replaces the old.
     */
    fun addMusic(text: String, forToday: Boolean = false) {
        val link = com.habitgame.domain.music.MusicLinks.findLink(text)
        if (link == null) {
            _state.update { it.copy(musicMessage = "Paste a link to a song or album.") }
            return
        }
        viewModelScope.launch {
            firstLoad.await()
            if (forToday && !_state.value.isToday) {
                flushSave()
                load(LocalDate.now())
            }
            val date = _state.value.selectedDate
            _state.update { it.copy(musicLoading = true, musicMessage = null) }
            val result = runCatching { musicResolver.resolve(link) }
            val info = result.getOrNull() ?: run {
                val error = result.exceptionOrNull()
                android.util.Log.w("HabitGame", "Couldn't look up $link", error)
                val kind = com.habitgame.domain.music.MusicLinks.kindOf(link)
                if (error is com.habitgame.domain.music.MusicResolver.NotMusicException || kind == null) {
                    _state.update { it.copy(musicLoading = false, musicMessage = "That link isn't a song or an album.") }
                    return@launch
                }
                // No internet (or the site didn't answer): keep the link, fill in the details later
                com.habitgame.domain.music.MusicInfo(link, com.habitgame.domain.music.MusicLinks.platformOf(link), kind)
            }
            if (_state.value.selectedDate != date) return@launch
            _state.update {
                it.copy(
                    music = info,
                    musicLoading = false,
                    musicMessage = if (info.title == null) "Saved. The cover and name will show once you're online." else null
                )
            }
            scheduleSave(immediate = true)
        }
    }

    fun removeMusic() {
        _state.update { it.copy(music = null, musicMessage = null) }
        scheduleSave(immediate = true)
    }

    private fun fillInMusic(date: LocalDate, url: String) {
        viewModelScope.launch {
            val info = runCatching { musicResolver.resolve(url) }
                .onFailure { android.util.Log.w("HabitGame", "Couldn't look up $url", it) }
                .getOrNull() ?: return@launch
            if (_state.value.selectedDate == date && _state.value.music?.url == url) {
                _state.update { it.copy(music = info) }
                scheduleSave(immediate = true)
            }
        }
    }

    fun previousDay() = changeDay(-1)
    fun nextDay() = changeDay(1)

    private fun changeDay(delta: Long) {
        val s = _state.value
        val target = s.selectedDate.plusDays(delta)
        if (target.isBefore(s.startDate) || target.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            flushSave()
            load(target)
        }
    }

    fun setMood(mood: Int) {
        _state.update { it.copy(mood = if (it.mood == mood) null else mood) }
        scheduleSave(immediate = true)
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text) }
        scheduleSave()
    }

    fun setWeight(weight: String) {
        val cleaned = weight.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        _state.update { it.copy(weight = cleaned) }
        scheduleSave()
    }

    private fun scheduleSave(immediate: Boolean = false) {
        hasUnsavedChanges = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!immediate) delay(600)
            save()
        }
    }

    private suspend fun flushSave() {
        saveJob?.cancel()
        if (hasUnsavedChanges) save()
    }

    private fun currentEntry(): JournalEntryEntity {
        val s = _state.value
        return JournalEntryEntity(
            date = s.selectedDate.toString(),
            mood = s.mood,
            text = s.text,
            weightKg = s.weight.toDoubleOrNull()
        ).withMusic(s.music)
    }

    private suspend fun save() {
        hasUnsavedChanges = false
        trackerRepo.saveJournalEntry(currentEntry())
        _state.update { it.copy(saveNote = "Saved") }
    }

    private fun weightNote(entries: List<PastEntry>): String {
        val weighed = entries.filter { it.entry.weightKg != null }.sortedBy { it.date }
        if (weighed.isEmpty()) return "Weigh yourself once a week, same morning, before breakfast."
        val first = weighed.first()
        val last = weighed.last()
        if (first.date == last.date) return "Starting weight logged on day ${first.day}."
        val diff = last.entry.weightKg!! - first.entry.weightKg!!
        val sign = if (diff >= 0) "+" else ""
        return "Day ${first.day}: ${fmt(first.entry.weightKg)} → Day ${last.day}: ${fmt(last.entry.weightKg)} ($sign${String.format("%.1f", diff)} kg)"
    }

    private fun fmt(kg: Double?): String = kg?.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format("%.1f", it) }.orEmpty()

    override fun onCleared() {
        // viewModelScope is already cancelled here; persist anything typed in the last moment
        if (hasUnsavedChanges) {
            val entry = currentEntry()
            kotlinx.coroutines.runBlocking { trackerRepo.saveJournalEntry(entry) }
        }
        super.onCleared()
    }
}
