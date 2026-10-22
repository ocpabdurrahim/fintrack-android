package io.github.ocpabdurrahim.fintrack.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.app
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import io.github.ocpabdurrahim.fintrack.data.FinDatabase
import io.github.ocpabdurrahim.fintrack.data.FinRepository
import io.github.ocpabdurrahim.fintrack.data.SettingsStore
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.math.max

data class SyncStatus(
    val state: State = State.SIGNED_OUT,
    val email: String? = null,
    /** Rows changed on this phone that are not uploaded yet. */
    val pending: Int = 0,
    val lastSyncedAt: Long = 0,
    val error: String? = null,
) {
    enum class State { SIGNED_OUT, CONNECTING, SYNCING, UP_TO_DATE, ERROR }
}

/**
 * Keeps the phone's database and the user's Firestore data in step
 * (docs/sync-protocol.md). The phone's database stays the source of truth for
 * the screens: the cloud only ever feeds it, so the app works the same offline.
 *
 *  - Upload: rows marked dirty are written to Firestore in batches, then marked clean.
 *  - Download: one listener per collection, for documents changed since the newest
 *    one already applied; a row with unsent changes is never overwritten.
 *  - First sign-in on this phone: the cloud copy wins for records both have, and
 *    records only the phone has are uploaded.
 */
