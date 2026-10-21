package com.perform.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class PlatformTest {

    private val windows = Platform.of("Windows 11", "C:\\Users\\reader") { name ->
        mapOf("APPDATA" to "C:\\Users\\reader\\AppData\\Roaming", "COMPUTERNAME" to "STUDY")[name]
    }
    private val mac = Platform.of("Mac OS X", "/Users/reader") { null }

    @Test
    fun `each OS gets its own platform`() {
        assertFalse(windows.isMac)
        assertTrue(mac.isMac)
    }

    @Test
    fun `data lives in the usual place for each OS`() {
        assertEquals(Path.of("C:\\Users\\reader\\AppData\\Roaming", "Perform"), windows.dataDir)
        assertEquals(Path.of("/Users/reader/Library/Application Support/Perform"), mac.dataDir)
        assertEquals(Path.of("/Users/reader/Books"), mac.defaultLibrary)
    }

    @Test
    fun `windows names the computer from the environment`() {
        assertEquals("STUDY", windows.computerName())
    }

    @Test
    fun `revealing a file uses Explorer or Finder`() {
        assertEquals(listOf("explorer", "/select,C:\\Books\\a.epub"), windows.revealCommand(Path.of("C:\\Books\\a.epub")))
        val book = Path.of("/Users/reader/Books/a.epub")
        assertEquals(listOf("open", "-R", book.toString()), mac.revealCommand(book))
        assertEquals("Trash", mac.trashName)
        assertEquals("Recycle Bin", windows.trashName)
    }

    @Test
    fun `start at login on Windows is a Run key entry`() {
        val add = (windows as WindowsPlatform).startAtLoginCommand(true, "C:\\Apps\\Perform Library.exe")
        assertEquals("\"C:\\Apps\\Perform Library.exe\" --background", add[add.indexOf("/d") + 1])
        assertEquals("delete", windows.startAtLoginCommand(false, null)[1])
    }

    @Test
    fun `start at login on the Mac is a LaunchAgent that runs at load`() {
        val plist = (mac as MacPlatform).launchAgentPlist("/Applications/Perform Library.app/Contents/MacOS/Perform Library")
        assertTrue(plist.contains("<string>com.perform.library</string>"))
        assertTrue(plist.contains("<string>/Applications/Perform Library.app/Contents/MacOS/Perform Library</string>"))
        assertTrue(plist.contains("<string>--background</string>"))
        assertTrue(plist.contains("<key>RunAtLoad</key>\n    <true/>"))
    }

    @Test
    fun `data kept under the old Preform name moves to the new folder once`() {
        val root = java.nio.file.Files.createTempDirectory("perform-rename")
        try {
            val legacy = root.resolve("Preform").also { java.nio.file.Files.createDirectories(it) }
            java.nio.file.Files.writeString(legacy.resolve("config.json"), "{}")
            val dataDir = root.resolve("Perform")
            AppPaths.adoptLegacyData(dataDir)
            org.junit.Assert.assertTrue(java.nio.file.Files.exists(dataDir.resolve("config.json")))
            org.junit.Assert.assertFalse(java.nio.file.Files.exists(legacy))
            // Already moved: nothing happens a second time.
            AppPaths.adoptLegacyData(dataDir)
            org.junit.Assert.assertTrue(java.nio.file.Files.exists(dataDir.resolve("config.json")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
