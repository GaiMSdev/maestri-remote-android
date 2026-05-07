package app.maestri.remote.domain.repository

import app.maestri.remote.domain.model.VoiceShortcut
import kotlinx.coroutines.flow.Flow

interface VoiceShortcutRepository {
    fun getAllShortcuts(): Flow<List<VoiceShortcut>>
    suspend fun addShortcut(shortcut: VoiceShortcut)
    suspend fun deleteShortcut(shortcut: VoiceShortcut)
    suspend fun toggleShortcut(shortcut: VoiceShortcut)
}
