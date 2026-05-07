package app.maestri.remote.data.database

import androidx.room.*
import app.maestri.remote.data.model.VoiceShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceShortcutDao {
    @Query("SELECT * FROM voice_shortcuts")
    fun getAllShortcuts(): Flow<List<VoiceShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: VoiceShortcutEntity)

    @Delete
    suspend fun deleteShortcut(shortcut: VoiceShortcutEntity)
}
