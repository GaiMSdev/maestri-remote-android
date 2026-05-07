package app.maestri.remote.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MessageDto {
    val type: String
}

@Serializable
@SerialName("workspace_snapshot")
data class WorkspaceSnapshotDto(
    override val type: String = "workspace_snapshot",
    val workspace: WorkspaceDto
) : MessageDto

@Serializable
@SerialName("node_status")
data class NodeStatusDto(
    override val type: String = "node_status",
    val nodeId: String,
    val status: String
) : MessageDto

@Serializable
@SerialName("terminal_line")
data class TerminalLineDto(
    override val type: String = "terminal_line",
    val nodeId: String,
    val line: String,
    val seq: Long
) : MessageDto

@Serializable
@SerialName("ombro_summary")
data class OmbroSummaryDto(
    override val type: String = "ombro_summary",
    val generatedAt: String,
    val text: String,
    val nextSteps: List<String>
) : MessageDto

@Serializable
@SerialName("note_updated")
data class NoteUpdatedDto(
    override val type: String = "note_updated",
    val note: NoteDto
) : MessageDto

@Serializable
@SerialName("session_end")
data class SessionEndDto(
    override val type: String = "session_end",
    val reason: String
) : MessageDto

@Serializable
@SerialName("terminal_input")
data class TerminalInputDto(
    override val type: String = "terminal_input",
    val nodeId: String,
    val text: String
) : MessageDto

@Serializable
@SerialName("node_action")
data class NodeActionDto(
    override val type: String = "node_action",
    val nodeId: String,
    val action: String // "stop" or "restart"
) : MessageDto

@Serializable
@SerialName("ombro_request")
data class OmbroRequestDto(
    override val type: String = "ombro_request"
) : MessageDto

@Serializable
@SerialName("note_update")
data class NoteUpdateDto(
    override val type: String = "note_update",
    val noteId: String,
    val content: String
) : MessageDto

@Serializable
@SerialName("ping")
data class PingDto(
    override val type: String = "ping"
) : MessageDto

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val nodes: List<NodeDto>,
    val notes: List<NoteDto>,
    val connections: List<ConnectionDto>
)

@Serializable
data class NodeDto(
    val id: String,
    val label: String,
    val agentType: String,
    val status: String,
    val connectedTo: List<String>,
    val metrics: AgentMetricsDto? = null
)

@Serializable
data class AgentMetricsDto(
    val tokensPerMinute: Int,
    val sessionCost: Double,
    val currency: String = "USD"
)

@Serializable
data class NoteDto(
    val id: String,
    val title: String,
    val content: String
)

@Serializable
data class ConnectionDto(
    val fromNodeId: String,
    val toNodeId: String
)
