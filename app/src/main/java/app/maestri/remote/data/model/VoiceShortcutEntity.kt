package app.maestri.remote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.maestri.remote.domain.model.VoiceShortcut

@Entity(tableName = "voice_shortcuts")
data class VoiceShortcutEntity(
    @PrimaryKey val id: String,
    val triggerPhrase: String,
    val command: String,
    val targetNodeId: String?,
    val isEnabled: Boolean
)

fun VoiceShortcutEntity.toDomain() = VoiceShortcut(
    id = id,
    triggerPhrase = triggerPhrase,
    command = command,
    targetNodeId = targetNodeId,
    isEnabled = isEnabled
)

fun VoiceShortcut.toEntity() = VoiceShortcutEntity(
    id = id,
    triggerPhrase = triggerPhrase,
    command = command,
    targetNodeId = targetNodeId,
    isEnabled = isEnabled
)
