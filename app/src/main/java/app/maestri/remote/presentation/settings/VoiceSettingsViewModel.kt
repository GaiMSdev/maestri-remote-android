package app.maestri.remote.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maestri.remote.domain.model.VoiceShortcut
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.ConnectionState
import app.maestri.remote.domain.repository.SettingsRepository
import app.maestri.remote.domain.repository.VoiceShortcutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voiceShortcutRepository: VoiceShortcutRepository,
    private val settingsRepository: SettingsRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    val canWrite: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val shortcuts: StateFlow<List<VoiceShortcut>> = voiceShortcutRepository.getAllShortcuts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isGeminiNanoEnabled: StateFlow<Boolean> = settingsRepository.isGeminiNanoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setGeminiNanoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGeminiNanoEnabled(enabled)
        }
    }

    fun addShortcut(triggerPhrase: String, command: String) {
        viewModelScope.launch {
            val shortcut = VoiceShortcut(
                triggerPhrase = triggerPhrase,
                command = command
            )
            voiceShortcutRepository.addShortcut(shortcut)
        }
    }

    fun toggleShortcut(shortcut: VoiceShortcut) {
        viewModelScope.launch {
            voiceShortcutRepository.toggleShortcut(shortcut)
        }
    }

    fun deleteShortcut(shortcut: VoiceShortcut) {
        viewModelScope.launch {
            voiceShortcutRepository.deleteShortcut(shortcut)
        }
    }
}
