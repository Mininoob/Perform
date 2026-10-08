package com.preform.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.preform.sync.PairingInvite
import com.preform.sync.Protocol
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.swing.JFileChooser
import kotlin.system.exitProcess

/** The tray app's own palette: the phone's "Ink" base with its blue accent. */
private object Ink {
    val background = Color(0xFF14161B)
    val surface = Color(0xFF1D2027)
    val text = Color(0xFFE6E8EE)
    val muted = Color(0xFF9097A6)
    val accent = Color(0xFF7AA2F7)
    val danger = Color(0xFFF7768E)
}

fun main(args: Array<String>) {
    val service = LibraryService(AppPaths.default())
    service.start()
    val startHidden = "--background" in args

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        var pairing by remember { mutableStateOf(false) }
        val quit = {
            service.stop()
            exitApplication()
            exitProcess(0)
        }

        Tray(
            icon = BookIcon,
            tooltip = "Preform Library",
            onAction = { visible = true },
            menu = {
                Item("Open", onClick = { visible = true })
                Item("Pair a phone", onClick = { pairing = true; visible = true })
                Separator()
                Item("Quit", onClick = quit)
            },
        )

        Window(
            onCloseRequest = { visible = false }, // keep serving from the tray
            visible = visible,
            title = "Preform Library",
            icon = BookIcon,
            state = rememberWindowState(size = DpSize(480.dp, 720.dp)),
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink.background,
                    surface = Ink.surface,
                    primary = Ink.accent,
                    onPrimary = Ink.background,
                    onSurface = Ink.text,
                    onBackground = Ink.text,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Ink.background) {
                    LibraryWindow(service, pairing, onPairingChange = { pairing = it })
                }
            }
        }
    }
}

