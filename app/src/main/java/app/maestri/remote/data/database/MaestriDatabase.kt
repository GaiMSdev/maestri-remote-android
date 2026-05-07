package app.maestri.remote.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.maestri.remote.data.model.NodeEntity
import app.maestri.remote.data.model.NoteEntity
import app.maestri.remote.data.model.WorkspaceEntity

@Database(
    entities = [
        NoteEntity::class,
        WorkspaceEntity::class,
        NodeEntity::class,
        app.maestri.remote.data.model.VoiceShortcutEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MaestriDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun voiceShortcutDao(): VoiceShortcutDao
}
