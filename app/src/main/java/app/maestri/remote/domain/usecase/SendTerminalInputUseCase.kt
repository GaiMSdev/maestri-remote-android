package app.maestri.remote.domain.usecase

import app.maestri.remote.domain.repository.TerminalRepository
import javax.inject.Inject

class SendTerminalInputUseCase @Inject constructor(
    private val repository: TerminalRepository
) {
    suspend operator fun invoke(nodeId: String, text: String) {
        repository.sendInput(nodeId, text)
    }
}
