package com.clipsync.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val PROJECT_URL = "https://github.com/Tanishktewetia/ClipSync"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onAddTile: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val links = LocalUriHandler.current
    Scaffold(topBar = { TopAppBar(title = { Text("The ClipSync guide") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to connection") }
    }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Two devices. One flow.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Everything you need to connect, copy, and stay in control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            GuideSection("01", "Connect your devices", listOf(
                "Install and open ClipSync on your Windows PC and Android phone.",
                "Join the same Wi-Fi network, or connect your phone to the PC's hotspot. Internet access is not needed for syncing.",
                "On Windows, open ClipSync from the tray and choose Pair new device. On your phone, tap Find & pair my PC.",
                "Compare all six digits on both devices. Tap Codes match only if they match. Discovery does not bypass this security check."))
            GuideSection("02", "Copy on Windows. Paste on Android.", listOf(
                "Wait for Connected, then copy plain text on Windows as usual.",
                "Open a text field on your unlocked phone and paste. There is no Receive button to press.",
                "While your phone is locked, only the latest incoming text waits in memory. Unlock to apply it. Android may still show its own clipboard indicator; ClipSync marks incoming text sensitive to hide the preview."))
            GuideSection("03", "What is a Quick Settings tile?", listOf(
                "Tiles are the shortcut buttons next to Wi-Fi, Bluetooth, and the flashlight when you swipe down twice from the top of your phone.",
                "Tap Add Send to PC below and accept Android's prompt. If no prompt appears, swipe down twice → tap the pencil or Edit → find Send to PC → drag it into the active area → Done.",
                "Copy text in any app, open Quick Settings, and tap Send to PC. Unlock first if asked. This one tap gives ClipSync permission to read the clipboard.",
                "Prefer not to add a tile? Tap Send clipboard now in ClipSync's persistent notification instead. Copying on the phone alone never sends anything."))
            Button(onClick = onAddTile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Send to PC tile")
            }
            GuideSection("04", "Background, reconnect & privacy", listOf(
                "Closing this screen does not stop sharing. Keep the quiet notification enabled and allow the one-time battery exemption for more reliable background operation.",
                "After restarting the phone, unlock it once. Enabled sync resumes; a force-stop in Android Settings requires opening the app again.",
                "Discovery checks your PC hotspot, saved address, and local network. Recovery backs off up to 30 seconds; no need to re-pair after an IP change.",
                "Only the newest known text is kept in memory. If offline, tap Send to PC to hold that phone text for reconnect. Text is not retained after the process exits. Conflicts use a logical counter and device ID, not your wall clock.",
                "Turn off Apply latest text on reconnect in Advanced to preserve the phone clipboard on reconnect. Pause stops both directions; Stop background connection disables automatic recovery."))
            GuideSection("05", "Something not connecting?", listOf(
                "Keep both devices on the same private Wi-Fi. Guest networks, VPN routing, or client isolation can block local connections.",
                "Allow ClipSync through Windows Firewall on your private network. If discovery is blocked, enter the PC's Wi-Fi IPv4 address in Advanced. Windows hotspot usually uses 192.168.137.1.",
                "A certificate mismatch needs your attention. Never approve an unexpected pairing code. Pair again explicitly if you intentionally replaced a device.",
                "Open Advanced → Diagnostics → Share logs. Include the app versions and what action failed. Logs exclude clipboard text; do not paste secrets into an issue."))
            OutlinedButton(onClick = { links.openUri(PROJECT_URL) }, modifier = Modifier.fillMaxWidth()) { Text("Source code & documentation on GitHub") }
            TextButton(onClick = { links.openUri("$PROJECT_URL/issues") }, modifier = Modifier.fillMaxWidth()) { Text("Report an issue") }
            Text("Local-network text sync · No account · No cloud clipboard history", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable
private fun GuideSection(number: String, title: String, steps: List<String>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$number / $title", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            steps.forEachIndexed { index, text ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
