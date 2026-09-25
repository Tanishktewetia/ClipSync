package com.clipsync.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    lastCrash: String?,
    serviceRunning: Boolean,
    clipsRead: Int,
    lastClipLength: Int?,
    onStartReader: () -> Unit,
    onStopReader: () -> Unit,
    tileSupported: Boolean,
    onOpenTileSettings: () -> Unit,
    onManualRead: () -> Unit,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onSaveLogs: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var showCrashDialog by remember { mutableStateOf(lastCrash != null) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ClipSync", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("v0.2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(8.dp))
            ClipboardStatusCard(serviceRunning, clipsRead, lastClipLength, onStartReader, onStopReader)
            Spacer(Modifier.height(16.dp))
            TileSetupCard(tileSupported, onOpenTileSettings, onManualRead)
            Spacer(Modifier.height(20.dp))
            DiagnosticsSection(
                expanded = diagnosticsExpanded,
                onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                onCopyLogs = onCopyLogs,
                onShareLogs = onShareLogs,
                onSaveLogs = onSaveLogs,
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showCrashDialog && lastCrash != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("ClipSync crashed") },
            text = { Text("The app crashed in the previous session. You can share the crash report from Diagnostics.") },
            confirmButton = { TextButton(onClick = { showCrashDialog = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun ClipboardStatusCard(
    serviceRunning: Boolean,
    clipsRead: Int,
    lastClipLength: Int?,
    onStartReader: () -> Unit,
    onStopReader: () -> Unit,
) {
    val statusColor by animateColorAsState(
        targetValue = if (serviceRunning) MaterialTheme.colorScheme.secondary else Gray500,
        animationSpec = tween(300),
        label = "clipboardStatusColor",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(14.dp))
                Text(
                    if (serviceRunning) "Reader running" else "Reader stopped",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (serviceRunning) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (serviceRunning) "Copy text, then use the Send to PC Quick Settings tile."
                    else "Start the reader once; it can resume after a phone restart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text("Clipboard reads: $clipsRead", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (lastClipLength != null) Text("Last clip: $lastClipLength characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (serviceRunning) {
                OutlinedButton(onClick = onStopReader, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Stop, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Stop reader")
                }
            } else {
                Button(onClick = onStartReader, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Start reader")
                }
            }
        }
    }
}

@Composable
private fun TileSetupCard(
    tileSupported: Boolean,
    onOpenTileSettings: () -> Unit,
    onManualRead: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How to send a clipboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (tileSupported) {
                Text("""1. Tap Add Send to PC tile below.
2. In Quick Settings, tap Edit or the pencil.
3. Drag Send to PC into your active tiles and tap Done.
4. Copy text, then tap Send to PC.""", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenTileSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Send to PC tile")
                }
                Text("The quiet ClipSync notification only shows that the background reader is running; it is not the send button.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("This Android version has no Quick Settings tile support. Use the in-app button as the manual fallback.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onManualRead, modifier = Modifier.fillMaxWidth()) {
                    Text("Read clipboard now")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onSaveLogs: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagButton(Icons.Outlined.ContentCopy, "Copy", onCopyLogs, Modifier.weight(1f))
                    DiagButton(Icons.Outlined.Share, "Share", onShareLogs, Modifier.weight(1f))
                    DiagButton(Icons.Outlined.Download, "Save", onSaveLogs, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiagButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label)
    }
}


