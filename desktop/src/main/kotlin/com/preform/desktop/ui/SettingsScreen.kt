package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.desktop.AppConfig
import com.preform.desktop.LibraryService
import com.preform.desktop.Platform
import com.preform.desktop.Status
import com.preform.desktop.ThemeAccent
import com.preform.desktop.ThemeBase
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
fun SettingsScreen(service: LibraryService, config: AppConfig, status: Status) {
    val platform = service.platform
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp)) {
        Text("Settings", fontFamily = LiterataDisplay, fontSize = 26.sp, color = colors.text)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.widthIn(max = 640.dp)) {
            Section("Library folder")
            Text(status.libraryRoot, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.text)
            if (!status.libraryFound) Text("This folder doesn't exist.", color = colors.danger, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { chooseFolder(platform, status.libraryRoot)?.let(service::setLibraryRoot) }) { Text("Change…") }
                TextButton(colors = quietButton(), onClick = { runCatching { java.awt.Desktop.getDesktop().open(File(status.libraryRoot)) } }) {
                    Text("Open in ${platform.fileManagerName}")
                }
                TextButton(colors = quietButton(), onClick = service::rescanSoon) { Text("Check for changes") }
            }

            Section("Book information")
            SettingSwitch(
                title = "Look up book information online",
                detail = "Genres, missing titles and authors, covers, descriptions and years from Open Library. " +
                    "Only titles, authors and file names are sent.",
                checked = config.lookUpOnline,
                onChange = service::setLookUpOnline,
            )
            SettingSwitch(
                title = "Write it into the book files",
                detail = "Fills in only what a file is missing, so other apps see it too. Each original is kept and can be put back.",
                checked = config.writeIntoFiles,
                enabled = config.lookUpOnline,
                onChange = service::setWriteIntoFiles,
            )
            RestoreOriginals(service)

            Section("Appearance")
            Text("Theme", color = colors.text, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeBase.entries.forEach { base ->
                    BaseSwatch(base, config.themeAccent, selected = config.themeBase == base) { service.setTheme(base = base) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Accent · ${config.themeAccent.label}", color = colors.text, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeAccent.entries.forEach { accent ->
                    val selected = config.themeAccent == accent
                    Box(
                        Modifier
                            .size(28.dp)
                            .border(2.dp, if (selected) colors.text else colors.background, CircleShape)
                            .padding(4.dp)
                            .background(accent.colorOn(config.themeBase), CircleShape)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable { service.setTheme(accent = accent) },
                    )
                }
            }
            Text("The same themes as Preform on your phone.", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))

            if (platform.canStartAtLogin) {
                Section("Startup")
                SettingSwitch(
                    title = if (platform.isMac) "Open at login" else "Start with Windows",
                    detail = "Shares your library in the background as soon as you sign in.",
                    checked = config.startWithWindows,
                    onChange = service::setStartAtLogin,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RestoreOriginals(service: LibraryService) {
    var backups by remember { mutableStateOf(service.backupCount) }
    var confirm by remember { mutableStateOf(false) }
    if (backups == 0) return
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$backups original ${if (backups == 1) "file" else "files"} kept",
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (confirm) {
            TextButton(colors = quietButton(), onClick = { confirm = false }) { Text("Cancel") }
            Button(onClick = {
                confirm = false
                Thread { service.restoreOriginals(); backups = service.backupCount }.start()
            }) { Text("Put originals back") }
        } else {
            TextButton(colors = quietButton(), onClick = { confirm = true }) { Text("Restore originals…") }
        }
    }
}

@Composable
private fun BaseSwatch(base: ThemeBase, accent: ThemeAccent, selected: Boolean, onClick: () -> Unit) {
    val p = palette(base, accent)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 72.dp, height = 52.dp)
                .border(2.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(10.dp))
                .padding(3.dp)
                .background(p.background, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 34.dp, height = 5.dp).background(p.text, RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 24.dp, height = 5.dp).background(p.muted, RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 16.dp, height = 5.dp).background(p.accent, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(base.label, color = if (selected) colors.text else colors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    Text(title.uppercase(), color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = colors.line)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (enabled) colors.text else colors.muted, fontSize = 14.sp)
            Text(detail, color = colors.muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

/** A folder picker that looks native: Finder's own on the Mac, Swing's on Windows. */
fun chooseFolder(platform: Platform, current: String, title: String = "Choose your library folder"): Path? {
    if (platform.isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply { directory = current; isVisible = true }
            return dialog.file?.let { Path.of(dialog.directory, it) }
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }
    val chooser = JFileChooser(current).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
