package com.habitgame.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.FocusSessionRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.UserProgressEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserEntity = UserEntity(),
    val xpProgress: Float = 0f,
    val xpRequired: Long = 100L,
    val totalTasksDone: Int = 0,
    val totalFocusSessions: Int = 0,
    val totalFocusHours: Float = 0f,
    val badges: List<Badge> = emptyList()
)

data class Badge(val emoji: String, val name: String, val description: String, val unlocked: Boolean)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val taskRepo: TaskRepository,
    private val sessionRepo: FocusSessionRepository,
    private val progressEngine: UserProgressEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = userRepo.observeUser()
        .map { user ->
            val u = user ?: UserEntity()
            val sessionCount = sessionRepo.getCompletedSessionCount()
            val focusSeconds = sessionRepo.getTotalFocusSeconds()
            val tasksDone = taskRepo.getCompletedTaskCount()
            ProfileUiState(
                user = u,
                xpProgress = progressEngine.progressFraction(u),
                xpRequired = UserProgressEngine.xpRequired(u.level),
                totalTasksDone = tasksDone,
                totalFocusSessions = sessionCount,
                totalFocusHours = focusSeconds / 3600f,
                badges = buildBadgeList(u, tasksDone, sessionCount)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    /**
     * Copies the picked photo into the app's private storage (shrunk to 512px, upright),
     * so it stays even if the original is moved or the gallery permission lapses.
     */
    fun setProfileImage(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { savePhoto(uri) } ?: return@launch
            val user = userRepo.getUser() ?: return@launch
            userRepo.updateUser(user.copy(profileImageUri = saved.absolutePath))
        }
    }

    /** A photo just taken with the camera. */
    fun setProfileImage(photo: Bitmap) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { writePhoto(photo) }.getOrNull() } ?: return@launch
            val user = userRepo.getUser() ?: return@launch
            userRepo.updateUser(user.copy(profileImageUri = saved.absolutePath))
        }
    }

    /** Returns false when the name is blank. */
    fun setDisplayName(name: String): Boolean {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isBlank()) return false
        viewModelScope.launch {
            val user = userRepo.getUser() ?: return@launch
            userRepo.updateUser(user.copy(displayName = trimmed))
        }
        return true
    }

    fun removeProfileImage() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { photoDir().listFiles()?.forEach { it.delete() } }
            val user = userRepo.getUser() ?: return@launch
            userRepo.updateUser(user.copy(profileImageUri = ""))
        }
    }

    private fun photoDir(): File = File(context.filesDir, "profile").apply { mkdirs() }

    private fun savePhoto(uri: Uri): File? = runCatching {
        val maxSize = 512
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies the photo's rotation
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = maxSize.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            val scale = maxSize.toFloat() / maxOf(raw.width, raw.height)
            if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
        }
        writePhoto(bitmap)
    }.getOrNull()

    private fun writePhoto(bitmap: Bitmap): File {
        // New file name each time so every screen notices the change; old photos are removed
        photoDir().listFiles()?.forEach { it.delete() }
        val file = File(photoDir(), "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    companion object {
        const val MAX_NAME = 20
    }

    private fun buildBadgeList(user: UserEntity, tasks: Int, sessions: Int): List<Badge> = listOf(
        Badge("🔥", "On Fire", "Reach a 7-day streak", user.currentStreak >= 7),
        Badge("💎", "Diamond Streak", "Reach a 30-day streak", user.currentStreak >= 30),
        Badge("⚡", "Focused Mind", "Complete 10 focus sessions", sessions >= 10),
        Badge("🏆", "Century Club", "Complete 100 tasks", tasks >= 100),
        Badge("🎯", "Task Master", "Complete 50 tasks", tasks >= 50),
        Badge("🚀", "Level 10", "Reach Level 10", user.level >= 10),
        Badge("⚔️", "Elite", "Reach Elite title", user.title == "Elite" || user.title == "Legendary"),
        Badge("🌟", "Legend", "Reach Level 50", user.level >= 50),
    )
}
