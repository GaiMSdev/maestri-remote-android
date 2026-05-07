package app.maestri.remote.core.di

import android.content.Context
import androidx.room.Room
import app.maestri.remote.data.database.MaestriDatabase
import app.maestri.remote.data.database.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MaestriDatabase {
        return Room.databaseBuilder(
            context,
            MaestriDatabase::class.java,
            "maestri_database"
        ).build()
    }

    @Provides
    fun provideNoteDao(database: MaestriDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    fun provideWorkspaceDao(database: MaestriDatabase): app.maestri.remote.data.database.WorkspaceDao {
        return database.workspaceDao()
    }

    @Provides
    fun provideVoiceShortcutDao(database: MaestriDatabase): app.maestri.remote.data.database.VoiceShortcutDao {
        return database.voiceShortcutDao()
    }
}
