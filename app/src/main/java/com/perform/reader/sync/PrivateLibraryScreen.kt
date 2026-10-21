package com.perform.reader.sync

import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.perform.sync.PairingInvite
import com.perform.sync.Protocol
import kotlinx.coroutines.launch

/** Settings → Private library: the libraries this phone is paired with, full sync for each, downloads. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrivateLibraryScreen(sync: LibrarySync, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val connections by sync.settings.connections.collectAsState(emptyList())
    val statuses by sync.statuses.collectAsState()
    val lastSyncs by sync.settings.lastSyncs.collectAsState(emptyMap())
    val keepAllProgress by sync.keepAllProgress.collectAsState()
    val shareLocal by sync.settings.shareLocal.collectAsState(initial = true)
    val uploadTo by sync.settings.uploadTo.collectAsState(initial = null)
    val status by sync.status.collectAsState()
    val used by sync.storageUsed.collectAsState(0L)
    val limit by sync.settings.storageLimit.collectAsState(SyncSettings.DEFAULT_STORAGE_LIMIT)

    var busy by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var unpairing by remember { mutableStateOf<Connection?>(null) }

    fun paired(result: Result<String>) {
        busy = false
        scope.launch {
            result.onSuccess { name ->
                snackbar.showSnackbar("Paired with $name")
                sync.sync()
            }.onFailure { snackbar.showSnackbar(it.friendlyMessage()) }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val invite = barcode.rawValue?.let(PairingInvite::decode)
                if (invite == null) {
                    scope.launch { snackbar.showSnackbar("That isn't a Perform Library code.") }
                } else {
                    busy = true
                    scope.launch { paired(sync.pair(invite)) }
                }
            }
            .addOnFailureListener { scope.launch { snackbar.showSnackbar("The scanner couldn't start. Enter the code instead.") } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Private library") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (connections.isEmpty()) {
                Text(
                    "Read the books on your computers from anywhere. Run Perform Library on a PC or Mac, " +
                        "choose Pair a phone, then scan its code here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (connections.isNotEmpty()) {
                Group(if (connections.size == 1) "Library" else "Libraries") {
                    connections.forEach { c ->
                        LibraryCard(
                            connection = c,
                            status = statuses[c.serverId],
                            lastSync = lastSyncs[c.serverId],
                            progress = keepAllProgress[c.serverId],
                            onSync = { scope.launch { sync.sync() } },
                            syncing = status == SyncStatus.Syncing,
                            onKeepAll = { on ->
                                scope.launch {
                                    sync.settings.setKeepAll(c.serverId, on)
                                    if (on) LibraryWorker.runSoon(context)
                                }
                            },
                            onUnpair = { unpairing = c },
                        )
                    }
                }
            }

            // Pairing (another) library.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        Text("Pairing…")
                    }
                } else {
                    if (connections.isEmpty()) {
                        Button(onClick = ::scan) { Text("Scan the computer's code") }
                    } else {
                        OutlinedButton(onClick = ::scan) { Text("Pair another computer") }
                    }
                    TextButton(onClick = { manual = true }) { Text("Type the code instead") }
                }
                Hint(
                    "Books stay on your computers and download when you open them. A book on two of them shows once. " +
                        "Away from home, turn on Tailscale on the computer and this phone.",
                )
            }

            if (connections.isNotEmpty()) {
                Group("This phone's books") {
                    Toggle(
                        title = "Add this phone's books to a library",
                        detail = "Books in your folder here and books you add go into it, on the shelf they're on. Removing one here never removes it there.",
                        checked = shareLocal,
                    ) { on ->
                        scope.launch {
                            sync.settings.setShareLocal(on)
                            if (on) LibraryWorker.runSoon(context)
                        }
                    }
                    if (shareLocal && connections.size > 1) {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            connections.forEach { c ->
                                FilterChip(
                                    selected = uploadTo == c.serverId,
                                    onClick = { scope.launch { sync.settings.setUploadTo(c.serverId); LibraryWorker.runSoon(context) } },
                                    label = { Text(c.serverName) },
                                )
                            }
                        }
                    }
                }

                Group("Downloads") {
                    Text(
                        if (limit > 0) "${Formatter.formatShortFileSize(context, used)} of ${Formatter.formatShortFileSize(context, limit)} used"
                        else "${Formatter.formatShortFileSize(context, used)} used",
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1L, 2L, 5L, 10L, 0L).forEach { gb ->
                            val bytes = gb * 1024 * 1024 * 1024
                            FilterChip(
                                selected = limit == bytes,
                                onClick = {
                                    scope.launch {
                                        sync.settings.setStorageLimit(bytes)
                                        sync.freeUpSpace()
                                        LibraryWorker.runSoon(context)
                                    }
                                },
                                label = { Text(if (gb == 0L) "No limit" else "$gb GB") },
                            )
                        }
                    }
                    Hint(
                        "Over the limit, the books you read longest ago are cleared. Books you keep downloaded, " +
                            "and books from a library kept in full, stay.",
                    )
                    OutlinedButton(onClick = { scope.launch { sync.freeUpSpace(everything = true) } }) { Text("Free up space") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (manual) {
        ManualPairDialog(
            onDismiss = { manual = false },
            onPair = { address, port, cert, code ->
                manual = false
                busy = true
                scope.launch { paired(sync.pairManually(address, port, cert, code)) }
            },
        )
    }

    unpairing?.let { c ->
        AlertDialog(
            onDismissRequest = { unpairing = null },
            title = { Text("Unpair from ${c.serverName}?") },
            text = { Text("Its books leave this phone, unless another library has them too. Nothing on ${c.serverName} changes.") },
            confirmButton = {
                TextButton(onClick = { unpairing = null; scope.launch { sync.unpair(c.serverId) } }) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { unpairing = null }) { Text("Cancel") } },
        )
    }
}

/** One paired library: its name and state, syncing it, keeping all of it on the phone, unpairing. */
@Composable
private fun LibraryCard(
    connection: Connection,
    status: SyncStatus?,
    lastSync: Long?,
    progress: LibrarySync.KeepAll?,
    syncing: Boolean,
    onSync: () -> Unit,
    onKeepAll: (Boolean) -> Unit,
    onUnpair: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(connection.serverName, style = MaterialTheme.typography.titleLarge, fontFamily = com.perform.reader.ui.LiterataDisplay)
        Text(
            when (status) {
                SyncStatus.Syncing -> "Syncing…"
                is SyncStatus.Offline -> status.reason
                else -> lastSync?.let { "Synced ${DateUtils.getRelativeTimeSpanString(it)}" } ?: "Not synced yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Toggle(
            title = "Keep every book from ${connection.serverName}",
            detail = when {
                !connection.keepAll -> "Off: its books download when you open them."
                progress == null -> "Downloads on Wi-Fi, newest first."
                progress.outOfRoom -> "${progress.onPhone} of ${progress.total} fit. Raise the limit below to keep the rest."
                progress.onPhone < progress.total -> "${progress.onPhone} of ${progress.total} on this phone. The rest download on Wi-Fi."
                else -> "All ${progress.total} of its books are on this phone."
            },
            checked = connection.keepAll,
            onChange = onKeepAll,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSync, enabled = !syncing) { Text("Sync now") }
            TextButton(onClick = onUnpair) { Text("Unpair") }
        }
    }
}

