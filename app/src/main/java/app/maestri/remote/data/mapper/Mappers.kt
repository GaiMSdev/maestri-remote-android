package app.maestri.remote.data.mapper

import app.maestri.remote.data.model.*
import app.maestri.remote.domain.model.*

fun WorkspaceDto.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    nodes = nodes.map { it.toDomain() },
    notes = notes.map { it.toDomain() },
    connections = connections.map { it.toDomain() }
)

fun NodeDto.toDomain(): Node = Node(
    id = id,
    label = label,
    agentType = try {
        AgentType.valueOf(agentType.uppercase())
    } catch (e: Exception) {
        AgentType.UNKNOWN
    },
    status = try {
        AgentStatus.valueOf(status.uppercase())
    } catch (e: Exception) {
        AgentStatus.IDLE
    },
    connectedTo = connectedTo,
    metrics = metrics?.toDomain()
)

fun AgentMetricsDto.toDomain(): AgentMetrics = AgentMetrics(
    tokensPerMinute = tokensPerMinute,
    sessionCost = sessionCost,
    currency = currency
)

fun NoteDto.toDomain(): Note = Note(
    id = id,
    title = title,
    content = content
)

fun ConnectionDto.toDomain(): Connection = Connection(
    fromNodeId = fromNodeId,
    toNodeId = toNodeId
)

fun TerminalLineDto.toDomain(): TerminalLine = TerminalLine(
    nodeId = nodeId,
    text = line,
    sequence = seq
)

fun OmbroSummaryDto.toDomain(): OmbroSummary = OmbroSummary(
    generatedAt = generatedAt,
    text = text,
    nextSteps = nextSteps
)

fun WorkspaceEntity.toDomain(nodes: List<NodeEntity>): Workspace = Workspace(
    id = id,
    name = name,
    nodes = nodes.map { it.toDomain() },
    notes = emptyList(), // Notes are handled separately by NoteRepository
    connections = emptyList() // Connections are not persisted currently as per requirements
)

fun NodeEntity.toDomain(): Node = Node(
    id = id,
    label = label,
    agentType = try {
        AgentType.valueOf(agentType.uppercase())
    } catch (e: Exception) {
        AgentType.UNKNOWN
    },
    status = try {
        AgentStatus.valueOf(status.uppercase())
    } catch (e: Exception) {
        AgentStatus.IDLE
    },
    connectedTo = connectedTo
)

fun Workspace.toEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name
)

fun Node.toEntity(workspaceId: String): NodeEntity = NodeEntity(
    id = id,
    workspaceId = workspaceId,
    label = label,
    agentType = agentType.name,
    status = status.name,
    connectedTo = connectedTo
)
