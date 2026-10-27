package com.habitgame.domain.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.room.InvalidationTracker
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.habitgame.core.database.AppDatabase
import com.habitgame.domain.transfer.ProgressTransfer
import com.habitgame.domain.transfer.TransferPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A phone signed in to the account, and when the app was last open on it. */
data class SyncDevice(val id: String, val name: String, val lastSeen: Long, val appVersion: String, val isThisPhone: Boolean)

/** A copy of this phone's progress saved just before the account's progress replaced it. */
data class SyncBackup(val file: java.io.File, val savedAt: Long)

data class SyncState(
    val configured: Boolean = false,        // the Firebase config file was in this build
    val name: String? = null,               // signed-in Google account
    val email: String? = null,
    val syncing: Boolean = false,
    val lastSynced: Long? = null,
    val error: String? = null,
    val devices: List<SyncDevice> = emptyList()   // most recently used first
) {
    val signedIn: Boolean get() = email != null || name != null
}

/**
 * Keeps progress in step across phones signed in to the same Google account, using
 * Firebase's free plan. The whole progress snapshot (the same one a transfer code carries)
 * is saved to the account a few seconds after anything changes, and other phones load it
 * as soon as it arrives. If two phones change things at the same moment, the later one wins.
 * A phone that signs in always takes the account's progress (only an empty account is filled from
 * the phone), and before the account's progress replaces what's on a phone, that phone's own
 * progress is backed up so it can be restored from Settings → Manual sync.
 * Each phone also notes its name and when the app was last open on it, for Settings → Devices.
 */
