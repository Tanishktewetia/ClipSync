package com.clipsync.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(step: Int, notifications: Boolean, battery: Boolean, tile: Boolean,
    advance: (Int) -> Unit, finish: () -> Unit, requestNotifications: () -> Unit, requestBattery: () -> Unit, addTile: () -> Unit) {
    val page = step.coerceIn(0, 3)
    val titles = listOf("Your clipboard. On both devices.", "Send without opening ClipSync", "Keep the connection ready", "A shortcut beside your Wi-Fi")
    val descriptions = listOf(
        "ClipSync shares plain text between your Windows PC and Android phone over the same Wi-Fi. No account or cloud clipboard needed.",
        "The quiet ClipSync notification shows your connection and includes Send clipboard now. One tap sends text you copied on your phone to your active PC.",
        "Android can limit apps when the screen is off. Allowing background battery use helps ClipSync keep receiving PC copies and recover its connection.",
        "Send to PC is a Quick Settings tile. Add it once, then copy text in any app and tap the tile to send it to your active PC.")
    val complete = when(page) { 1 -> notifications; 2 -> battery; 3 -> tile; else -> false }
    BackHandler(enabled = page > 0) { advance(page - 1) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { if (page == 3) finish() else advance(page + 1) }, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (page == 3) "Open dashboard" else "Continue") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (page > 0) TextButton(onClick = { advance(page - 1) }) { Text("Back") } else Spacer(Modifier.width(1.dp))
                        if (page > 0 && !complete) TextButton(onClick = { if (page == 3) finish() else advance(page + 1) }) { Text("Skip for now") }
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader("A shared clipboard, on your terms")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Set up ClipSync", style = MaterialTheme.typography.titleSmall)
                    Text("${page + 1} of 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { (page + 1) / 4f }, modifier = Modifier.fillMaxWidth())
                SetupIllustration(page)
                Text(titles[page], style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(descriptions[page], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                when(page) {
                    0 -> {
                        SetupExplanation("PC → phone", "Automatic. Copy on Windows, then paste on your phone.")
                        SetupExplanation("Phone → PC", "Copy text, then tap the notification action or Send to PC tile.")
                        Text("Pair once by comparing a six-digit code. You decide which PC is active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1, 2 -> {
                        if (complete) SetupComplete(if(page == 1) "Notifications are enabled" else "Background battery use is allowed")
                        else Button(onClick = if(page == 1) requestNotifications else requestBattery,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                            Text(if(page == 1) "Allow notifications" else "Allow background battery use")
                        }
                        Text(if(page == 1) "Optional: PC-to-phone copies still work without notification permission. You can enable it later in Settings."
                            else "Optional: skipping may make background connections less reliable. You can change this later in Android Settings.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    3 -> {
                        TileInstructions()
                        if(tile) SetupComplete("Send to PC tile is added")
                        else Button(onClick = addTile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("Add Send to PC tile") }
                        Text("If Android cannot open the add-tile prompt, follow the steps above. You can skip this and use the notification instead.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun BrandHeader(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.padding(12.dp).size(28.dp))
        }
        Column { Text("ClipSync", style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
private fun SetupExplanation(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
private fun SetupComplete(label: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CheckCircle, null); Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
@Composable
private fun SetupIllustration(page: Int) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when(page) {
                0 -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        Icon(Icons.Outlined.LaptopWindows, "Windows PC", Modifier.size(56.dp))
                        Icon(Icons.Outlined.Sync, "Clipboard shared over Wi-Fi", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Outlined.PhoneAndroid, "Android phone", Modifier.size(48.dp))
                    }
                    Text("One local connection. Two places to paste.", style = MaterialTheme.typography.bodyMedium)
                }
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsNone, null); Text("ClipSync", style = MaterialTheme.typography.titleSmall)
                    }
                    Text("Connected to your PC", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.AutoMirrored.Outlined.Send, null); Text("Send clipboard now", color = MaterialTheme.colorScheme.primary) }
                }
                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(52.dp))
                        Column { Text("Screen off", style = MaterialTheme.typography.titleSmall); Text("Connection stays ready", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Text("Receive PC copies while ClipSync is in the background.", style = MaterialTheme.typography.bodyMedium)
                }
                3 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Icon(Icons.Outlined.Wifi, "Wi-Fi"); Icon(Icons.Outlined.Bluetooth, "Bluetooth"); Icon(Icons.Outlined.Edit, "Edit tiles") }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.AutoMirrored.Outlined.Send, null); Text("Send to PC", style = MaterialTheme.typography.titleSmall) }
                }
            }
        }
    }
}
@Composable
private fun TileInstructions() {
    listOf("Swipe down twice to open Quick Settings.", "Tap the pencil or Edit button.", "Drag Send to PC into the active tiles.", "Tap Done or Back to save.").forEachIndexed { index, instruction ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(instruction, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable
fun TileGuide(addTile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add a shortcut next to Wi-Fi", style = MaterialTheme.typography.titleMedium)
        SetupIllustration(3)
        TileInstructions()
        Button(onClick = addTile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("Add Send to PC tile") }
    }
}
