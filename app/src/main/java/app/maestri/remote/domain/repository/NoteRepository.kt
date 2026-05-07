package app.maestri.remote.domain.repository

import app.maestri.remote.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getNotes(): Flow<List<Note>>
    fun getNote(id: String): Flow<Note?>
    suspend fun updateNote(id: String, content: String)
    suspend fun syncNotes(notes: List<Note>)
}
