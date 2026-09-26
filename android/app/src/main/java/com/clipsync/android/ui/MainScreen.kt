package com.clipsync.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipsync.android.BuildConfig
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.security.ManualAddress
import com.clipsync.android.service.PairingPrompt
import com.clipsync.android.service.SyncUiState
import com.clipsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: SyncUiState, lastCrash: String?,
    onConnect: (String, Boolean) -> Unit, onPause: (Boolean) -> Unit, onStop: () -> Unit, onAddTile: () -> Unit,
    onPairingAnswer: (Long, Boolean) -> Unit,
    onCopyLogs: () -> Unit, onShareLogs: () -> Unit, onSaveLogs: () -> Unit,
    onBatterySettings: () -> Unit,
    replayOnConnect: Boolean, onReplayChanged: (Boolean) -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    if (help) { HelpScreen(onBack = { help = false }, onAddTile = onAddTile); return }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable(state.address) { mutableStateOf(state.address) }
    var crashVisible by rememberSaveable { mutableStateOf(lastCrash != null) }
    val addressError = remember(address) { runCatching { if (address.isBlank()) "" else ManualAddress.parse(address) }.exceptionOrNull()?.message }
    Scaffold(topBar = {
        TopAppBar(title = { Text("ClipSync", fontWeight = FontWeight.SemiBold) },
            actions = { TextButton(onClick = { help = true }) { Icon(Icons.Outlined.HelpOutline, null); Spacer(Modifier.width(6.dp)); Text("Guide") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("YOUR DEVICES, IN SYNC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            StatusCard(state)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().toggleable(value = state.paused, role = Role.Switch, onValueChange = onPause)
                    .padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Pause syncing", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.paused) "Clipboard updates are paused in both directions." else "Keep the connection, pause clipboard updates.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.paused, onCheckedChange = null)
                }
            }
            if (!advanced && !state.connected && !state.connecting) {
                Button(onClick = {
                    onConnect(address, !state.paired)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (state.paired) "Find my PC" else "Find & pair my PC")
                }
            }
            if (!state.paired) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your first connection", style = MaterialTheme.typography.titleLarge)
                    Text("1. Join the same Wi-Fi or your PC hotspot.\n2. On Windows, choose Pair new device.\n3. Tap Find & pair my PC, then compare the codes.")
                    Text("No IP address needed. Your PC is discovered nearby, then verified securely.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp)); Text("Copy here. Use it there.", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("PC → phone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Automatic. Copy on Windows, then paste on your unlocked phone.")
                    HorizontalDivider()
                    Text("Phone → PC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Copy text, then tap Send clipboard now in the notification. Or add a shortcut beside Wi-Fi and Bluetooth in Quick Settings.")
                    OutlinedButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Send to PC shortcut")
                    }
                    TextButton(onClick = { help = true }, modifier = Modifier.fillMaxWidth()) { Text("What is a tile? Show me how →") }
                }
            }
            Text("Made to stay out of your way", style = MaterialTheme.typography.titleMedium)
            Text("Close this screen; sync stays on. Locked? The latest PC text waits in memory until you unlock.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.sendFeedback?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp)) {
                    TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Outlined.Tune, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Advanced", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (advanced) "Collapse Advanced" else "Expand Advanced")
                    }
                    AnimatedVisibility(visible = advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider()
                            Text("Connection", style = MaterialTheme.typography.titleMedium)
                            Text("Nearby PCs are found automatically. If your network blocks discovery, enter a Wi-Fi IPv4 address as a fallback.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(value = address, onValueChange = { if (it.length <= 15) address = it },
                                label = { Text("Manual PC IP (optional)") }, placeholder = { Text("192.168.137.1") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.connecting,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = address.isNotBlank() && addressError != null,
                                supportingText = { Text(if (address.isNotBlank() && addressError != null) addressError else "Leave empty for automatic discovery.") })
                            if (!state.connecting) {
                                if (!state.connected) Button(onClick = { onConnect(address, !state.paired) }, enabled = addressError == null,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(if (state.paired) "Connect" else "Pair with PC")
                                }
                                if (!state.paired) Text("First choose Pair new device in the Windows app. Then compare the six-digit codes on both screens.",
                                    style = MaterialTheme.typography.bodySmall)
                                if (state.paired) TextButton(onClick = { onConnect(address, true) }, enabled = addressError == null) {
                                    Text("Pair again with this PC")
                                }
                            }
                            if (state.running) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.connecting) "Cancel connection" else "Stop background connection")
                            }
                            Row(Modifier.fillMaxWidth().toggleable(value = replayOnConnect, role = Role.Switch, onValueChange = onReplayChanged), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Apply latest text on reconnect", style = MaterialTheme.typography.titleMedium)
                                    Text("Turn off to keep your phone clipboard unchanged when connecting.", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = replayOnConnect, onCheckedChange = null)
                            }
                            Text("Manual send shortcut", style = MaterialTheme.typography.titleMedium)
                            Text("Optional: add Send to PC to Quick Settings. Swipe down twice → Edit/pencil → drag Send to PC into your active tiles. Copying alone never sends anything.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onAddTile) { Text("Add Send to PC tile") }
                            TextButton(onClick = onBatterySettings) { Text("Battery optimization settings") }
                            HorizontalDivider()
                            Diagnostics(onCopyLogs, onShareLogs, onSaveLogs)
                            Text("Version ${BuildConfig.VERSION_NAME} · Text only · TLS 1.3", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { links.openUri(PROJECT_URL) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("GitHub · Source & documentation") }
            Text("ClipSync ${BuildConfig.VERSION_NAME} · Local by design", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
        }
    }
    state.pairing?.let { PairingDialog(it, onPairingAnswer) }
    if (crashVisible) AlertDialog(onDismissRequest = { crashVisible = false }, title = { Text("Previous session ended unexpectedly") },
        text = { Text("Open Advanced → Diagnostics and share the logs so we can investigate.") },
        confirmButton = { TextButton(onClick = { crashVisible = false; advanced = true }) { Text("Open Diagnostics") } })
}

