package com.preform.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.preform.desktop.ui.BookMark
import com.preform.desktop.ui.CoverCache
import com.preform.desktop.ui.LibraryWindow
import com.preform.desktop.ui.Place
import com.preform.desktop.ui.PreformTheme
import com.preform.desktop.ui.WindowState
import com.preform.desktop.ui.colorOn
import java.awt.Dimension
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val platform = Platform.current
    if (platform.isMac) {
        // Menu-bar icons in one color, tinted by macOS for light and dark menu bars.
        System.setProperty("apple.awt.enableTemplateImages", "true")
    }
    val service = LibraryService(AppPaths.default(platform), platform)
    service.start()
    val startHidden = "--background" in args

    application {
        val config by service.configFlow.collectAsState()
        var visible by remember { mutableStateOf(!startHidden) }
        val windowState = remember { WindowState() }
        val covers = remember { CoverCache(service.library) }
        val accent = config.themeAccent.colorOn(config.themeBase)
        val windowIcon = remember { com.preform.desktop.ui.SeriesMark() }
        // The macOS menu bar wants a one-color template icon; Windows' tray shows the colored mark.
        val trayIcon = remember { if (platform.isMac) BookMark(Color.Black, monochrome = true) else com.preform.desktop.ui.SeriesMark() }

        Tray(
            icon = trayIcon,
            tooltip = "Preform Library",
            onAction = { visible = true },
            menu = {
                Item("Open Preform Library", onClick = { visible = true })
                Item("Pair a phone", onClick = {
                    windowState.go(Place.Phones)
                    windowState.pairing = true
                    visible = true
                })
                Separator()
                Item("Quit", onClick = {
                    service.stop()
                    exitApplication()
                    exitProcess(0)
                })
            },
        )

        Window(
            onCloseRequest = { visible = false }, // keeps sharing from the tray / menu bar
            visible = visible,
            title = "Preform Library",
            icon = windowIcon,
            state = rememberWindowState(size = DpSize(1240.dp, 820.dp)),
            onKeyEvent = { windowState.onKey(it, platform.isMac) },
        ) {
            window.minimumSize = Dimension(960, 620)
            PreformTheme(config.themeBase, config.themeAccent) {
                LibraryWindow(service, covers, windowState)
            }
        }
    }
}
