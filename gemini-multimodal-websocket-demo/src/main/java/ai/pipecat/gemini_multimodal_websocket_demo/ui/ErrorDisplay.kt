package ai.pipecat.gemini_multimodal_websocket_demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError

@Composable
fun ErrorDisplay(
    error: LibreChatError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Błąd",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = getErrorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onDismiss != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Zamknij")
                    }
                }
                
                if (onRetry != null && isRetryable(error)) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Spróbuj ponownie")
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(
    error: LibreChatError,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Błąd")
        },
        text = {
            Text(getErrorMessage(error))
        },
        confirmButton = {
            if (onRetry != null && isRetryable(error)) {
                Button(onClick = onRetry) {
                    Text("Spróbuj ponownie")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij")
            }
        }
    )
}

@Composable
private fun getErrorMessage(error: LibreChatError): String {
    return when (error) {
        is LibreChatError.NetworkError -> 
            "Brak połączenia z internetem. Sprawdź połączenie i spróbuj ponownie."
        is LibreChatError.AuthenticationError -> 
            "Błąd logowania. Sprawdź dane dostępu (email i hasło)."
        is LibreChatError.ServerError -> 
            "Problem z serwerem LibreChat (kod: ${error.code}). Spróbuj później."
        is LibreChatError.TokenExpired -> 
            "Sesja wygasła. Zaloguj się ponownie."
        is LibreChatError.ParseError -> 
            "Błąd przetwarzania danych. Skontaktuj się z pomocą techniczną."
    }
}

private fun isRetryable(error: LibreChatError): Boolean {
    return when (error) {
        is LibreChatError.NetworkError -> true
        is LibreChatError.ServerError -> error.code >= 500
        is LibreChatError.TokenExpired -> false
        is LibreChatError.AuthenticationError -> false
        is LibreChatError.ParseError -> false
    }
}
