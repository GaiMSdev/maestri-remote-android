package app.maestri.remote.domain.model

import kotlinx.serialization.Serializable

data class Workspace(
    val id: String,
    val name: String,
    val nodes: List<Node>,
    val notes: List<Note>,
    val connections: List<Connection>
)

data class Node(
    val id: String,
    val label: String,
    val agentType: AgentType,
    val status: AgentStatus,
    val connectedTo: List<String>,
    val metrics: AgentMetrics? = null
)

data class Note(
    val id: String,
    val title: String,
    val content: String
)

data class VoiceShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val triggerPhrase: String,
    val command: String,
    val targetNodeId: String? = null, // null means global/current
    val isEnabled: Boolean = true
)

data class Connection(
    val fromNodeId: String,
    val toNodeId: String
)

enum class AgentType(val displayName: String) {
    CLAUDE_CODE("Claude Code"),
    CODEX("Codex"),
    GEMINI("Gemini"),
    SHELL("Shell"),
    UNKNOWN("Unknown")
}

enum class AgentStatus {
    IDLE, RUNNING, WAITING, DONE, ERROR, NEEDS_INPUT
}

@Serializable
data class AgentMetrics(
    val tokensPerMinute: Int,
    val sessionCost: Double,
    val currency: String = "USD"
)

@Serializable
data class OmbroSummary(
    val generatedAt: String,
    val text: String,
    val nextSteps: List<String>
)
