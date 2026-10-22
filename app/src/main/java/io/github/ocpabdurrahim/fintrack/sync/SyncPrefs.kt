package io.github.ocpabdurrahim.fintrack.sync

import android.content.Context
import androidx.core.content.edit

/** Where each collection's download got to, per signed-in user. */
class SyncPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /** Newest server `updatedAt` (millis) already applied for this user and collection. */
    fun lastPulled(uid: String, collection: String): Long = prefs.getLong("pulled.$uid.$collection", 0L)

    fun setLastPulled(uid: String, collection: String, millis: Long) =
        prefs.edit { putLong("pulled.$uid.$collection", millis) }

    /** True once this phone has merged with this user's cloud data. */
    fun firstSyncDone(uid: String): Boolean = prefs.getBoolean("firstSync.$uid", false)

    fun setFirstSyncDone(uid: String) = prefs.edit {
        putBoolean("firstSync.$uid", true)
        putString("everSyncedUid", uid)
    }

    /** Whether any account has ever synced on this phone. */
    val everSynced: Boolean get() = prefs.getString("everSyncedUid", null) != null

    var lastSyncedAt: Long
        get() = prefs.getLong("lastSyncedAt", 0L)
        set(value) = prefs.edit { putLong("lastSyncedAt", value) }
}
