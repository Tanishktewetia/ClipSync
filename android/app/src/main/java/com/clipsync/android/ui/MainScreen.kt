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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Send
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipsync.android.ui.theme.*

/**
 * Main screen composable. Shows a hero status card and a Diagnostics section.
 * Phase 0: placeholder status (no real sync logic wired yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    lastCrash: String?,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onSaveLogs: () -> Unit,
    onConnectionTest: suspend (String, Int, String) -> String,
) {
    val scrollState = rememberScrollState()
    var showCrashDialog by remember { mutableStateOf(lastCrash != null) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("192.168.137.1") }
    var port by remember { mutableStateOf("48653") }
    var message by remember { mutableStateOf("hello") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ClipSync",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v0.1.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero Status Card
            StatusCard()

            Spacer(modifier = Modifier.height(20.dp))

            ConnectionTestCard(host, { host = it }, port, { port = it }, message, { message = it }, busy, result) {
                busy = true
                result = null
                scope.launch {
                    val started = System.nanoTime()
                    result = try { "${onConnectionTest(host, port.toInt(), message)} (${(System.nanoTime() - started) / 1_000_000} ms)" } catch (e: Exception) { "Failed: ${e.message}" }
                    busy = false
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Diagnostics Section
            DiagnosticsSection(
                expanded = diagnosticsExpanded,
                onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                onCopyLogs = onCopyLogs,
                onShareLogs = onShareLogs,
                onSaveLogs = onSaveLogs,
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Crash dialog
    if (showCrashDialog && lastCrash != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("ClipSync crashed") },
            text = {
                Text(
                    text = "The app crashed in the previous session. You can share the crash report from Diagnostics.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun StatusCard() {
    // Phase 0: static "Waiting" state
    val statusColor by animateColorAsState(
        targetValue = Gray500,
        animationSpec = tween(300),
        label = "statusColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                // Pulsing dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Waiting",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // Status detail
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No device connected. Pair a device to start syncing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DiagnosticsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onSaveLogs: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            // Header / toggle
            TextButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiagButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy",
                        onClick = onCopyLogs,
                        modifier = Modifier.weight(1f),
                    )
                    DiagButton(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        onClick = onShareLogs,
                        modifier = Modifier.weight(1f),
                    )
                    DiagButton(
                        icon = Icons.Outlined.Download,
                        label = "Save",
                        onClick = onSaveLogs,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ConnectionTestCard(host: String, onHost: (String) -> Unit, port: String, onPort: (String) -> Unit, message: String, onMessage: (String) -> Unit, busy: Boolean, result: String?, onSend: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connection test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Temporary Phase 1 Wi-Fi/TLS echo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(host, onHost, Modifier.fillMaxWidth(), label = { Text("PC IP address") }, singleLine = true)
            OutlinedTextField(port, onPort, Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true)
            OutlinedTextField(message, onMessage, Modifier.fillMaxWidth(), label = { Text("Message") }, singleLine = true)
            Button(onClick = onSend, enabled = !busy && host.isNotBlank() && port.toIntOrNull() != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Send, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Sending…" else "Send")
            }
            if (result != null) Text(result, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


