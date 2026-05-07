package app.maestri.remote.domain.usecase

import app.maestri.remote.domain.model.TerminalLine
import app.maestri.remote.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTerminalLinesUseCase @Inject constructor(
    private val repository: TerminalRepository
) {
    operator fun invoke(nodeId: String): Flow<List<TerminalLine>> {
        return repository.getLines(nodeId)
    }
}
