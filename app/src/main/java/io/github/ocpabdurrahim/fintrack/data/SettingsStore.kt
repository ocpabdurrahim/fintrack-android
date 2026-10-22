package io.github.ocpabdurrahim.fintrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class Settings(
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val grouping: Grouping = Grouping.SOUTH_ASIAN,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Null when the app lock is off. */
    val pinHash: String? = null,
    val biometric: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val hideBalance: Boolean = false,
)

private val Context.finDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.finDataStore

    private object Keys {
        val currencyCode = stringPreferencesKey("currency_code")
        val currencySymbol = stringPreferencesKey("currency_symbol")
        val grouping = stringPreferencesKey("grouping")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pinHash = stringPreferencesKey("pin_hash")
        val biometric = booleanPreferencesKey("biometric")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val hideBalance = booleanPreferencesKey("hide_balance")
    }

    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val s = transform(read(prefs))
            prefs[Keys.currencyCode] = s.currencyCode
            prefs[Keys.currencySymbol] = s.currencySymbol
            prefs[Keys.grouping] = s.grouping.name
            prefs[Keys.theme] = s.theme.name
            prefs[Keys.dynamicColor] = s.dynamicColor
            val pin = s.pinHash
            if (pin == null) prefs.remove(Keys.pinHash) else prefs[Keys.pinHash] = pin
            prefs[Keys.biometric] = s.biometric
            prefs[Keys.reminderEnabled] = s.reminderEnabled
            prefs[Keys.reminderHour] = s.reminderHour
            prefs[Keys.reminderMinute] = s.reminderMinute
            prefs[Keys.hideBalance] = s.hideBalance
        }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            currencyCode = p[Keys.currencyCode] ?: d.currencyCode,
            currencySymbol = p[Keys.currencySymbol] ?: d.currencySymbol,
            grouping = p[Keys.grouping]?.let { name -> Grouping.entries.firstOrNull { it.name == name } } ?: d.grouping,
            theme = p[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: d.theme,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            pinHash = p[Keys.pinHash],
            biometric = p[Keys.biometric] ?: d.biometric,
            reminderEnabled = p[Keys.reminderEnabled] ?: d.reminderEnabled,
            reminderHour = p[Keys.reminderHour] ?: d.reminderHour,
            reminderMinute = p[Keys.reminderMinute] ?: d.reminderMinute,
            hideBalance = p[Keys.hideBalance] ?: d.hideBalance,
        )
    }
}