/**
 * Pairing without the QR code: address and code, then a security check. The phone shows
 * a piece of the certificate it was given; the person makes sure the PC shows the same.
 */
@Composable
private fun ManualPairDialog(onDismiss: () -> Unit, onPair: (String, Int, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fingerprint by remember { mutableStateOf<String?>(null) }

    val host = address.trim().substringBefore(':')
    val port = address.trim().substringAfter(':', "").toIntOrNull() ?: Protocol.DEFAULT_PORT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fingerprint == null) "Pair with your PC" else "Security check") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val cert = fingerprint
                if (cert == null) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; error = null },
                        label = { Text("PC address") },
                        placeholder = { Text("192.168.1.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Text(
                        "Both are on the PC under Pair a phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Your PC's pairing panel shows a security check. It must match:")
                    Text(
                        Protocol.checkCode(cert),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "If it's different, something else on this network answered. Don't pair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            val cert = fingerprint
            if (cert == null) {
                TextButton(
                    enabled = host.isNotBlank() && code.length == 6 && !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            runCatching { LibraryClient.fingerprintAt(host, port) }
                                .onSuccess { fingerprint = it }
                                .onFailure { error = it.friendlyMessage() }
                            checking = false
                        }
                    },
                ) { Text(if (checking) "Connecting…" else "Next") }
            } else {
                TextButton(onClick = { onPair(host, port, cert, code) }) { Text("It matches, pair") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
