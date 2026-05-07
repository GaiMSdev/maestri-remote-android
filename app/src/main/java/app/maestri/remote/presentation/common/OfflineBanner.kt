package app.maestri.remote.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.maestri.remote.R
import app.maestri.remote.domain.repository.ConnectionState

@Composable
fun OfflineBanner(
    connectionState: ConnectionState,
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (connectionState is ConnectionState.Connected) return

    val message = ConnectionStateFormatter.formatMessage(connectionState)

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            if (connectionState is ConnectionState.Disconnected || connectionState is ConnectionState.Stale) {
                TextButton(
                    onClick = onReconnectClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.reconnect))
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
