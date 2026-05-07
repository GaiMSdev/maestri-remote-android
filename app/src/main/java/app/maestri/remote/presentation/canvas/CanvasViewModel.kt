package app.maestri.remote.presentation.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maestri.remote.domain.model.AgentStatus
import app.maestri.remote.domain.model.OmbroSummary
import app.maestri.remote.domain.model.Workspace
import app.maestri.remote.core.haptics.HapticOrchestrator
import app.maestri.remote.core.security.BiometricHelper
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.ConnectionState
import app.maestri.remote.domain.repository.OmbroRepository
import app.maestri.remote.domain.repository.TerminalRepository
import app.maestri.remote.domain.repository.SettingsRepository
import app.maestri.remote.domain.usecase.VoiceCommandResult
import app.maestri.remote.domain.usecase.VoiceCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CanvasSummary(
    val activeAgentsCount: Int,
    val totalSessionCost: Double,
    val currency: String
)

sealed interface CanvasUiState {
    data object Loading : CanvasUiState
    data class Success(
        val workspace: Workspace?,
        val connectionState: ConnectionState,
        val ombroSummary: OmbroSummary?,
        val summary: CanvasSummary? = null,
        val isOmbroRefreshing: Boolean = false
    ) : CanvasUiState
    data class Error(val message: String) : CanvasUiState
}

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val ombroRepository: OmbroRepository,
    private val settingsRepository: SettingsRepository,
    private val terminalRepository: TerminalRepository,
    private val voiceCommandUseCase: app.maestri.remote.domain.usecase.VoiceCommandUseCase,
    private val hapticOrchestrator: HapticOrchestrator,
    val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _voiceResult = MutableStateFlow<VoiceCommandResult?>(null)
    val voiceResult: StateFlow<VoiceCommandResult?> = _voiceResult.asStateFlow()

    private var lastNodeStatuses = emptyMap<String, AgentStatus>()

    init {
        viewModelScope.launch {
            connectionRepository.workspace.collect { workspace ->
                val isHapticsEnabled = settingsRepository.isHapticsEnabled.first()
                if (!isHapticsEnabled || workspace == null) {
                    if (workspace != null) {
                        lastNodeStatuses = workspace.nodes.associate { it.id to it.status }
                    }
                    return@collect
                }

                workspace.nodes.forEach { node ->
                    val lastStatus = lastNodeStatuses[node.id]
                    if (node.status != lastStatus) {
                        if (node.status == AgentStatus.ERROR || node.status == AgentStatus.NEEDS_INPUT) {
                            hapticOrchestrator.triggerFeedback(node.status)
                        }
                    }
                }
                lastNodeStatuses = workspace.nodes.associate { it.id to it.status }
            }
        }
    }

    fun executeVoiceCommand(input: String) {
        viewModelScope.launch {
            _voiceResult.value = voiceCommandUseCase.execute(input)
            delay(3000)
            _voiceResult.value = null
        }
    }

    private val isOmbroRefreshing = MutableStateFlow(false)

    val canWrite: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val isBiometricEnabled: StateFlow<Boolean> = settingsRepository.isBiometricEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoApproveEnabled: StateFlow<Boolean> = settingsRepository.isAutoApproveEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val uiState: StateFlow<CanvasUiState> = combine(
        connectionRepository.workspace,
        connectionRepository.connectionState,
        ombroRepository.latestSummary,
        isOmbroRefreshing
    ) { workspace, connectionState, ombroSummary, refreshing ->
        val summary = workspace?.let { ws ->
            val activeAgents = ws.nodes.count { it.status != AgentStatus.IDLE && it.status != AgentStatus.DONE }
            val totalCost = ws.nodes.sumOf { it.metrics?.sessionCost ?: 0.0 }
            val currency = ws.nodes.firstOrNull { it.metrics != null }?.metrics?.currency ?: "USD"
            CanvasSummary(activeAgents, totalCost, currency)
        }
        CanvasUiState.Success(workspace, connectionState, ombroSummary, summary, refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CanvasUiState.Loading
    )

    fun connect(pin: String, host: String? = null) {
        viewModelScope.launch {
            try {
                connectionRepository.connect(pin, host)
            } catch (e: Exception) {
                // errors surfaced via connectionState flow
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
        }
    }

    fun killAllNodes() {
        viewModelScope.launch {
            connectionRepository.killAllNodes()
        }
    }

    fun requestOmbroSummary() {
        viewModelScope.launch {
            isOmbroRefreshing.value = true
            ombroRepository.requestSummary()
            delay(2000)
            isOmbroRefreshing.value = false
        }
    }

    fun stopNode(nodeId: String) {
        viewModelScope.launch {
            connectionRepository.sendNodeAction(nodeId, "stop")
        }
    }

    fun restartNode(nodeId: String) {
        viewModelScope.launch {
            connectionRepository.sendNodeAction(nodeId, "restart")
        }
    }

    fun sendInput(nodeId: String, text: String) {
        viewModelScope.launch {
            terminalRepository.sendInput(nodeId, text)
        }
    }
}
