package com.perform.desktop

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.perform.desktop.ui.BookMark
import com.perform.desktop.ui.ResizeEdges
import com.perform.desktop.ui.TitleBar
import com.perform.desktop.ui.blendMacTitleBar
import com.perform.desktop.ui.colors
import com.perform.desktop.ui.windowOutline
import com.perform.desktop.ui.CoverCache
import com.perform.desktop.ui.LibraryWindow
import com.perform.desktop.ui.Place
import com.perform.desktop.ui.PerformTheme
import com.perform.desktop.ui.WindowState
import com.perform.desktop.ui.colorOn
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
        val covers = remember { CoverCache(service.library, service.peers::cover) }
        val accent = config.themeAccent.colorOn(config.themeBase)
        val windowIcon = remember { com.perform.desktop.ui.SeriesMark() }
        // The macOS menu bar wants a one-color template icon; Windows' tray shows the colored mark.
        val trayIcon = remember { if (platform.isMac) BookMark(Color.Black, monochrome = true) else com.perform.desktop.ui.SeriesMark() }

        Tray(
            icon = trayIcon,
            tooltip = "Perform Library",
            onAction = { visible = true },
            menu = {
                Item("Open Perform Library", onClick = { visible = true })
                Item("Pair a phone", onClick = {
                    windowState.go(Place.Devices)
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
            title = "Perform Library",
            icon = windowIcon,
            state = rememberWindowState(size = DpSize(1240.dp, 820.dp)),
            onKeyEvent = { windowState.onKey(it, platform.isMac) },
            // Our own title bar: macOS keeps its buttons over it; Windows draws its own.
            undecorated = !platform.isMac,
        ) {
            window.minimumSize = Dimension(960, 620)
            if (platform.isMac) blendMacTitleBar()
            PerformTheme(config.themeBase, config.themeAccent) {
                Box(Modifier.fillMaxSize().windowOutline(colors.line, enabled = !platform.isMac)) {
                    Column(Modifier.fillMaxSize()) {
                        TitleBar(platform.isMac)
                        LibraryWindow(service, covers, windowState)
                    }
                    if (!platform.isMac) ResizeEdges()
                }
            }
        }
    }
}
