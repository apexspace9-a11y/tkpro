package vn.tietkiem.pro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.tietkiem.pro.security.PinSecurity

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val theme: String = "SYSTEM",
    val pinSalt: String = "",
    val pinHash: String = "",
    val biometricEnabled: Boolean = false
) {
    val hasPin: Boolean get() = pinSalt.isNotBlank() && pinHash.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC = booleanPreferencesKey("biometric")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME] ?: "SYSTEM",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC] ?: false
        )
    }

    suspend fun setTheme(value: String) {
        context.settingsStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setPin(pin: String) {
        val (salt, hash) = PinSecurity.create(pin)
        context.settingsStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    suspend fun clearPin() {
        context.settingsStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
            it[Keys.BIOMETRIC] = false
        }
    }

    suspend fun setBiometric(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.BIOMETRIC] = enabled }
    }
}
