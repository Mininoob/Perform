package com.preform.desktop.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.preform.desktop.Addresses
import com.preform.desktop.Device
import com.preform.desktop.LibraryService
import com.preform.desktop.PairingWindow
import com.preform.sync.PairingInvite
import com.preform.sync.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.text.DateFormat
import java.util.Date

/** Pair a phone, see paired phones, and where they can reach this computer. */
@Composable
fun PhonesScreen(service: LibraryService, state: WindowState) {
    val status by service.status.collectAsState()

    // Devices and addresses change outside this window (a phone syncs, Wi-Fi changes).
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { service.refreshStatus() }
            delay(10_000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp)) {
        Text("Devices", fontFamily = LiterataDisplay, fontSize = 26.sp, color = colors.text)
        Text("Phones read this library, at home or over Tailscale. Other computers' libraries can be browsed and copied from.", color = colors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        Column(Modifier.widthIn(max = 640.dp)) {
            if (state.pairing) {
                PairPanel(service, onDone = { service.closePairing(); state.pairing = false })
            } else {
                Button(onClick = { state.pairing = true }) { Text("Pair a phone") }
            }

            val (computers, phones) = status.devices.partition { it.kind == com.preform.sync.PeerKind.COMPUTER }
            Section("Phones")
            if (phones.isEmpty()) Text("No phones paired yet.", color = colors.muted, fontSize = 13.sp)
            phones.forEach { DeviceRow(it, onRevoke = { service.revoke(it.deviceId) }) }

            Section("Computers")
            ComputersSection(service, state, browsers = computers)

            Section("Reachable at")
            if (status.addresses.isEmpty()) Text("No network connection.", color = colors.muted, fontSize = 13.sp)
            status.addresses.forEach { address ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(address, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            Addresses.isTailscale(address) || address.endsWith(".ts.net") -> "Tailscale · works away from home"
                            address.startsWith("192.168.") || address.startsWith("10.") -> "Home network"
                            else -> "Other network"
                        },
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    Text(title.uppercase(), color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = colors.line)
    Spacer(Modifier.height(8.dp))
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
    // A phone paired: the panel closes itself (the code is single-use).
    val status by service.status.collectAsState()
    val pairedBefore = remember { status.devices.size }
    LaunchedEffect(status.devices.size) { if (status.devices.size > pairedBefore) onDone() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            withContext(Dispatchers.IO) { service.refreshStatus() }
        }
    }

    Row(
        Modifier.fillMaxWidth().background(colors.raised, RoundedCornerShape(14.dp)).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(14.dp)) {
            Image(qr, "Pairing code", Modifier.size(200.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Scan with Preform", fontFamily = LiterataDisplay, fontSize = 20.sp, color = colors.text)
            Text("On your phone: Settings → Private library → Scan the PC's code.", color = colors.muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("Or type this code", color = colors.muted, fontSize = 12.sp)
            Text(
                invite.code.chunked(3).joinToString(" "),
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp,
                color = colors.text,
            )
            Text(
                "The phone will show a security check. It must read ${Protocol.checkCode(invite.certSha256)}",
                color = colors.muted,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Expires in %d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(colors = quietButton(), onClick = onDone) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onRevoke: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyphs.Phone, null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, color = colors.text, fontSize = 14.sp)
            Text(
                device.lastSeen?.let { "Last synced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" }
                    ?: "Not synced yet",
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        if (confirming) {
            TextButton(colors = quietButton(), onClick = { confirming = false }) { Text("Keep") }
            Button(onClick = onRevoke, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text("Remove") }
        } else {
            TextButton(colors = quietButton(), onClick = { confirming = true }) { Text("Remove", color = colors.danger) }
        }
    }
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
