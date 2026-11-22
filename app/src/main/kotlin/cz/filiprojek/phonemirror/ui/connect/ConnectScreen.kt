package cz.filiprojek.phonemirror.ui.connect

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.filiprojek.phonemirror.DeviceIdentity
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.discovery.NsdBrowser

@Composable
fun ConnectScreen(onConnect: (ConnectTarget) -> Unit, onScan: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val browser = remember { NsdBrowser(context.applicationContext) }
    val ownId = remember { DeviceIdentity(context).id }
    DisposableEffect(browser) {
        browser.start()
        onDispose { browser.stop() }
    }
    val found by browser.cameras.collectAsState()
    val cameras = found.values.filter { it.deviceId != ownId }.sortedBy { it.name }
    var manual by rememberSaveable { mutableStateOf("") }
    val manualTarget = ConnectTarget.parseManual(manual)
    BackHandler(onBack = onBack)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Connect to a camera", style = MaterialTheme.typography.titleLarge)
            }
            Text("Cameras on this WiFi", style = MaterialTheme.typography.titleMedium)
            if (cameras.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Searching… start “Use as camera” on the other phone.")
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cameras, key = { "${it.host}:${it.port}" }) { target ->
                    ElevatedCard(onClick = { onConnect(target) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhotoCamera, null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(target.name ?: target.host, style = MaterialTheme.typography.titleMedium)
                                Text("${target.host}:${target.port}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR code")
            }
            Text("Or enter the address shown on the camera phone", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("IP address, e.g. 192.168.1.20") },
                singleLine = true,
                isError = manual.isNotBlank() && manualTarget == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { manualTarget?.let(onConnect) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { manualTarget?.let(onConnect) }, enabled = manualTarget != null, modifier = Modifier.fillMaxWidth()) {
                Text("Connect")
            }
        }
    }
}
