package app.maestri.remote.presentation.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.maestri.remote.R
import app.maestri.remote.domain.usecase.VoiceCommandResult

@Composable
fun VoiceCommandResult.format(): String {
    return when (this) {
        is VoiceCommandResult.NoWorkspace -> stringResource(R.string.voice_no_workspace)
        is VoiceCommandResult.NoFailingFound -> stringResource(R.string.voice_no_failing)
        is VoiceCommandResult.Restarting -> stringResource(R.string.voice_restarting, this.count)
        is VoiceCommandResult.StoppingAll -> stringResource(R.string.voice_stopping)
        is VoiceCommandResult.ActiveAgents -> stringResource(R.string.voice_active_agents, this.count)
        is VoiceCommandResult.RequiresConfirmation -> stringResource(R.string.voice_requires_confirmation, this.action.replace("_", " "))
        is VoiceCommandResult.Unrecognized -> stringResource(R.string.voice_unrecognized, this.input)
    }
}