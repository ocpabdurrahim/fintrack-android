package io.github.ocpabdurrahim.fin

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ocpabdurrahim.fin.data.BackupManager
import io.github.ocpabdurrahim.fin.data.FinDatabase
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.Settings
import io.github.ocpabdurrahim.fin.data.SettingsStore
import io.github.ocpabdurrahim.fin.system.Reminders
import io.github.ocpabdurrahim.fin.system.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        Shortcuts.publish(this)
        container.appScope.launch {
            container.repository.ensureSeeded()
            container.repository.processRecurring(System.currentTimeMillis())
            val settings = container.settings.settings.first()
            if (settings.reminderEnabled) {
                Reminders.ensureScheduled(this@FinApp, settings.reminderHour, settings.reminderMinute)
            }
        }
    }
}

/** Hand-wired dependencies: one database, repository and settings store per process. */
class AppContainer(val app: FinApp) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: FinDatabase = FinDatabase.build(app)
    val repository = FinRepository(database)
    val settings = SettingsStore(app)
    val settingsState: StateFlow<Settings?> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, null)
    val backup = BackupManager(app, repository, settings)
    val appLock = AppLockState()
}

/**
 * Whether the PIN screen is showing. Locks on a cold start and after the app has
 * been in the background for longer than [GRACE_MS]; trips to the system file
 * picker (backup/restore) call [skipNextLock] so they do not ask again.
 */
class AppLockState {
    var unlocked by mutableStateOf(false)
    private var backgroundAt = 0L
    private var skipNext = false

    fun skipNextLock() {
        skipNext = true
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val away = backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt > GRACE_MS
        backgroundAt = 0L
        if (skipNext) {
            skipNext = false
            return
        }
        if (away) unlocked = false
    }

    private companion object {
        const val GRACE_MS = 10_000L
    }
}
