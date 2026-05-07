package app.maestri.remote.data.database

import androidx.room.*
import app.maestri.remote.data.model.NodeEntity
import app.maestri.remote.data.model.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces LIMIT 1")
    fun getWorkspace(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM nodes WHERE workspaceId = :workspaceId")
    fun getNodesForWorkspace(workspaceId: String): Flow<List<NodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    @Query("UPDATE nodes SET status = :status WHERE id = :nodeId")
    suspend fun updateNodeStatus(nodeId: String, status: String)

    @Transaction
    suspend fun saveWorkspace(workspace: WorkspaceEntity, nodes: List<NodeEntity>) {
        clearAll()
        insertWorkspace(workspace)
        insertNodes(nodes)
    }

    @Query("DELETE FROM workspaces")
    suspend fun clearWorkspaces()

    @Query("DELETE FROM nodes")
    suspend fun clearNodes()

    @Transaction
    suspend fun clearAll() {
        clearWorkspaces()
        clearNodes()
    }
}