class SyncManager(
    context: Context,
    private val db: FinDatabase,
    private val repository: FinRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val prefs = SyncPrefs(context)
    private val tables = syncTables(db)
    private val auth get() = Firebase.auth
    private val firestore get() = Firebase.firestore

    private val _status = MutableStateFlow(SyncStatus(lastSyncedAt = prefs.lastSyncedAt))
    val status: StateFlow<SyncStatus> = _status

    /** Where the web version of the app lives (Firebase Hosting's default address). */
    val webUrl: String get() = "https://${Firebase.app.options.projectId}.web.app"

    private var session: Job? = null
    private var currentUid: String? = null
    private val serverLoaded = MutableStateFlow(emptySet<String>())
    @Volatile private var lastRemoteCurrency: Currency? = null

    fun start() {
        auth.addAuthStateListener { onUser(it.currentUser) }
    }

    suspend fun signInWithGoogle(activity: Activity) {
        val token = GoogleSignIn.idToken(activity)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    }

    /** Stops syncing. Everything stays on the phone and in the cloud. */
    fun signOut() = auth.signOut()

    private fun onUser(user: FirebaseUser?) {
        val uid = user?.uid
        if (uid == currentUid) return
        currentUid = uid
        session?.cancel()
        if (user == null || uid == null) {
            _status.value = SyncStatus(lastSyncedAt = prefs.lastSyncedAt)
            // Never synced: deletions have no one to tell, so drop their tombstones.
            if (!prefs.everSynced) scope.launch { tables.forEach { it.purgeTombstones() } }
            return
        }
        _status.value = SyncStatus(state = SyncStatus.State.CONNECTING, email = user.email, lastSyncedAt = prefs.lastSyncedAt)
        session = scope.launch { runWithRetry(uid) }
    }

    private suspend fun runWithRetry(uid: String) {
        var backoff = 5_000L
        while (true) {
            try {
                runSession(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync stopped; retrying in ${backoff / 1000}s", e)
                _status.update { it.copy(state = SyncStatus.State.ERROR, error = e.message ?: e.javaClass.simpleName) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
            }
        }
    }

    /** Runs until cancelled (sign-out) or until something fails, which restarts it. */
    private suspend fun runSession(uid: String) = coroutineScope {
        serverLoaded.value = emptySet()
        if (!prefs.firstSyncDone(uid)) firstSync(uid)
        launch { pushLoop(uid) }
        tables.forEach { table -> launch { pullLoop(uid, table) } }
        launch { syncCurrency(uid) }
        launch {
            combine(db.syncDao().observePending(), serverLoaded) { pending, loaded -> pending to loaded.size }
                .collect { (pending, loaded) ->
                    val state = if (pending == 0 && loaded == tables.size) SyncStatus.State.UP_TO_DATE else SyncStatus.State.SYNCING
                    _status.update { it.copy(state = state, pending = pending, error = null) }
                }
        }
    }

    private fun collection(uid: String, name: String) = firestore.collection("users").document(uid).collection(name)
    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    private suspend fun firstSync(uid: String) {
        _status.update { it.copy(state = SyncStatus.State.SYNCING) }
        val downloaded = tables.map { table -> table to collection(uid, table.name).get(Source.SERVER).await() }
        val now = System.currentTimeMillis()
        db.withTransaction {
            // Everything here is a candidate for upload; whatever the cloud has replaces it below.
            tables.forEach { it.markAllDirty() }
            for ((table, snapshot) in downloaded) {
                var newest = 0L
                for (doc in snapshot.documents) {
                    newest = max(newest, doc.updatedMillis())
                    table.applyDocument(doc.id, doc.data ?: continue, now, force = true)
                }
                prefs.setLastPulled(uid, table.name, newest)
            }
        }
        val remote = userDoc(uid).get(Source.SERVER).await()
        if (remote.exists()) applyRemoteCurrency(remote) else pushCurrency(uid, localCurrency())
        prefs.setFirstSyncDone(uid)
        markSynced()
        repository.processRecurring(now)
    }

    private suspend fun pushLoop(uid: String) {
        db.syncDao().observePending().conflate().collect { pending ->
            if (pending > 0) pushAll(uid)
        }
    }

    private suspend fun pushAll(uid: String) {
        var pushed = false
        for (table in tables) {
            val rows = table.pending()
            for (chunk in rows.chunked(400)) {
                val batch = firestore.batch()
                chunk.forEach { batch.set(collection(uid, table.name).document(it.id), it.document) }
                batch.commit().await() // waits while offline; the rows stay dirty until it lands
                // A row edited again meanwhile keeps its newer updatedAt and stays dirty.
                db.withTransaction { chunk.forEach { table.markClean(it.id, it.updatedAt) } }
                pushed = true
            }
            if (rows.isNotEmpty()) table.purgeTombstones()
        }
        if (pushed) markSynced()
    }

    private suspend fun pullLoop(uid: String, table: SyncTable<*>) {
        val since = prefs.lastPulled(uid, table.name)
        // ">=": re-applying the newest document again is harmless; missing one is not.
        val changes = collection(uid, table.name).whereGreaterThanOrEqualTo("updatedAt", Timestamp(Date(since)))
        snapshots(changes).collect { snapshot ->
            val now = System.currentTimeMillis()
            var newest = prefs.lastPulled(uid, table.name)
            var applied = 0
            db.withTransaction {
                for (change in snapshot.documentChanges) {
                    val doc = change.document
                    if (doc.metadata.hasPendingWrites()) continue
                    newest = max(newest, doc.updatedMillis())
                    if (table.applyDocument(doc.id, doc.data, now, force = false)) applied++
                }
            }
            prefs.setLastPulled(uid, table.name, newest)
            if (!snapshot.metadata.isFromCache) serverLoaded.update { it + table.name }
            if (applied > 0) {
                markSynced()
                // A schedule that arrived from the web may already be due here.
                if (table.name == "recurring") repository.processRecurring(now)
            }
        }
    }

    // ---- Currency (the one setting shared with the web) ---------------------

    private data class Currency(val code: String, val symbol: String, val grouping: Grouping)

    private suspend fun localCurrency(): Currency =
        settings.settings.first().let { Currency(it.currencyCode, it.currencySymbol, it.grouping) }

    private suspend fun syncCurrency(uid: String) = coroutineScope {
        launch {
            documentSnapshots(userDoc(uid)).collect { snap ->
                if (snap.exists() && !snap.metadata.hasPendingWrites()) applyRemoteCurrency(snap)
            }
        }
        launch {
            // Only changes made here from now on; at start-up the cloud's value wins.
            settings.settings
                .map { Currency(it.currencyCode, it.currencySymbol, it.grouping) }
                .distinctUntilChanged()
                .drop(1)
                .collect { local -> if (local != lastRemoteCurrency) pushCurrency(uid, local) }
        }
    }

    private suspend fun applyRemoteCurrency(snap: DocumentSnapshot) {
        val code = snap.getString("currencyCode") ?: return
        val symbol = snap.getString("currencySymbol") ?: return
        val grouping = Grouping.entries.firstOrNull { it.name == snap.getString("grouping") } ?: Grouping.SOUTH_ASIAN
        val remote = Currency(code, symbol, grouping)
        lastRemoteCurrency = remote
        if (remote != localCurrency()) {
            settings.update { it.copy(currencyCode = code, currencySymbol = symbol, grouping = grouping) }
        }
    }

    private fun pushCurrency(uid: String, c: Currency) {
        lastRemoteCurrency = c
        userDoc(uid).set(
            mapOf(
                "currencyCode" to c.code,
                "currencySymbol" to c.symbol,
                "grouping" to c.grouping.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "Currency upload failed", it) }
    }

    private fun markSynced() {
        val now = System.currentTimeMillis()
        prefs.lastSyncedAt = now
        _status.update { it.copy(lastSyncedAt = now) }
    }

    private fun snapshots(query: Query): Flow<QuerySnapshot> = callbackFlow {
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private fun documentSnapshots(ref: DocumentReference): Flow<DocumentSnapshot> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.updatedMillis(): Long = getTimestamp("updatedAt")?.toDate()?.time ?: 0L

    private companion object {
        const val TAG = "FinTrackSync"
    }
}
