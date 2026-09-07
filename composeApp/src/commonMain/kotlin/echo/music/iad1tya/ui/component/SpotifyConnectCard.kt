package echo.music.iad1tya.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import echo.music.iad1tya.spotify.SpotifyOAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun SpotifyConnectCard(enabled: Boolean, oauth: SpotifyOAuthManager = koinInject()) {
    val connection by oauth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showSetup by remember { mutableStateOf(false) }
    var clientId by remember(connection.clientId) { mutableStateOf(connection.clientId) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text(if (connection.connected) "Spotify connected" else "Connect Spotify to import playlists")
        Text("Spotify permits importing playlists you own or collaborate on.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { error = null; showSetup = true }, enabled = enabled && !busy) {
            Text(if (connection.connected) "Reconnect Spotify" else "Connect Spotify")
        }
        if (connection.connected || connection.awaitingBrowser) {
            TextButton(enabled = enabled && !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) {
                            if (connection.awaitingBrowser) oauth.cancelAuthorization() else oauth.disconnect()
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = "Unable to update Spotify connection. Please try again." }
                    finally { busy = false }
                }
            }) { Text(if (connection.awaitingBrowser) "Cancel connection" else "Disconnect Spotify") }
        }
        if (connection.awaitingBrowser) Text("Finish signing in in your browser, then return to Rhytma.")
        connection.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (showSetup) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSetup = false },
            title = { Text("Connect Spotify") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Use the public Client ID from your Spotify Developer Dashboard app. No client secret is needed.")
                    OutlinedTextField(clientId, { clientId = it }, label = { Text("Spotify Client ID") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Text("Register this exact redirect URI in that app's settings:")
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(oauth.redirectUri) }
                    Text("Sign-in opens Spotify in your browser. Your password is never entered into Rhytma.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && clientId.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val url = withContext(Dispatchers.IO) { oauth.beginAuthorization(clientId) }
                            uriHandler.openUri(url)
                            showSetup = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Could not open Spotify sign-in. Please try again." }
                        finally { busy = false }
                    }
                }) { Text("Continue to Spotify") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showSetup = false }) { Text("Cancel") } },
        )
    }
}
