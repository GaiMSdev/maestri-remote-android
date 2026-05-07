package app.maestri.remote.domain.usecase

import app.maestri.remote.domain.model.AgentStatus
import app.maestri.remote.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed class VoiceCommandResult {
    abstract val input: String

    data class NoWorkspace(override val input: String) : VoiceCommandResult()
    data class NoFailingFound(override val input: String) : VoiceCommandResult()
    data class Restarting(val count: Int, override val input: String) : VoiceCommandResult()
    data class RequiresConfirmation(val action: String, override val input: String) : VoiceCommandResult()
    data object StoppingAll : VoiceCommandResult() {
        override val input: String get() = ""
    }
    data class ActiveAgents(val count: Int, override val input: String) : VoiceCommandResult()
    data class Unrecognized(override val input: String) : VoiceCommandResult()
}

class VoiceCommandUseCase @Inject constructor(
    private val repository: ConnectionRepository
) {
    suspend fun execute(naturalLanguageInput: String): VoiceCommandResult {
        val workspace = repository.workspace.first() ?: return VoiceCommandResult.NoWorkspace(naturalLanguageInput)
        val input = naturalLanguageInput.lowercase()

        return when {
            input.contains("restart") && input.contains("failing") -> {
                val failingNodes = workspace.nodes.filter { it.status == AgentStatus.ERROR }
                if (failingNodes.isEmpty()) {
                    VoiceCommandResult.NoFailingFound(naturalLanguageInput)
                } else {
                    VoiceCommandResult.RequiresConfirmation("restart_failing", naturalLanguageInput)
                }
            }
            input.contains("stop") && input.contains("all") -> {
                VoiceCommandResult.RequiresConfirmation("stop_all", naturalLanguageInput)
            }
            input.contains("check") && input.contains("status") -> {
                val active = workspace.nodes.count { it.status != AgentStatus.IDLE && it.status != AgentStatus.DONE }
                VoiceCommandResult.ActiveAgents(active, naturalLanguageInput)
            }
            else -> VoiceCommandResult.Unrecognized(naturalLanguageInput)
        }
    }
}