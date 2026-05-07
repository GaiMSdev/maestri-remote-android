package app.maestri.remote.domain.repository

import app.maestri.remote.domain.model.TerminalLine
import kotlinx.coroutines.flow.Flow

interface TerminalRepository {
    /**
     * Streams the terminal lines for a specific node.
     * Initially emits the current buffer, then subsequent updates.
     */
    fun getLines(nodeId: String): Flow<List<TerminalLine>>

    /**
     * Buffers a new line and strips ANSI codes.
     */
    fun addLine(line: TerminalLine)

    /**
     * Sends input to the remote terminal via the connection layer.
     */
    suspend fun sendInput(nodeId: String, text: String)

    /**
     * Clears all buffers (e.g., on disconnect).
     */
    fun clearAll()
}
