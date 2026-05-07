package app.maestri.remote.presentation.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.maestri.remote.R
import app.maestri.remote.domain.repository.ConnectionState
import java.time.Duration
import java.time.Instant

object ConnectionStateFormatter {

    @Composable
    fun formatMessage(state: ConnectionState, now: Instant = Instant.now()): String {
        val context = LocalContext.current
        return when (state) {
            is ConnectionState.Connected -> context.getString(R.string.connection_connected)
            is ConnectionState.Disconnected -> context.getString(R.string.connection_disconnected)
            is ConnectionState.Discovering -> context.getString(R.string.connection_discovering)
            is ConnectionState.Connecting -> context.getString(R.string.connection_connecting)
            is ConnectionState.Reconnecting -> context.getString(
                R.string.connection_reconnecting,
                state.attempt,
                formatStaleAge(state.lastSync, now)
            )
            is ConnectionState.Stale -> formatStaleAge(state.lastSync, now)
        }
    }

    @Composable
    private fun formatStaleAge(lastSync: Instant, now: Instant): String {
        val context = LocalContext.current
        val duration = Duration.between(lastSync, now)
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> context.getString(R.string.last_sync_just_now)
            minutes < 60 -> context.getString(R.string.last_sync_minutes, minutes)
            else -> context.getString(R.string.last_sync_hour)
        }
    }
}