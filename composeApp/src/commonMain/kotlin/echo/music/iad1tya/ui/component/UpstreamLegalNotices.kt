package echo.music.iad1tya.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import echomusic.composeapp.generated.resources.Res
import echomusic.composeapp.generated.resources.copyright
import echomusic.composeapp.generated.resources.upstream_credit_app
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpstreamLegalNotices() {
    val uriHandler = LocalUriHandler.current
    var showLicense by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Text("Upstream attribution", style = MaterialTheme.typography.titleMedium)
        Text("Rhytma — Dhruv's Modified App. Modifications do not transfer ownership of the original work.")
        Text("Based on EchoMusic by Aditya and SimpMusic by maxrave-dev and contributors. Original credits:")
        Text(stringResource(Res.string.copyright))
        Text(stringResource(Res.string.upstream_credit_app))
        Text("Licensed under the GNU General Public License v3. This software comes without warranty. You may redistribute it under the license terms. Dependency notices and licenses follow below.")
        TextButton(onClick = { showLicense = true }) { Text("Read GNU GPL v3 license") }
        TextButton(onClick = { uriHandler.openUri("https://github.com/EchoMusicApp/Echo-Music") }) {
            Text("EchoMusic upstream source")
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/maxrave-dev/SimpMusic") }) {
            Text("SimpMusic upstream source")
        }
    }
    if (showLicense) {
        val license by produceState("") {
            value = Res.readBytes("files/upstream-LICENSE.txt").decodeToString()
        }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("GNU General Public License v3") },
            text = {
                Text(license, Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("Close") }
            },
        )
    }
}
