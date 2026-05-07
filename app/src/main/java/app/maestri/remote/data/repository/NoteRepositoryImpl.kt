package app.maestri.remote.data.repository

import app.maestri.remote.data.database.NoteDao
import app.maestri.remote.data.model.MessageDto
import app.maestri.remote.data.model.NoteUpdateDto
import app.maestri.remote.data.model.toDomain
import app.maestri.remote.data.model.toEntity
import app.maestri.remote.domain.model.Note
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val connectionRepositoryProvider: Provider<ConnectionRepository>,
    private val json: Json
) : NoteRepository {

    override fun getNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getNote(id: String): Flow<Note?> {
        return noteDao.getNoteFlowById(id).map { it?.toDomain() }
    }

    override suspend fun updateNote(id: String, content: String) {
        // Optimistic UI: Update local database immediately if content changed
        val currentNote = noteDao.getNoteById(id)
        if (currentNote != null && currentNote.content != content) {
            val updatedEntity = currentNote.copy(content = content)
            noteDao.insertNote(updatedEntity)
        }

        // Send update to Mac bridge
        val dto = NoteUpdateDto(noteId = id, content = content)
        val message = json.encodeToString<MessageDto>(dto)
        connectionRepositoryProvider.get().sendMessage(message)
    }

    override suspend fun syncNotes(notes: List<Note>) {
        noteDao.clearAll()
        noteDao.insertNotes(notes.map { it.toEntity() })
    }
}
