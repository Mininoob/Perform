package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.desktop.Device
import com.preform.desktop.LibraryService
import com.preform.desktop.PeerView
import com.preform.sync.PeerHello
import com.preform.sync.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The Devices page's computers: other Preform libraries this one sees, computers that see
 * this one, and libraries found on the network to connect to.
 */
@Composable
fun ComputersSection(service: LibraryService, state: WindowState, browsers: List<Device>) {
    val views by service.peers.views.collectAsState()
    val nearby by service.nearby.collectAsState()
    var connecting by remember { mutableStateOf<ConnectTarget?>(null) }
    val scope = rememberCoroutineScope()
    val computers = views.filter { it.peer.kind == com.preform.sync.PeerKind.COMPUTER }

    if (computers.isEmpty() && browsers.isEmpty()) {
        Text(
            "Connect another computer's Preform Library to browse its shelves and copy books between them.",
            color = colors.muted,
            fontSize = 13.sp,
        )
    }
    var previewing by remember { mutableStateOf<PeerView?>(null) }
    computers.forEach { view ->
        PeerRow(
            view,
            onBrowse = { state.go(Place.Peer(view.peer.peerId)) },
            onDisconnect = { service.peers.disconnect(view.peer.peerId) },
            onMirror = { on -> if (on) previewing = view else service.peers.setMirror(view.peer.peerId, false) },
        )
    }
    previewing?.let { view ->
        MirrorDialog(service, view, onDismiss = { previewing = null }) {
            previewing = null
            service.peers.setMirror(view.peer.peerId, true)
            scope.launch(Dispatchers.IO) { service.peers.refresh(view.peer.peerId) }
        }
    }
    browsers.forEach { device ->
        DeviceLine(device.name, "Can see this library" + (device.lastSeen?.let { " · last looked ${when_(it)}" } ?: ""))
    }

    Spacer(Modifier.height(18.dp))
    Text("NEARBY", color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
    val known = computers.map { it.peer.peerId }.toSet()
    val found = nearby.filter { it.serverId !in known }
    if (found.isEmpty()) Text("No other libraries found on this network.", color = colors.muted, fontSize = 13.sp)
    found.forEach { library ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Books, null, tint = colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(library.name, color = colors.text, fontSize = 14.sp, fontFamily = Literata)
                Text(library.addresses.first(), color = colors.muted, fontSize = 12.sp)
            }
            TextButton(colors = quietButton(), onClick = { connecting = ConnectTarget(library.addresses, library.port, library.name) }) {
                Text("Connect…", color = colors.accent)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(colors = quietButton(), onClick = { connecting = ConnectTarget(emptyList(), Protocol.DEFAULT_PORT, null) }) {
            Text("Connect by address…", color = colors.text, fontSize = 13.sp)
        }
        TextButton(colors = quietButton(), onClick = { state.pairing = true }) {
            Text("Show this library's code", color = colors.text, fontSize = 13.sp)
        }
    }

    connecting?.let { target ->
        ConnectDialog(service, target, onDismiss = { connecting = null }) { name, peerId ->
            connecting = null
            state.notice = Notice("Connected to $name")
            state.go(Place.Peer(peerId))
        }
    }
}

@Composable
private fun PeerRow(view: PeerView, onBrowse: () -> Unit, onDisconnect: () -> Unit, onMirror: (Boolean) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Column {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyphs.Books, null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(view.peer.name, color = colors.text, fontSize = 14.sp, fontFamily = Literata)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(7.dp).background(if (view.online) colors.ok else colors.line, CircleShape))
            }
            Text(
                listOfNotNull(
                    "${view.bookCount} ${if (view.bookCount == 1) "book" else "books"}",
                    view.peer.platform.takeIf { it != "unknown" }?.let { if (it == "macos") "Mac" else "Windows" },
                    if (view.online) null else view.peer.lastSync?.let { "seen ${when_(it)}" } ?: "not reached yet",
                ).joinToString(" · "),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        if (confirming) {
            TextButton(colors = quietButton(), onClick = { confirming = false }) { Text("Keep") }
            Button(onClick = onDisconnect, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text("Disconnect") }
        } else {
            TextButton(colors = quietButton(), onClick = onBrowse) { Text("Browse", color = colors.accent) }
            TextButton(colors = quietButton(), onClick = { confirming = true }) { Text("Disconnect", color = colors.danger) }
        }
    }
    // Mirroring: this library takes the other's changes. Both sides on keeps them the same.
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Mirror this library", color = colors.text, fontSize = 13.sp)
            Text(
                when {
                    view.peer.mirror && view.mirrorsUs -> "Kept the same both ways: books, folders, details and removals."
                    view.peer.mirror -> "Its changes come here. Turn mirroring on there too to send yours back."
                    view.mirrorsUs -> "It mirrors this library. Turn this on to take its changes too."
                    else -> "Off: browse it and copy books one at a time."
                },
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = view.peer.mirror,
            onCheckedChange = onMirror,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
    }
}

