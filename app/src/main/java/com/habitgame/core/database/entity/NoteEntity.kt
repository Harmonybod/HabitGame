package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A note in the Notes tab: written here, or shared in from another app such as Samsung Notes
 * ([source] is that app's name, empty for notes written in HabitGame).
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val source: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
