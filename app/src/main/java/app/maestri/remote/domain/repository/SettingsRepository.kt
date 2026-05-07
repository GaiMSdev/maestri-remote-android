package app.maestri.remote.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val savedPin: Flow<String?>
    val isBiometricEnabled: Flow<Boolean>
    val isHapticsEnabled: Flow<Boolean>
    val isAutoApproveEnabled: Flow<Boolean>
    val isGeminiNanoEnabled: Flow<Boolean>

    suspend fun savePin(pin: String)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setAutoApproveEnabled(enabled: Boolean)
    suspend fun setGeminiNanoEnabled(enabled: Boolean)
    suspend fun clearSettings()
}
