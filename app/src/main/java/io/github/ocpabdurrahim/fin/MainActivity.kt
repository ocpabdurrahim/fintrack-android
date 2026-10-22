package io.github.ocpabdurrahim.fin

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.ui.FinRoot

// FragmentActivity rather than ComponentActivity: BiometricPrompt needs it.
class MainActivity : FragmentActivity() {
    /** Set by the launcher shortcuts; the UI opens the add screen and clears it. */
    private val pendingAdd = mutableStateOf<TxnType?>(null)
    private val container: AppContainer get() = (application as FinApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FinRoot(
                container = container,
                pendingAdd = pendingAdd.value,
                onPendingAddHandled = { pendingAdd.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.appLock.onBackground()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_ADD) return
        val requested = intent.getStringExtra(EXTRA_TYPE)
        pendingAdd.value = TxnType.entries.firstOrNull { it.name == requested } ?: TxnType.EXPENSE
    }

    companion object {
        const val ACTION_ADD = "io.github.ocpabdurrahim.fin.action.ADD"
        const val EXTRA_TYPE = "type"
    }
}
