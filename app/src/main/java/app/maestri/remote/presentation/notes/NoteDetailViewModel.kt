package app.maestri.remote.presentation.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maestri.remote.domain.model.Note
import app.maestri.remote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import app.maestri.remote.domain.repository.ConnectionRepository
import app.maestri.remote.domain.repository.ConnectionState
import kotlinx.coroutines.flow.map

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val connectionRepository: ConnectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

    val canWrite: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val connectionState: StateFlow<ConnectionState> = connectionRepository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.Connecting
        )

    val note: StateFlow<Note?> = repository.getNote(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateContent(content: String) {
        viewModelScope.launch {
            repository.updateNote(noteId, content)
        }
    }
}