@Composable
private fun StatusCard(state: SyncUiState) {
    val dark = isSystemInDarkTheme()
    val accent by animateColorAsState(when (state.status) {
        "Connected" -> if (dark) Green400 else Color(0xFF047857)
        "Paused" -> if (dark) Violet400 else Color(0xFF6D28D9)
        "Error" -> if (dark) Red400 else Color(0xFFB91C1C)
        else -> if (dark) Amber400 else Color(0xFF92400E)
    }, tween(250), label = "connection status")
    val icon = when (state.status) {
        "Connected" -> Icons.Outlined.CheckCircle
        "Paused" -> Icons.Outlined.PauseCircle
        "Error" -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Schedule
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = if (dark) .16f else .08f)),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = accent.copy(alpha = .12f), shape = CircleShape) {
                if (state.connecting) CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(28.dp), color = accent, strokeWidth = 3.dp)
                else Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Text(state.status, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = accent)
            Text(state.error ?: when {
                state.paused -> "Your phone clipboard stays as it is. Resume when you're ready."
                state.pendingUnlock -> "The latest PC clipboard is waiting for your phone to unlock."
                state.connected -> "PC copies arrive automatically. Send phone copies with one notification or tile tap."
                state.pairing != null -> "Compare the code with your Windows app."
                state.connecting -> "Finding your PC and checking its secure identity…"
                state.paired -> "Your PC is paired. Connect when both devices are on the same Wi-Fi."
                else -> "Pair your PC once to start receiving clipboard text."
            }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (state.connecting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
            if (state.connected) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Encrypted · ${state.received} received · ${state.sent} sent", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PairingDialog(prompt: PairingPrompt, answer: (Long, Boolean) -> Unit) {
    AlertDialog(onDismissRequest = { answer(prompt.id, false) },
        icon = { Icon(Icons.Outlined.PhonelinkLock, contentDescription = null) },
        title = { Text("Do the codes match?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Compare these digits with the code shown in ClipSync on your PC. Only confirm if all six match.")
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Pairing code " + prompt.code.toCharArray().joinToString(" ") },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    prompt.code.forEach { digit ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                                Text(digit.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                Text("Your clipboard is not changed until you confirm. The PC's pairing window lasts two minutes.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { answer(prompt.id, true) }) { Text("Codes match · Pair") } },
        dismissButton = { TextButton(onClick = { answer(prompt.id, false) }) { Text("Cancel") } })
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
