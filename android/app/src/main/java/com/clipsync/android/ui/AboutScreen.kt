package com.clipsync.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.clipsync.android.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onHelp: () -> Unit) {
    BackHandler(onBack = onBack)
    val links = LocalUriHandler.current
    Scaffold(topBar = { TopAppBar(title = { Text("About ClipSync") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Settings") }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BrandHeader("Your clipboard, between your devices")
            Text("Made for your PC and your phone", style = MaterialTheme.typography.headlineMedium)
            Text("ClipSync shares plain text over your local Wi-Fi. Windows copies arrive automatically on Android. To send phone text back, tap Send clipboard now in the notification or the Send to PC tile.", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text("Private by design", style = MaterialTheme.typography.titleMedium)
            Text("Connections use TLS and saved certificate identities. Pair by comparing a code, choose one active PC, and pause whenever you want. Clipboard text is kept in memory, not saved as a history or written into app logs.")
            Text("Version ${BuildConfig.VERSION_NAME} · Text sync", style = MaterialTheme.typography.bodyMedium)
            Text("Project: Tanishktewetia / ClipSync", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onHelp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Read the ClipSync guide") }
            OutlinedButton(onClick = { links.openUri(PROJECT_URL) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("GitHub · Source & documentation") }
            OutlinedButton(onClick = { links.openUri("$PROJECT_URL/issues") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Report an issue on GitHub") }
            Text("Need support? Settings → Diagnostics lets you share connection logs. Remove personal details before posting a public issue.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
