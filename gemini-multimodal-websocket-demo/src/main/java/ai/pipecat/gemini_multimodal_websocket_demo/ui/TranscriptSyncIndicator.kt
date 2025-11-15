package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Composable that displays transcript synchronization status
 * Shows progress indicator and attempt count during sync
 * Blocks user interaction until sync completes
 */
@Composable
fun TranscriptSyncIndicator(
    syncStatus: SessionManager.SyncStatus,
    onCancelSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (syncStatus) {
        is SessionManager.SyncStatus.Syncing -> {
            // Show blocking dialog during sync
            TranscriptSyncDialog(
                attempt = syncStatus.attempt,
                onCancel = onCancelSync
            )
        }
        is SessionManager.SyncStatus.Error -> {
            if (syncStatus.willRetry) {
                // Show error with retry indication
                TranscriptSyncDialog(
                    attempt = 0,
                    errorMessage = syncStatus.message,
                    onCancel = onCancelSync
                )
            }
        }
        is SessionManager.SyncStatus.Success -> {
            // Optionally show success message briefly
            // For now, we'll just let it disappear
        }
        is SessionManager.SyncStatus.Idle -> {
            // Nothing to show
        }
    }
}

/**
 * Dialog shown during transcript synchronization
 * Blocks user from starting new conversations until sync completes
 */
@Composable
private fun TranscriptSyncDialog(
    attempt: Int,
    errorMessage: String? = null,
    onCancel: () -> Unit
) {
    var showCancelWarning by remember { mutableStateOf(false) }
    
    if (showCancelWarning) {
        // Show warning dialog before cancelling
        AlertDialog(
            onDismissRequest = { showCancelWarning = false },
            title = {
                Text(text = "Ostrzeżenie")
            },
            text = {
                Text(text = stringResource(R.string.transcript_sync_cancel_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelWarning = false
                        onCancel()
                    }
                ) {
                    Text(stringResource(R.string.transcript_sync_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelWarning = false }) {
                    Text(stringResource(R.string.transcript_sync_cancel_dismiss))
                }
            }
        )
    }
    
    Dialog(onDismissRequest = { /* Cannot dismiss during sync */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                
                // Status text
                if (attempt > 0) {
                    Text(
                        text = stringResource(R.string.transcript_sync_in_progress, attempt),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Error message if present
                if (errorMessage != null) {
                    Text(
                        text = stringResource(R.string.transcript_sync_error, errorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Blocking message
                Text(
                    text = stringResource(R.string.transcript_sync_blocking_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Cancel button
                OutlinedButton(
                    onClick = { showCancelWarning = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Anuluj")
                }
            }
        }
    }
}
