package app.maestri.remote.data.repository

import app.maestri.remote.data.model.TerminalInputDto
import app.maestri.remote.domain.model.TerminalLine
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TerminalRepositoryImpl @Inject constructor(
    private val connectionRepositoryProvider: Provider<ConnectionRepository>,
    private val json: Json
) : TerminalRepository {

    private val buffers = ConcurrentHashMap<String, ArrayDeque<TerminalLine>>()
    private val _updates = MutableStateFlow<Map<String, Unit>>(emptyMap())

    private val ansiRegex = Regex("\u001B\\[[;\\d]*[A-Za-z]")
    private val lineLimit = 2000

    override fun getLines(nodeId: String): Flow<List<TerminalLine>> {
        return _updates.map {
            val buffer = buffers[nodeId]
            if (buffer != null) {
                synchronized(buffer) {
                    buffer.toList()
                }
            } else {
                emptyList()
            }
        }
    }

    override fun addLine(line: TerminalLine) {
        val strippedText = line.text.replace(ansiRegex, "")
        val strippedLine = line.copy(text = strippedText)

        val buffer = buffers.getOrPut(line.nodeId) { ArrayDeque() }
        synchronized(buffer) {
            buffer.addLast(strippedLine)
            while (buffer.size > lineLimit) {
                buffer.removeFirst()
            }
        }
        
        // Trigger update for this nodeId
        _updates.value = _updates.value + (line.nodeId to Unit)
    }

    override suspend fun sendInput(nodeId: String, text: String) {
        val dto = TerminalInputDto(nodeId = nodeId, text = text)
        val message = json.encodeToString(dto)
        connectionRepositoryProvider.get().sendMessage(message)
    }

    override fun clearAll() {
        buffers.clear()
        _updates.value = emptyMap()
    }
}