@Singleton
class CloudSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transfer: ProgressTransfer,
    private val db: AppDatabase,
    private val rewardManager: com.habitgame.domain.engine.RewardManager
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val deviceId: String = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    /** When the cloud copy this phone last matched was saved. */
    private var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_SYNCED, value).apply() }

    /** Last time something changed here that isn't in the cloud yet (0 = nothing). */
    private var localChangedAt: Long
        get() = prefs.getLong(KEY_LOCAL_CHANGED, 0L)
        set(value) { prefs.edit().putLong(KEY_LOCAL_CHANGED, value).apply() }

    /** The account this phone has finished its first sync with. */
    private var syncedUid: String?
        get() = prefs.getString(KEY_SYNCED_UID, null)
        set(value) { prefs.edit().putString(KEY_SYNCED_UID, value).apply() }

    private lateinit var scope: CoroutineScope
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val lock = Mutex()
    private var listener: ListenerRegistration? = null
    @Volatile private var applyingRemote = false
    @Volatile private var ready = false   // first cloud check done; local changes may upload

    val configured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private fun doc(uid: String) = FirebaseFirestore.getInstance().collection("users").document(uid)

    @OptIn(FlowPreview::class)
    fun start(appScope: CoroutineScope) {
        scope = appScope
        if (!configured) return
        _state.update { it.copy(configured = true, lastSynced = lastSyncedAt.takeIf { t -> t > 0 }) }
        auth.addAuthStateListener { onAccountChanged(it.currentUser) }
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(SYNCED_TABLES) {
            override fun onInvalidated(tables: Set<String>) = onLocalChange()
        })
        scope.launch { changes.debounce(5_000).collect { upload() } }
    }

    // ─── Signing in and out ─────────────────────────────────────────────────────

    /** Shows Google's sign-in sheet. Returns an error message, or null when signed in (or cancelled). */
    suspend fun signIn(activity: Activity): String? {
        if (!configured) return "Cloud sync isn't set up in this version of the app yet."
        val clientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (clientIdRes == 0) return "Google sign-in isn't set up in Firebase yet."
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(context.getString(clientIdRes)).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return "Google didn't return an account. Please try again."
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (_: NoCredentialException) {
            "There's no Google account on this phone. Add one in the phone's Settings → Accounts, then try again."
        } catch (e: Exception) {
            "Sign-in didn't work: ${e.message ?: "unknown error"}"
        }
    }

    /** Stops syncing. This phone keeps its progress; the cloud copy stays in the account. */
    suspend fun signOut() {
        if (!configured) return
        // This phone leaves the account's list of devices
        auth.currentUser?.let { user ->
            runCatching { withTimeoutOrNull(3_000) { doc(user.uid).update(FieldPath.of("devices", deviceId), FieldValue.delete()).await() } }
        }
        listener?.remove(); listener = null
        auth.signOut()
        syncedUid = null
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    fun syncNow() {
        if (configured && auth.currentUser != null) scope.launch { upload(force = true) }
    }

    // ─── Backups ────────────────────────────────────────────────────────────────

    private val backupDir: java.io.File get() = java.io.File(context.filesDir, "sync_backups").apply { mkdirs() }

    /** Copies of this phone's progress from before the account's progress replaced it, newest first. */
    fun backups(): List<SyncBackup> =
        backupDir.listFiles { f -> f.name.startsWith("backup_") }.orEmpty()
            .mapNotNull { f -> f.name.removePrefix("backup_").removeSuffix(".txt").toLongOrNull()?.let { SyncBackup(f, it) } }
            .sortedByDescending { it.savedAt }

    /** Saves this phone's progress as a transfer code, keeping the newest [MAX_BACKUPS]. */
    private suspend fun backUpThisPhone() {
        if (!transfer.hasProgress()) return
        runCatching {
            java.io.File(backupDir, "backup_${System.currentTimeMillis()}.txt").writeText(transfer.createCode(appVersion()))
            backups().drop(MAX_BACKUPS).forEach { it.file.delete() }
        }
    }

    // ─── Devices ────────────────────────────────────────────────────────────────

    @Volatile private var lastTouch = 0L

    /** Notes that the app is open on this phone now (at most once a minute). */
    fun touchDevice(force: Boolean = false) {
        if (!configured || !::scope.isInitialized) return
        val user = auth.currentUser ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastTouch < 60_000) return
        lastTouch = now
        val info = mapOf("name" to deviceName(), "lastSeen" to now, "app" to appVersionName())
        // Merged into the account's document so the progress saved there is left alone
        runCatching { doc(user.uid).set(mapOf("devices" to mapOf(deviceId to info)), SetOptions.merge()) }
    }

    /** Takes a phone off the list (it's added back the next time the app opens on it while signed in). */
    fun removeDevice(id: String) {
        val user = auth.currentUser ?: return
        runCatching { doc(user.uid).update(FieldPath.of("devices", id), FieldValue.delete()) }
    }

    private fun readDevices(snapshot: DocumentSnapshot): List<SyncDevice> {
        val map = snapshot.get("devices") as? Map<*, *> ?: return emptyList()
        return map.mapNotNull { (id, value) ->
            val info = value as? Map<*, *> ?: return@mapNotNull null
            SyncDevice(
                id = id.toString(),
                name = info["name"]?.toString().orEmpty().ifBlank { "Android phone" },
                lastSeen = (info["lastSeen"] as? Number)?.toLong() ?: 0L,
                appVersion = info["app"]?.toString().orEmpty(),
                isThisPhone = id == deviceId
            )
        }.sortedByDescending { it.lastSeen }
    }

    private fun deviceName(): String {
        val maker = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    // ─── Keeping in step ────────────────────────────────────────────────────────

    private fun onAccountChanged(user: FirebaseUser?) {
        listener?.remove(); listener = null
        ready = false
        _state.update { it.copy(name = user?.displayName, email = user?.email, error = null, devices = emptyList()) }
        if (user == null) return
        if (syncedUid != user.uid) { lastSyncedAt = 0L }
        // Live updates from other phones while the app is open
        listener = doc(user.uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _state.update { it.copy(error = "Can't reach the cloud right now. Changes will sync later.") }
                ready = true
                return@addSnapshotListener
            }
            if (snapshot != null) {
                _state.update { it.copy(devices = readDevices(snapshot)) }
                scope.launch { onRemote(user.uid, snapshot) }
            }
        }
        touchDevice(force = true)
    }

    private suspend fun onRemote(uid: String, snapshot: DocumentSnapshot) = lock.withLock {
        if (snapshot.metadata.hasPendingWrites()) return@withLock   // our own save, echoed back
        // Only trust what the server says. A copy from this phone's cache can look empty just
        // because this phone has never downloaded it, and must never be taken to mean "no progress online".
        if (snapshot.metadata.isFromCache) return@withLock
        val firstSyncForAccount = syncedUid != uid

        if (!snapshot.exists() || snapshot.getString("code") == null) {
            // The server confirms the account has no progress yet: this phone's progress becomes the cloud copy
            ready = true
            syncedUid = uid
            scope.launch {
                rewardManager.signedIn()
                upload(force = true)
            }
            return@withLock
        }
        val savedAt = snapshot.getLong("updatedAt") ?: 0L
        val code = snapshot.getString("code") ?: return@withLock
        val cloud = runCatching { transfer.read(code) }.getOrElse {
            _state.update { s -> s.copy(error = it.message) }
            ready = true
            return@withLock
        }

        if (firstSyncForAccount) {
            // Signing in: the account's progress always wins. Whatever this phone had is backed up first.
            backUpThisPhone()
            applyRemote(cloud, savedAt)
            syncedUid = uid
            ready = true
            // Once the account's progress has settled in, so the bonus is saved on top of it
            // (paid only if this account hasn't had it before)
            scope.launch {
                delay(3_000)
                rewardManager.signedIn()
            }
            return@withLock
        }

        when {
            savedAt <= lastSyncedAt -> Unit                                      // already have it
            localChangedAt > savedAt && ready -> scope.launch { upload() }      // ours is newer
            else -> {                                                           // another phone's newer copy
                // A backup now and then, in case a change from another phone wasn't wanted
                if ((backups().firstOrNull()?.savedAt ?: 0L) < System.currentTimeMillis() - BACKUP_EVERY) backUpThisPhone()
                applyRemote(cloud, savedAt)
            }
        }
        ready = true
    }

    private suspend fun applyRemote(cloud: TransferPayload, savedAt: Long) {
        applyingRemote = true
        try {
            transfer.load(cloud)
            lastSyncedAt = savedAt
            localChangedAt = 0L
            _state.update { it.copy(lastSynced = savedAt, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Couldn't load your cloud progress: ${e.message}") }
        } finally {
            // Room reports the load's own changes a moment later; don't send them back up
            scope.launch { delay(2_000); applyingRemote = false }
        }
    }

    private fun onLocalChange() {
        if (applyingRemote || !ready || auth.currentUser == null) return
        localChangedAt = System.currentTimeMillis()
        changes.tryEmit(Unit)
    }

    private suspend fun upload(force: Boolean = false) {
        val user = auth.currentUser ?: return
        if (!force && (!ready || localChangedAt == 0L)) return
        _state.update { it.copy(syncing = true, error = null) }
        val now = System.currentTimeMillis()
        val result = runCatching {
            val data = mapOf("code" to transfer.createCode(appVersion()), "updatedAt" to now, "device" to deviceId)
            // Saved straight away on the phone; Firebase sends it when there's internet.
            // Merged, so the list of phones in the same document stays.
            val write = doc(user.uid).set(data, SetOptions.merge())
            lastSyncedAt = now
            localChangedAt = 0L
            withTimeoutOrNull(15_000) { write.await() }
        }
        _state.update {
            when {
                result.isFailure -> it.copy(syncing = false, error = "Couldn't save to the cloud: ${result.exceptionOrNull()?.message}")
                result.getOrNull() == null -> it.copy(syncing = false, lastSynced = now, error = "Saved on this phone; it'll reach the cloud when you're online.")
                else -> it.copy(syncing = false, lastSynced = now, error = null)
            }
        }
    }

    private fun appVersion(): Int = runCatching {
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    }.getOrDefault(0)

    companion object {
        private const val KEY_DEVICE = "device_id"
        private const val KEY_LAST_SYNCED = "last_synced_at"
        private const val KEY_LOCAL_CHANGED = "local_changed_at"
        private const val KEY_SYNCED_UID = "synced_uid"
        private const val MAX_BACKUPS = 5
        private const val BACKUP_EVERY = 12 * 60 * 60_000L

        /** Everything a transfer code carries. */
        private val SYNCED_TABLES = arrayOf(
            "user", "tasks", "habits", "habit_completions", "streak_days", "location_zones", "day_progress",
            "journal_entries", "savings_entries", "xp_log", "challenge", "reward_items", "focus_sessions", "notes", "reward_claims"
        )
    }
}
