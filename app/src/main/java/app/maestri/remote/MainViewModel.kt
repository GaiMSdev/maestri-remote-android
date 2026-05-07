package app.maestri.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.ConnectionState
import app.maestri.remote.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.Connecting
        )

    init {
        autoConnect()
    }

    private fun autoConnect() {
        viewModelScope.launch {
            val pin = settingsRepository.savedPin.firstOrNull()
            if (!pin.isNullOrBlank()) {
                repository.connect(pin)
            }
        }
    }

    fun reconnect() {
        autoConnect()
    }
}
