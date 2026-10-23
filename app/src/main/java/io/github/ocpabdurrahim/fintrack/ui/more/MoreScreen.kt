package io.github.ocpabdurrahim.fintrack.ui.more

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fintrack.AppContainer
import io.github.ocpabdurrahim.fintrack.BuildConfig
import io.github.ocpabdurrahim.fintrack.data.Settings
import io.github.ocpabdurrahim.fintrack.data.ThemeMode
import io.github.ocpabdurrahim.fintrack.domain.Dates
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import io.github.ocpabdurrahim.fintrack.domain.Money
import io.github.ocpabdurrahim.fintrack.domain.Pin
import io.github.ocpabdurrahim.fintrack.system.Biometrics
import io.github.ocpabdurrahim.fintrack.sync.GoogleSignIn
import io.github.ocpabdurrahim.fintrack.sync.SyncStatus
import io.github.ocpabdurrahim.fintrack.system.Reminders
import io.github.ocpabdurrahim.fintrack.system.findActivity
import io.github.ocpabdurrahim.fintrack.update.UpdateState
import io.github.ocpabdurrahim.fintrack.update.UpdateWorker
import io.github.ocpabdurrahim.fintrack.ui.Routes
import io.github.ocpabdurrahim.fintrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.fintrack.ui.components.PinSetupDialog
import io.github.ocpabdurrahim.fintrack.ui.finViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class CurrencyOption(val code: String, val symbol: String, val name: String, val grouping: Grouping)

val currencies = listOf(
    CurrencyOption("BDT", "৳", "Bangladeshi taka", Grouping.SOUTH_ASIAN),
    CurrencyOption("INR", "₹", "Indian rupee", Grouping.SOUTH_ASIAN),
    CurrencyOption("PKR", "Rs", "Pakistani rupee", Grouping.SOUTH_ASIAN),
    CurrencyOption("NPR", "Rs", "Nepalese rupee", Grouping.SOUTH_ASIAN),
    CurrencyOption("LKR", "Rs", "Sri Lankan rupee", Grouping.INTERNATIONAL),
    CurrencyOption("USD", "$", "US dollar", Grouping.INTERNATIONAL),
    CurrencyOption("EUR", "€", "Euro", Grouping.INTERNATIONAL),
    CurrencyOption("GBP", "£", "British pound", Grouping.INTERNATIONAL),
    CurrencyOption("SAR", "SAR", "Saudi riyal", Grouping.INTERNATIONAL),
    CurrencyOption("AED", "AED", "UAE dirham", Grouping.INTERNATIONAL),
    CurrencyOption("QAR", "QAR", "Qatari riyal", Grouping.INTERNATIONAL),
    CurrencyOption("KWD", "KWD", "Kuwaiti dinar", Grouping.INTERNATIONAL),
    CurrencyOption("OMR", "OMR", "Omani rial", Grouping.INTERNATIONAL),
    CurrencyOption("MYR", "RM", "Malaysian ringgit", Grouping.INTERNATIONAL),
    CurrencyOption("SGD", "S$", "Singapore dollar", Grouping.INTERNATIONAL),
    CurrencyOption("CAD", "C$", "Canadian dollar", Grouping.INTERNATIONAL),
    CurrencyOption("AUD", "A$", "Australian dollar", Grouping.INTERNATIONAL),
    CurrencyOption("JPY", "¥", "Japanese yen", Grouping.INTERNATIONAL),
    CurrencyOption("TRY", "₺", "Turkish lira", Grouping.INTERNATIONAL),
    CurrencyOption("IDR", "Rp", "Indonesian rupiah", Grouping.INTERNATIONAL),
)

class MoreViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings?> = c.settingsState
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    private fun say(message: String) {
        messageChannel.trySend(message)
    }

    fun setCurrency(code: String, symbol: String, grouping: Grouping) =
        update { it.copy(currencyCode = code, currencySymbol = symbol, grouping = grouping) }

    fun setGrouping(grouping: Grouping) = update { it.copy(grouping = grouping) }
    fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }
    fun setDynamicColor(on: Boolean) = update { it.copy(dynamicColor = on) }
    fun setBiometric(on: Boolean) = update { it.copy(biometric = on) }

    fun setPin(pin: String) {
        viewModelScope.launch {
            c.settings.update { it.copy(pinHash = Pin.hash(pin)) }
            c.appLock.unlocked = true
            say("App lock is on")
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            c.settings.update { it.copy(pinHash = null, biometric = false) }
            say("App lock is off")
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            c.settings.update { it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute) }
            if (enabled) {
                Reminders.schedule(c.app, hour, minute)
                say("Reminder set for ${formatTime(hour, minute)} every day")
            } else {
                Reminders.cancel(c.app)
            }
        }
    }

    fun skipNextLock() = c.appLock.skipNextLock()

    val sync: StateFlow<SyncStatus> = c.sync.status
    val webUrl: String get() = c.sync.webUrl

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            try {
                c.sync.signInWithGoogle(activity)
                say("Signed in. Your data is syncing.")
            } catch (e: GoogleSignIn.Cancelled) {
                // The user closed the account picker.
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                say("No Google account on this phone. Add one in Android Settings first.")
            } catch (e: Exception) {
                say("Sign-in failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    val updateState: StateFlow<UpdateState> = c.updater.state
    val currentVersion: String = c.updater.currentVersion

    fun canInstallUpdates(): Boolean = c.updater.canInstall()

    fun setAutoUpdate(on: Boolean) {
        update { it.copy(autoUpdate = on) }
        if (on) {
            UpdateWorker.schedule(c.app)
            checkForUpdates()
        } else {
            UpdateWorker.cancel(c.app)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            c.updater.checkAndDownload(userAsked = true)
            if (c.updater.state.value is UpdateState.UpToDate) say("FinTrack $currentVersion is the latest version")
        }
    }

    fun installUpdate() = c.updater.installNow()

    fun signOut() {
        c.sync.signOut()
        say("Signed out. Your data stays on this phone.")
    }

    fun exportJson(uri: Uri) = runFileTask("Backup failed") {
        val n = c.backup.exportJson(uri)
        say("Backup saved ($n transactions)")
    }

    fun importJson(uri: Uri) = runFileTask("Restore failed") {
        val n = c.backup.importJson(uri)
        say("Restored $n transactions")
    }

    fun exportCsv(uri: Uri) = runFileTask("Export failed") {
        val n = c.backup.exportCsv(uri)
        say("Exported $n transactions")
    }

    fun eraseAll() {
        viewModelScope.launch {
            c.repository.resetAll()
            say("All data erased")
        }
    }

    private fun runFileTask(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                say("$failure: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun formatTime(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(timeFormat)

private enum class MoreDialog { CURRENCY, GROUPING, THEME, PIN, REMINDER_TIME, RESTORE_CONFIRM, ERASE, SIGN_OUT }

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val vm = finViewModel { MoreViewModel(it) }
    val settingsOrNull by vm.settings.collectAsStateWithLifecycle()
    val s = settingsOrNull ?: return
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<MoreDialog?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportJson(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = uri
            dialog = MoreDialog.RESTORE_CONFIRM
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dialog = MoreDialog.REMINDER_TIME
    }
    val biometricAvailable = remember { Biometrics.available(context) }
    val sync by vm.sync.collectAsStateWithLifecycle()
    val webHost = vm.webUrl.removePrefix("https://")
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(vm.canInstallUpdates()) }
    // Re-read when coming back from Android's "Install unknown apps" screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = vm.canInstallUpdates() }
    val openInstallSettings = {
        vm.skipNextLock()
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
        }
        Unit
    }
    val openWeb = {
        vm.skipNextLock()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.webUrl))) }
        Unit
    }
    val today = Dates.forFileName(LocalDate.now())

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { GroupTitle("Sync") }
            if (sync.state == SyncStatus.State.SIGNED_OUT) {
                item {
                    SettingRow(
                        Icons.Filled.CloudOff,
                        "Sync with the web",
                        "Sign in with Google to use the same data at $webHost",
                        onClick = { context.findActivity()?.let(vm::signIn) },
                        trailing = {
                            FilledTonalButton(onClick = { context.findActivity()?.let(vm::signIn) }) { Text("Sign in") }
                        },
                        tag = "sync_sign_in",
                    )
                }
            } else {
                item {
                    SettingRow(
                        when (sync.state) {
                            SyncStatus.State.UP_TO_DATE -> Icons.Filled.CloudDone
                            SyncStatus.State.ERROR -> Icons.Filled.SyncProblem
                            else -> Icons.Filled.CloudUpload
                        },
                        sync.email ?: "Signed in",
                        syncText(sync),
                        tag = "sync_status",
                    )
                }
                item { SettingRow(Icons.Filled.Language, "Open FinTrack on the web", webHost, onClick = openWeb) }
                item {
                    SettingRow(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        "Sign out",
                        "Stops syncing; your data stays on this phone",
                        onClick = { dialog = MoreDialog.SIGN_OUT },
                    )
                }
            }

            item { GroupTitle("Manage") }
            item { SettingRow(Icons.Filled.AccountBalanceWallet, "Accounts", "Cash, bank, mobile wallets", onClick = { onOpen(Routes.ACCOUNTS) }, tag = "more_accounts") }
            item { SettingRow(Icons.Filled.Category, "Categories", "Add, rename, recolour", onClick = { onOpen(Routes.CATEGORIES) }, tag = "more_categories") }
            item { SettingRow(Icons.Filled.Repeat, "Recurring", "Rent, salary and other repeating entries", onClick = { onOpen(Routes.RECURRING) }, tag = "more_recurring") }

            item { GroupTitle("Preferences") }
            item {
                SettingRow(
                    Icons.Filled.AttachMoney,
                    "Currency",
                    "${s.currencyCode} (${s.currencySymbol})",
                    onClick = { dialog = MoreDialog.CURRENCY },
                    tag = "more_currency",
                )
            }
            item {
                SettingRow(
                    Icons.Filled.FormatListNumbered,
                    "Number format",
                    Money.format(123456700, s.currencySymbol, s.grouping),
                    onClick = { dialog = MoreDialog.GROUPING },
                )
            }
            item { SettingRow(Icons.Filled.Palette, "Theme", s.theme.label, onClick = { dialog = MoreDialog.THEME }, tag = "more_theme") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingRow(
                        Icons.Filled.ColorLens,
                        "Wallpaper colours",
                        "Match the app to your wallpaper",
                        onClick = { vm.setDynamicColor(!s.dynamicColor) },
                        trailing = { Switch(checked = s.dynamicColor, onCheckedChange = vm::setDynamicColor) },
                    )
                }
            }

            item { GroupTitle("Security") }
            item {
                val lockOn = s.pinHash != null
                SettingRow(
                    Icons.Filled.Lock,
                    "App lock",
                    if (lockOn) "PIN required when FinTrack opens" else "Ask for a ${Pin.LENGTH}-digit PIN when FinTrack opens",
                    onClick = { if (lockOn) vm.disableLock() else dialog = MoreDialog.PIN },
                    trailing = {
                        Switch(
                            checked = lockOn,
                            onCheckedChange = { on -> if (on) dialog = MoreDialog.PIN else vm.disableLock() },
                        )
                    },
                    tag = "more_lock",
                )
            }
            if (s.pinHash != null) {
                item { SettingRow(Icons.Filled.Lock, "Change PIN", null, onClick = { dialog = MoreDialog.PIN }) }
                if (biometricAvailable) {
                    item {
                        SettingRow(
                            Icons.Filled.Fingerprint,
                            "Unlock with fingerprint",
                            "Use your fingerprint or face instead of the PIN",
                            onClick = { vm.setBiometric(!s.biometric) },
                            trailing = { Switch(checked = s.biometric, onCheckedChange = vm::setBiometric) },
                        )
                    }
                }
            }

            item { GroupTitle("Reminder") }
            item {
                fun enableReminder() {
                    if (Reminders.canNotify(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        dialog = MoreDialog.REMINDER_TIME
                    } else {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                SettingRow(
                    Icons.Filled.Notifications,
                    "Daily reminder",
                    if (s.reminderEnabled) "Every day at ${formatTime(s.reminderHour, s.reminderMinute)}" else "A nudge to record the day's spending",
                    onClick = { if (s.reminderEnabled) vm.setReminder(false, s.reminderHour, s.reminderMinute) else enableReminder() },
                    trailing = {
                        Switch(
                            checked = s.reminderEnabled,
                            onCheckedChange = { on -> if (on) enableReminder() else vm.setReminder(false, s.reminderHour, s.reminderMinute) },
                        )
                    },
                )
            }
            if (s.reminderEnabled) {
                item {
                    SettingRow(
                        Icons.Filled.Schedule,
                        "Reminder time",
                        formatTime(s.reminderHour, s.reminderMinute),
                        onClick = { dialog = MoreDialog.REMINDER_TIME },
                    )
                }
            }

            item { GroupTitle("Your data") }
            item {
                SettingRow(Icons.Filled.Backup, "Back up", "Save everything to a file (Drive, Downloads…)", onClick = {
                    vm.skipNextLock()
                    exportLauncher.launch("fintrack-backup-$today.json")
                }, tag = "more_backup")
            }
            item {
                SettingRow(Icons.Filled.Restore, "Restore", "Replace what's here with a backup file", onClick = {
                    vm.skipNextLock()
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
            }
            item {
                SettingRow(Icons.Filled.TableChart, "Export to CSV", "Open your history in Excel or Google Sheets", onClick = {
                    vm.skipNextLock()
                    csvLauncher.launch("fintrack-transactions-$today.csv")
                })
            }
            item {
                SettingRow(
                    Icons.Filled.DeleteForever,
                    "Erase all data",
                    "Start over with an empty FinTrack",
                    onClick = { dialog = MoreDialog.ERASE },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            item { GroupTitle("App updates") }
            item {
                SettingRow(
                    Icons.Filled.SystemUpdate,
                    "Automatic updates",
                    "Download and install new versions from GitHub by itself",
                    onClick = { vm.setAutoUpdate(!s.autoUpdate) },
                    trailing = { Switch(checked = s.autoUpdate, onCheckedChange = vm::setAutoUpdate) },
                    tag = "auto_update",
                )
            }
            item {
                val (title, subtitle) = updateTexts(updateState, vm.currentVersion)
                SettingRow(
                    Icons.Filled.Refresh,
                    title,
                    subtitle,
                    onClick = { if (updateState is UpdateState.Ready) vm.installUpdate() else vm.checkForUpdates() },
                    tag = "check_updates",
                )
            }
            if (!canInstall) {
                item {
                    SettingRow(
                        Icons.Filled.Security,
                        "Allow FinTrack to install updates",
                        "A one-time Android switch: turn on “Allow from this source”",
                        onClick = openInstallSettings,
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                SettingRow(
                    Icons.Filled.Info,
                    "FinTrack ${BuildConfig.VERSION_NAME}",
                    if (sync.state == SyncStatus.State.SIGNED_OUT) "Works offline. Your records stay on this phone."
                    else "Works offline and syncs with $webHost",
                )
            }
        }
    }

    when (dialog) {
        null -> Unit
        MoreDialog.CURRENCY -> CurrencyDialog(
            current = s,
            onDismiss = { dialog = null },
            onPick = { code, symbol, grouping ->
                vm.setCurrency(code, symbol, grouping)
                dialog = null
            },
        )
        MoreDialog.GROUPING -> ChoiceDialog(
            title = "Number format",
            options = Grouping.entries.map { it to Money.format(123456700, s.currencySymbol, it) },
            selected = s.grouping,
            onDismiss = { dialog = null },
            onPick = {
                vm.setGrouping(it)
                dialog = null
            },
        )
        MoreDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to it.label },
            selected = s.theme,
            onDismiss = { dialog = null },
            onPick = {
                vm.setTheme(it)
                dialog = null
            },
        )
        MoreDialog.PIN -> PinSetupDialog(
            onDismiss = { dialog = null },
            onSet = {
                vm.setPin(it)
                dialog = null
            },
        )
        MoreDialog.REMINDER_TIME -> {
            val timeState = rememberTimePickerState(initialHour = s.reminderHour, initialMinute = s.reminderMinute, is24Hour = false)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Remind me at") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setReminder(true, timeState.hour, timeState.minute)
                        dialog = null
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        MoreDialog.RESTORE_CONFIRM -> ConfirmDialog(
            title = "Restore this backup?",
            text = "Everything currently in FinTrack will be replaced by the backup's contents. Make a backup first if you might need today's data.",
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = {
                pendingRestore?.let(vm::importJson)
                pendingRestore = null
                dialog = null
            },
            onDismiss = {
                pendingRestore = null
                dialog = null
            },
        )
        MoreDialog.SIGN_OUT -> ConfirmDialog(
            title = "Sign out?",
            text = "This phone stops syncing. Everything stays here and in your cloud copy; sign in again any time.",
            confirmLabel = "Sign out",
            onConfirm = {
                vm.signOut()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        MoreDialog.ERASE -> ConfirmDialog(
            title = "Erase all data?",
            text = "Every transaction, account, category, budget and recurring entry will be deleted. This can't be undone.",
            confirmLabel = "Erase everything",
            destructive = true,
            onConfirm = {
                vm.eraseAll()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    tag: String? = null,
    tint: Color = Color.Unspecified,
) {
    var modifier: Modifier = Modifier
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    if (tag != null) modifier = modifier.testTag(tag)
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun CurrencyDialog(current: Settings, onDismiss: () -> Unit, onPick: (String, String, Grouping) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Only the symbol changes. Amounts you already entered are not converted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                currencies.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option.code, option.symbol, option.grouping) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.code == current.currencyCode,
                            onClick = { onPick(option.code, option.symbol, option.grouping) },
                        )
                        Text("${option.symbol}  ${option.name}", modifier = Modifier.weight(1f))
                        Text(option.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { if (it.length <= 4) custom = it },
                    label = { Text("Other symbol") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = custom.isNotBlank(),
                onClick = { onPick(custom.trim().uppercase(), custom.trim(), current.grouping) },
            ) { Text("Use symbol") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun syncText(s: SyncStatus): String = when (s.state) {
    SyncStatus.State.SIGNED_OUT -> ""
    SyncStatus.State.CONNECTING -> "Connecting…"
    SyncStatus.State.SYNCING ->
        if (s.pending > 0) "Uploading ${s.pending} change${if (s.pending == 1) "" else "s"}…" else "Syncing…"
    SyncStatus.State.UP_TO_DATE -> "Up to date" + if (s.lastSyncedAt > 0) " · ${ago(s.lastSyncedAt)}" else ""
    SyncStatus.State.ERROR -> "Sync paused (${s.error ?: "error"}). Retrying…"
}

private fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = (now - millis) / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        else -> Dates.full(Dates.toLocalDate(millis))
    }
}

private fun updateTexts(state: UpdateState, current: String): Pair<String, String> = when (state) {
    UpdateState.Idle -> "Check for updates" to "You have FinTrack $current"
    UpdateState.Checking -> "Checking for updates…" to "You have FinTrack $current"
    is UpdateState.UpToDate -> "Check for updates" to "FinTrack $current is the latest · checked ${ago(state.checkedAt)}"
    is UpdateState.Downloading -> "Downloading FinTrack ${state.release.version}…" to "${(state.progress * 100).roundToInt()}%"
    is UpdateState.Ready -> "Install FinTrack ${state.release.version}" to "Downloaded. It installs by itself when you leave the app, or tap to install now."
    is UpdateState.Installing -> "Installing FinTrack ${state.release.version}…" to "FinTrack closes and reopens updated"
    is UpdateState.Failed -> "Check for updates" to "Last attempt failed: ${state.message}"
}
