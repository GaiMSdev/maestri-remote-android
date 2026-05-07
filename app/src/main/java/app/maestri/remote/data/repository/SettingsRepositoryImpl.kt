package app.maestri.remote.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.maestri.remote.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val SAVED_PIN = stringPreferencesKey("saved_pin")
        val BIOMETRIC_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("biometric_enabled")
        val HAPTICS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("haptics_enabled")
        val AUTO_APPROVE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("auto_approve_enabled")
        val GEMINI_NANO_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("gemini_nano_enabled")
    }

    override val savedPin: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.SAVED_PIN]
    }

    override val isBiometricEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.BIOMETRIC_ENABLED] ?: false
    }

    override val isHapticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true
    }

    override val isAutoApproveEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_APPROVE_ENABLED] ?: false
    }

    override val isGeminiNanoEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.GEMINI_NANO_ENABLED] ?: false
    }

    override suspend fun savePin(pin: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_PIN] = pin
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] = enabled
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    override suspend fun setAutoApproveEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_APPROVE_ENABLED] = enabled
        }
    }

    override suspend fun setGeminiNanoEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_NANO_ENABLED] = enabled
        }
    }

    override suspend fun clearSettings() {
        context.settingsDataStore.edit { it.clear() }
    }
}
