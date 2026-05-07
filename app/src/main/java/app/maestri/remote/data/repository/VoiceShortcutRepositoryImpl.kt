package app.maestri.remote.data.repository

import app.maestri.remote.data.database.VoiceShortcutDao
import app.maestri.remote.data.model.toDomain
import app.maestri.remote.data.model.toEntity
import app.maestri.remote.domain.model.VoiceShortcut
import app.maestri.remote.domain.repository.VoiceShortcutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceShortcutRepositoryImpl @Inject constructor(
    private val voiceShortcutDao: VoiceShortcutDao
) : VoiceShortcutRepository {

    override fun getAllShortcuts(): Flow<List<VoiceShortcut>> {
        return voiceShortcutDao.getAllShortcuts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addShortcut(shortcut: VoiceShortcut) {
        voiceShortcutDao.insertShortcut(shortcut.toEntity())
    }

    override suspend fun deleteShortcut(shortcut: VoiceShortcut) {
        voiceShortcutDao.deleteShortcut(shortcut.toEntity())
    }

    override suspend fun toggleShortcut(shortcut: VoiceShortcut) {
        val updated = shortcut.copy(isEnabled = !shortcut.isEnabled)
        voiceShortcutDao.insertShortcut(updated.toEntity())
    }
}
