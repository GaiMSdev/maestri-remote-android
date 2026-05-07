package app.maestri.remote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.maestri.remote.domain.model.Note

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

fun NoteEntity.toDomain() = Note(
    id = id,
    title = title,
    content = content
)

fun Note.toEntity() = NoteEntity(
    id = id,
    title = title,
    content = content
)
