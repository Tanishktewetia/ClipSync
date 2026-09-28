package com.clipsync.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clipsync.android.BuildConfig
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.service.SyncUiState
import com.clipsync.android.store.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: SyncUiState, lastCrash: String?, notifications: Boolean, battery: Boolean, tile: Boolean,
    onNotifications: () -> Unit, onBattery: () -> Unit, onTile: () -> Unit, onDismissTile: () -> Unit,
    onRedo: () -> Unit, onConnect: (String) -> Unit, onPair: (String, String?) -> Unit,
    onPause: (Boolean) -> Unit, onForget: (String) -> Unit, onRename: (String, String) -> Unit,
    onPairCode: (Long, String?) -> Unit, onCopyLogs: () -> Unit, onShareLogs: () -> Unit, onSaveLogs: () -> Unit,
    replayOnConnect: Boolean, onReplayChanged: (Boolean) -> Unit) {
    var help by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    if (help) { HelpScreen(onBack = { help = false }, onAddTile = onTile); return }
    if (about) { AboutScreen(onBack = { about = false }, onHelp = { about = false; help = true }); return }
    var settings by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf(false) }
    var forgetId by rememberSaveable { mutableStateOf<String?>(null) }
    var crash by rememberSaveable { mutableStateOf(lastCrash != null) }
    val active = state.devices.firstOrNull { it.isActive }
    BackHandler(enabled = settings) { settings = false }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (settings) "Settings" else "ClipSync", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { help = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Help", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = { settings = !settings }) {
                        Icon(if (settings) Icons.Outlined.Close else Icons.Outlined.Settings,
                            if (settings) "Back to devices" else "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (!settings) Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (state.connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.connected && active != null) "Connected · ${active.displayName}" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }, bottomBar = {
        if (!settings) Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("PC → phone automatically · Phone → PC with one tap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { help = true }, contentPadding = PaddingValues(0.dp)) { Text("How ClipSync works") }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (settings) {
                BrandHeader("Local clipboard sharing for Windows & Android")
                OutlinedButton(onClick = { help = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Help & troubleshooting") }
                OutlinedButton(onClick = { about = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("About ClipSync & GitHub") }
                OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Redo setup guide") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply latest text on reconnect", Modifier.weight(1f))
                    Switch(checked = replayOnConnect, onCheckedChange = onReplayChanged)
                }
                Diagnostics(onCopyLogs, onShareLogs, onSaveLogs)
                Text("Version ${BuildConfig.VERSION_NAME} · Text only · TLS 1.3", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { settings = false }) { Text("Back to devices") }
            } else {

                if (!notifications || !battery || !tile) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Finish your setup", style = MaterialTheme.typography.titleMedium)
                        if (!notifications) OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Icon(Icons.Outlined.NotificationsNone, null); Spacer(Modifier.width(8.dp)); Text("Enable notifications") }
                        if (!battery) OutlinedButton(onClick = onBattery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Icon(Icons.Outlined.BatterySaver, null); Spacer(Modifier.width(8.dp)); Text("Allow background battery use") }
                        if (!tile) { OutlinedButton(onClick = { guide = !guide }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Icon(Icons.Outlined.GridView, null); Spacer(Modifier.width(8.dp)); Text("Send to PC tile setup") }
                            TextButton(onClick = onDismissTile) { Text("Dismiss tile reminder", textDecoration = TextDecoration.Underline) } }
                        AnimatedVisibility(guide && !tile) { TileGuide(onTile) }
                    }
                }
                if (state.devices.isNotEmpty()) Text("Your PCs", style = MaterialTheme.typography.titleMedium)
                DevicePolicy.ordered(state.devices).forEach { device -> key(device.id) {
                    DeviceTile(device, active, state.connecting, state.networkLabel, onConnect, onPair, onPause, { forgetId = device.id }, onRename)
                } }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                var sendNote by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(state.sendFeedback) { sendNote = state.sendFeedback; kotlinx.coroutines.delay(5000); sendNote = null }
                sendNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                AddDevice(state, onPair, onPairCode)

            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
    forgetId?.let { id -> state.devices.firstOrNull { it.id == id }?.let { device ->
        AlertDialog(onDismissRequest = { forgetId = null }, title = { Text("Forget ${device.displayName}?") },
            text = { Text("You'll need to pair again to reconnect.") },
            confirmButton = { TextButton(onClick = { onForget(id); forgetId = null }) { Text("Forget", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { forgetId = null }) { Text("Cancel") } })
    } }
    if (crash) AlertDialog(onDismissRequest = { crash = false }, title = { Text("Previous session ended unexpectedly") },
        text = { Text("Share the Diagnostics logs from Settings so we can investigate.") },
        confirmButton = { TextButton(onClick = { crash = false; settings = true }) { Text("Open Diagnostics") } })
}

@Composable
private fun DeviceTile(device: PairedDevice, active: PairedDevice?, connecting: Boolean, network: String,
    connect: (String) -> Unit, pair: (String, String?) -> Unit, pause: (Boolean) -> Unit, forget: () -> Unit, rename: (String, String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(device.displayName) { mutableStateOf(device.displayName) }
    val dark = isSystemInDarkTheme()
    val color = when (device.connectionState) {
        DeviceState.Connected -> if (dark) Color(0xFF6EE7B7) else Color(0xFF047857)
        DeviceState.Unreachable -> MaterialTheme.colorScheme.error
        DeviceState.IdentityChanged -> if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
        DeviceState.Connecting, DeviceState.Paused -> if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val status = when (device.connectionState) { DeviceState.IdentityChanged -> "Identity changed"; DeviceState.Connecting -> "Connecting…"; else -> device.connectionState.name }
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        // Only the header toggles expansion. Actions below have independent hit targets.
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded; confirmation = null }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                Text(device.lastConnectedAt?.let { relativeTime(it) } ?: status,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(status, color = color, style = MaterialTheme.typography.labelMedium)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Text("IP: ${device.lastKnownAddress}", style = MaterialTheme.typography.bodyMedium)
                Text("Host: ${device.hostLabel}", style = MaterialTheme.typography.bodyMedium)
                Text("Network: $network", style = MaterialTheme.typography.bodyMedium)
                device.connectedSince?.let { Text("Connected since: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) }
                if (device.connectionState == DeviceState.IdentityChanged) Text("This address now presents a different certificate. Pair again only after comparing the code on your PC.", color = color)
                if (editing) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { if (name.isNotBlank()) { rename(device.id, name.trim()); editing = false } }) { Text("Save name") }
                } else TextButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Rename") }
                if (confirmation != null && active?.id == confirmation && !device.isActive) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("This will disconnect ${active?.displayName}. Continue?")
                            Row { TextButton(onClick = { connect(device.id); confirmation = null }, enabled = !connecting) { Text("Continue") }
                                TextButton(onClick = { confirmation = null }) { Text("Cancel") } }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (device.connectionState) {
                        DeviceState.Connected, DeviceState.Paused -> Button(shape = MaterialTheme.shapes.medium, onClick = { pause(device.connectionState != DeviceState.Paused) }) {
                            Icon(if (device.connectionState == DeviceState.Paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null)
                            Text(if (device.connectionState == DeviceState.Paused) "Resume" else "Pause")
                        }
                        DeviceState.Connecting -> Text("Verifying connection…", Modifier.weight(1f).padding(vertical = 12.dp))
                        DeviceState.IdentityChanged -> Button(shape = MaterialTheme.shapes.medium, onClick = { pair(device.lastKnownAddress, device.id) }, enabled = !connecting) { Text("Pair again") }
                        else -> Button(shape = MaterialTheme.shapes.medium, onClick = {
                            if (active != null && active.id != device.id) confirmation = active.id else connect(device.id)
                        }, enabled = !connecting) { Text("Connect") }
                    }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = forget) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Forget") }
                }
            }
        }
    }
}

