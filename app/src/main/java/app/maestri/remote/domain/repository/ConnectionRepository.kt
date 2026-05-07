package app.maestri.remote.domain.repository

import app.maestri.remote.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

interface ConnectionRepository {
    val connectionState: StateFlow<ConnectionState>
    val workspace: StateFlow<Workspace?>

    suspend fun connect(pin: String, host: String? = null)
    fun disconnect()

    /**
     * Sends a raw message to the Mac bridge.
     */
    suspend fun sendMessage(message: String)

    /**
     * Sends a node action (stop/restart) to the Mac bridge.
     */
    suspend fun sendNodeAction(nodeId: String, action: String)

    /**
     * Kills all active agent nodes.
     */
    suspend fun killAllNodes()
}

sealed interface ConnectionState {
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int, val lastSync: Instant) : ConnectionState
    data class Disconnected(val since: Instant) : ConnectionState
    data class Stale(val lastSync: Instant) : ConnectionState
    
    // Internal transitions
    data object Discovering : ConnectionState
    data object Connecting : ConnectionState
}