/** Before mirroring starts: what it will do here first. Nothing is removed by turning it on. */
@Composable
private fun MirrorDialog(service: LibraryService, view: PeerView, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val preview = remember(view) { service.peers.mirrorPreview(view.peer.peerId) }
    val name = view.peer.name
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text("Mirror $name?", fontFamily = LiterataDisplay, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOfNotNull(
                        if (preview.copy > 0) "${preview.copy} ${if (preview.copy == 1) "book" else "books"} (${size(preview.bytes)}) will be copied here" else null,
                        if (preview.move > 0) "${preview.move} will move to match $name" else null,
                    ).joinToString(", and ").ifEmpty { "This library already has everything $name has." } + ".",
                    color = colors.text,
                    fontSize = 14.sp,
                )
                Text(
                    "From then on, books added, moved, edited or removed on $name follow here, newest change first. " +
                        "Nothing already here is removed by turning this on, and removals go to the ${service.platform.trashName}.",
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                Text("Start mirroring")
            }
        },
        dismissButton = { TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel", color = colors.text) } },
    )
}

@Composable
private fun DeviceLine(name: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyphs.Books, null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = colors.text, fontSize = 14.sp, fontFamily = Literata)
            Text(detail, color = colors.muted, fontSize = 12.sp)
        }
    }
}

private fun when_(at: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))

/** Where to connect: a library found nearby, or an address still to be typed. */
class ConnectTarget(val addresses: List<String>, val port: Int, val name: String?)

/**
 * Connecting to another computer's library: reach it, show the security check the
 * other screen must match, then trade its pairing code for read access.
 */
@Composable
private fun ConnectDialog(service: LibraryService, target: ConnectTarget, onDismiss: () -> Unit, onConnected: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(target.addresses.firstOrNull().orEmpty()) }
    var found by remember { mutableStateOf<Pair<PeerHello, String>?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val host = address.trim().substringBefore(':')
    val port = address.trim().substringAfter(':', "").toIntOrNull() ?: target.port

    fun reach() {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { service.peers.hello(host, port) } }
            busy = false
            result.onSuccess { found = it }.onFailure { error = "Couldn't reach a Preform Library at $host. Is it running?" }
        }
    }

    fun connect(hello: PeerHello, cert: String) {
        busy = true
        error = null
        scope.launch {
            val addresses = (listOf(host) + target.addresses).distinct()
            val result = withContext(Dispatchers.IO) { runCatching { service.peers.connect(addresses, port, cert, code, hello) } }
            busy = false
            result.onSuccess { onConnected(it.name, it.peerId) }
                .onFailure { error = it.message?.takeIf { m -> m.isNotBlank() && m.length < 160 } ?: "That code didn't work. Check it and try again." }
        }
    }

    // A library found nearby needs no address: reach it straight away.
    if (found == null && target.addresses.isNotEmpty() && !busy && error == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { reach() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(found?.first?.name?.let { "Connect to $it" } ?: target.name?.let { "Connect to $it" } ?: "Connect to a library", fontFamily = LiterataDisplay, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val hello = found
                if (hello == null) {
                    if (target.addresses.isEmpty()) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it; error = null },
                            singleLine = true,
                            label = { Text("Address") },
                            placeholder = { Text("192.168.1.20 or my-mac.tailnet.ts.net") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (busy) "Reaching it…" else "", color = colors.muted, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        "On ${hello.first.name}, open Devices and choose Show this library's code. Its security check must read:",
                        color = colors.text,
                        fontSize = 13.sp,
                    )
                    Text(Protocol.checkCode(hello.second), fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Text("If it's different, something else answered. Don't connect.", color = colors.muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
                        singleLine = true,
                        label = { Text("Its 6-digit code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "This computer will see that library's shelves and can copy books from it. Nothing there changes.",
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
                error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            val hello = found
            if (hello == null) {
                TextButton(colors = quietButton(), enabled = host.isNotBlank() && !busy, onClick = ::reach) {
                    Text(if (busy) "Reaching…" else "Next", color = colors.accent)
                }
            } else {
                Button(
                    enabled = code.length == 6 && !busy,
                    onClick = { connect(hello.first, hello.second) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(if (busy) "Connecting…" else "It matches, connect") }
            }
        },
        dismissButton = { TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel", color = colors.text) } },
    )
}