@Composable
private fun AddDevice(state: SyncUiState, pair: (String, String?) -> Unit, answer: (Long, String?) -> Unit) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    val prompt = state.pairing
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state.devices.isEmpty()) "Connect your first PC" else "Add a PC", style = MaterialTheme.typography.headlineSmall)
            Text("On Windows, open ClipSync and choose Pair new device.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (prompt != null) {
            var code by rememberSaveable(prompt.id) { mutableStateOf("") }
            OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(prompt.name, style = MaterialTheme.typography.titleMedium)
                    Text("Do these codes match?", style = MaterialTheme.typography.bodyLarge)
                    Text(prompt.code, style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "Pairing code " + prompt.code.toCharArray().joinToString(" ") })
                    Text("Only confirm if your PC shows the same six digits.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                        label = { Text("PC code (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = prompt.error != null, modifier = Modifier.fillMaxWidth(), enabled = !prompt.busy)
                    prompt.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (state.waitingPairs > 0) Text("Another PC is waiting to pair", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { answer(prompt.id, code.ifBlank { prompt.code }) },
                        enabled = !prompt.busy && (code.isEmpty() || code.length == 6),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Confirm pairing") }
                    OutlinedButton(onClick = { answer(prompt.id, null) }, enabled = !prompt.busy,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
                }
            }
        } else {
            if (!manual) {
                Button(onClick = { pair("", null) }, enabled = !state.searching, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(10.dp)); Text(if (state.searching) "Searching…" else "Find a PC")
                }
                OutlinedButton(onClick = { manual = true }, enabled = !state.searching, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Outlined.Keyboard, null); Spacer(Modifier.width(10.dp)); Text("Enter IP address instead")
                }
            } else {
                OutlinedTextField(address, { address = it.take(21) }, label = { Text("PC IP address") },
                    placeholder = { Text("192.168.1.20") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !state.searching)
                Button(onClick = { pair(address, null) }, enabled = address.isNotBlank() && !state.searching,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (state.searching) "Connecting…" else "Pair with this PC")
                }
                OutlinedButton(onClick = { manual = false }, enabled = !state.searching,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use automatic search") }
            }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.pairingMessage?.let { message ->
            Surface(color = if (state.pairingFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (state.pairingFailed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.pairingFailed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null)
                        Text(if (state.pairingFailed) "Pairing needs attention" else "Pairing complete", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { com.clipsync.android.service.SyncRuntime.clearPairingFeedback() }) { Text("Dismiss") }
                    if (state.pairingFailed && prompt == null && !state.searching) OutlinedButton(
                        onClick = { pair(if (manual) address else "", null) }, enabled = !manual || address.isNotBlank(),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun Diagnostics(copy: () -> Unit, share: () -> Unit, save: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var logs by remember { mutableStateOf("") }
    Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
    Text("Logs contain status and clip size/hash only, never copied text.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = copy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) { Text("Copy logs") }
        OutlinedButton(onClick = share, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) { Text("Share logs") }
    }
    TextButton(onClick = save) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Save to Downloads") }
    TextButton(onClick = { expanded = !expanded; if (expanded) logs = FileLogger.getRecentLogs(60).takeLast(12000) }) {
        Text(if (expanded) "Hide recent logs" else "View recent logs")
    }
    if (expanded) SelectionContainer {
        Text(logs, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp))
    }
}

private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60000)
    return when { minutes < 1 -> "Just now"; minutes < 60 -> "$minutes min ago"; minutes < 1440 -> "${minutes / 60} h ago"; else -> "${minutes / 1440} d ago" }
}
