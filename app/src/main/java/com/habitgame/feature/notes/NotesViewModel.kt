package com.habitgame.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.dao.NoteDao
import com.habitgame.core.database.entity.NoteEntity
import com.habitgame.domain.notes.IncomingNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** The Notes tab: every note, filtered by the search box. */
@HiltViewModel
class NotesViewModel @Inject constructor(private val dao: NoteDao) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = combine(dao.observeAll(), _query) { notes, q ->
        if (q.isBlank()) notes
        else notes.filter { it.title.contains(q.trim(), ignoreCase = true) || it.body.contains(q.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    /** Saves a note shared in from another app and hands back its id, to open it. */
    fun saveIncoming(note: IncomingNote, onSaved: (Long) -> Unit) = viewModelScope.launch {
        onSaved(dao.upsert(NoteEntity(title = note.title, body = note.body, source = note.source)))
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch { dao.upsert(note.copy(pinned = !note.pinned)) }
}

/** One note being written or edited. It's saved as you go; an empty new note isn't kept. */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val dao: NoteDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    init {
        viewModelScope.launch { _note.value = (if (id > 0) dao.get(id) else null) ?: NoteEntity(id = 0) }
    }

    fun edit(title: String = _note.value?.title.orEmpty(), body: String = _note.value?.body.orEmpty()) {
        val current = _note.value ?: return
        _note.value = current.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
        save()
    }

    fun togglePin() {
        val current = _note.value ?: return
        _note.value = current.copy(pinned = !current.pinned)
        save()
    }

    // One save at a time, so quick typing in a new note can't insert it twice
    private val saving = kotlinx.coroutines.sync.Mutex()

    private fun save() = viewModelScope.launch {
        saving.withLock {
            val current = _note.value ?: return@withLock
            if (current.title.isBlank() && current.body.isBlank()) return@withLock
            val saved = dao.upsert(current.copy(id = id))
            if (id == 0L) {
                id = saved
                _note.update { it?.copy(id = saved) }
            }
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (id > 0) dao.delete(id)
        onDone()
    }
}
