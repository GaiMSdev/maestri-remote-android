package app.maestri.remote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val label: String,
    val agentType: String,
    val status: String,
    val connectedTo: List<String>
)
