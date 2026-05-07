package app.maestri.remote.core.di

import app.maestri.remote.data.repository.*
import app.maestri.remote.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        connectionRepositoryImpl: ConnectionRepositoryImpl
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindTerminalRepository(
        terminalRepositoryImpl: TerminalRepositoryImpl
    ): TerminalRepository

    @Binds
    @Singleton
    abstract fun bindOmbroRepository(
        ombroRepositoryImpl: OmbroRepositoryImpl
    ): OmbroRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVoiceShortcutRepository(
        voiceShortcutRepositoryImpl: VoiceShortcutRepositoryImpl
    ): VoiceShortcutRepository
}