@Composable
private fun LibraryWindow(service: LibraryService, pairing: Boolean, onPairingChange: (Boolean) -> Unit) {
    val status by service.status.collectAsState()

    // Keep "last seen" and addresses fresh while the window is up.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            service.refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(status)

        status.error?.let { Text(it, color = Ink.danger) }

        Section("Library") {
            Text(status.libraryRoot, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(
                if (status.libraryFound) "${status.bookCount} ${if (status.bookCount == 1) "book" else "books"}"
                else "This folder doesn't exist",
                color = if (status.libraryFound) Ink.muted else Ink.danger,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { chooseFolder(status.libraryRoot)?.let { service.setLibraryRoot(it.toPath()) } }) {
                    Text("Change…")
                }
                TextButton(onClick = { runCatching { Desktop.getDesktop().open(File(status.libraryRoot)) } }) {
                    Text("Open folder")
                }
                TextButton(onClick = { Thread { service.rescan() }.start() }) { Text("Rescan") }
            }
        }

        Section("Phones") {
            if (pairing) {
                PairPanel(service, onDone = { service.closePairing(); onPairingChange(false) })
            } else {
                Button(onClick = { onPairingChange(true) }) { Text("Pair a phone") }
            }
            if (status.devices.isEmpty()) {
                Text("No phones paired yet.", color = Ink.muted, fontSize = 13.sp)
            }
            status.devices.forEach { device -> DeviceRow(device, onRevoke = { service.revoke(device.deviceId) }) }
        }

        Section("Reachable at") {
            if (status.addresses.isEmpty()) Text("No network connection", color = Ink.muted, fontSize = 13.sp)
            status.addresses.forEach { address ->
                val tag = when {
                    Addresses.isTailscale(address) || address.endsWith(".ts.net") -> "Tailscale, works away from home"
                    else -> "Home network"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(address, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(tag, color = Ink.muted, fontSize = 12.sp)
                }
            }
        }

        Section("Genres") {
            var lookUp by remember { mutableStateOf(service.config.lookUpGenres) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Look up genres online")
                    Text(
                        "For books that don't list a genre, ask Open Library. Only the title and author are sent.",
                        color = Ink.muted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = lookUp,
                    onCheckedChange = { lookUp = it; service.setLookUpGenres(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent),
                )
            }
        }

        if (Autostart.exe != null) {
            var autostart by remember { mutableStateOf(service.config.startWithWindows) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start with Windows", modifier = Modifier.weight(1f))
                Switch(
                    checked = autostart,
                    onCheckedChange = { autostart = it; service.setStartWithWindows(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent),
                )
            }
        }

        Text(
            "Closing this window keeps the library running in the tray.",
            color = Ink.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Header(status: Status) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(BookIcon, null, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Preform Library", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            val running = status.error == null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (running) Color(0xFF9ECE6A) else Ink.danger, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(6.dp))
                Text(if (running) "Sharing" else "Stopped", color = Ink.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = Ink.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        HorizontalDivider(color = Ink.surface)
        content()
    }
}

@Composable
private fun PairPanel(service: LibraryService, onDone: () -> Unit) {
    val invite = remember { service.invite() }
    val qr = remember(invite) { qrImage(invite) }
    var secondsLeft by remember { mutableStateOf(PairingWindow.VALID_MS / 1000) }

    LaunchedEffect(invite) {
        while (secondsLeft > 0 && service.pairingOpen) {
            delay(1_000)
            secondsLeft--
        }
        onDone()
    }
    // A phone paired: the window closes itself (the code is single-use).
    val status by service.status.collectAsState()
    val pairedBefore = remember { status.devices.size }
    LaunchedEffect(status.devices.size) { if (status.devices.size > pairedBefore) onDone() }

    Column(
        Modifier.fillMaxWidth().background(Ink.surface, RoundedCornerShape(12.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("In Preform on your phone: Settings → Private library → Pair with PC", fontSize = 13.sp, color = Ink.muted)
        Box(Modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Image(qr, "Pairing code", Modifier.size(220.dp))
        }
        Text("Or type this code", color = Ink.muted, fontSize = 12.sp)
        Text(
            invite.code.chunked(3).joinToString(" "),
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
        )
        Text("Expires in %d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = Ink.muted, fontSize = 12.sp)
        Text(
            "Typing the code? Your phone will show a security check. It must read ${Protocol.checkCode(invite.certSha256)}",
            color = Ink.muted,
            fontSize = 12.sp,
        )
        TextButton(onClick = onDone) { Text("Cancel") }
    }
}

@Composable
private fun DeviceRow(device: Device, onRevoke: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.name)
            Text(
                device.lastSeen?.let { "Last synced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" }
                    ?: "Not synced yet",
                color = Ink.muted,
                fontSize = 12.sp,
            )
        }
        if (confirming) {
            TextButton(onClick = { confirming = false }) { Text("Keep") }
            Button(onClick = onRevoke, colors = ButtonDefaults.buttonColors(containerColor = Ink.danger)) { Text("Remove") }
        } else {
            TextButton(onClick = { confirming = true }) { Text("Remove", color = Ink.danger) }
        }
    }
}

private fun chooseFolder(current: String): File? {
    val chooser = JFileChooser(current).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose your library folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun qrImage(invite: PairingInvite): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        invite.encode(),
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
    )
    val scale = 8
    val image = BufferedImage(matrix.width * scale, matrix.height * scale, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until image.width) for (y in 0 until image.height) {
        image.setRGB(x, y, if (matrix.get(x / scale, y / scale)) 0x000000 else 0xFFFFFF)
    }
    return image.toComposeImageBitmap()
}

/** A closed book in the accent blue, drawn rather than shipped as a file. */
private object BookIcon : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.08f)
        // Pages peeking out on the right.
        drawRoundRect(Color(0xFFE6E8EE), Offset(w * 0.22f, h * 0.10f), Size(w * 0.64f, h * 0.80f), r)
        // Cover.
        drawRoundRect(Ink.accent, Offset(w * 0.14f, h * 0.06f), Size(w * 0.64f, h * 0.84f), r)
        // Spine.
        drawRect(Color(0xFF5A7FD0), Offset(w * 0.14f, h * 0.06f), Size(w * 0.10f, h * 0.84f))
        // Title band.
        drawRect(Ink.background.copy(alpha = 0.55f), Offset(w * 0.34f, h * 0.24f), Size(w * 0.32f, h * 0.06f))
    }
}
