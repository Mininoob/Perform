package com.perform.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Everything that differs between Windows and macOS. The rest of the app is the same
 * code on both; it asks this for paths, names and the few system hooks it needs.
 */
interface Platform {
    val isMac: Boolean

    /** Where the app keeps its settings, database, covers and backups. */
    val dataDir: Path

    /** The library folder offered on first run. */
    val defaultLibrary: Path

    /** The name phones show for this computer. */
    fun computerName(): String

    /** Starts the app (in the background) when the user logs in, or stops doing so. */
    fun setStartAtLogin(on: Boolean)

    /** Whether starting at login can be set: only from the installed app, not a build. */
    val canStartAtLogin: Boolean

    /** Shows a file selected in Explorer or Finder. */
    fun revealCommand(file: Path): List<String>

    /** How to run Tailscale's command-line tool. */
    val tailscaleCommand: String

    /** "Recycle Bin" or "Trash", "Explorer" or "Finder". */
    val trashName: String
    val fileManagerName: String

    fun reveal(file: Path) {
        runCatching { ProcessBuilder(revealCommand(file)).start() }
    }

    companion object {
        val current: Platform by lazy { of(System.getProperty("os.name"), System.getProperty("user.home"), System::getenv) }

        /** The platform for an OS name; the environment and home are passed in so tests can fake them. */
        fun of(osName: String, home: String, env: (String) -> String?): Platform =
            if (osName.startsWith("Mac", ignoreCase = true)) MacPlatform(Path.of(home), env) else WindowsPlatform(Path.of(home), env)

        /** The installed app's own executable, as jpackage reports it; null when running from a build. */
        val installedExecutable: String? get() = System.getProperty("jpackage.app-path")
    }
}

class WindowsPlatform(private val home: Path, private val env: (String) -> String?) : Platform {
    override val isMac = false

    override val dataDir: Path
        get() = Path.of(env("APPDATA")?.takeIf { it.isNotBlank() } ?: home.toString(), "Perform")

    override val defaultLibrary: Path
        get() = Path.of("E:\\Books").takeIf { it.exists() } ?: home.resolve("Documents").resolve("Books")

    override fun computerName(): String = env("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: "My PC"

    override val canStartAtLogin: Boolean get() = Platform.installedExecutable != null

    override fun setStartAtLogin(on: Boolean) {
        runCatching { ProcessBuilder(startAtLoginCommand(on, Platform.installedExecutable)).redirectErrorStream(true).start().waitFor() }
        // The entry the app made when it was still called Preform.
        runCatching { ProcessBuilder("reg", "delete", RUN_KEY, "/v", LEGACY_VALUE, "/f").redirectErrorStream(true).start().waitFor() }
    }

    /** The `reg` command that adds or removes the per-user Run entry. */
    fun startAtLoginCommand(on: Boolean, exe: String?): List<String> = if (on && exe != null) {
        listOf("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$exe\" --background", "/f")
    } else {
        listOf("reg", "delete", RUN_KEY, "/v", VALUE, "/f")
    }

    override fun revealCommand(file: Path) = listOf("explorer", "/select,$file")

    override val tailscaleCommand = "tailscale"
    override val trashName = "Recycle Bin"
    override val fileManagerName = "Explorer"

    private companion object {
        const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE = "Perform Library"
        const val LEGACY_VALUE = "Preform Library"
    }
}

class MacPlatform(private val home: Path, private val env: (String) -> String?) : Platform {
    override val isMac = true

    override val dataDir: Path get() = home.resolve("Library/Application Support/Perform")

    override val defaultLibrary: Path get() = home.resolve("Books")

    override fun computerName(): String =
        runCatching {
            val process = ProcessBuilder("scutil", "--get", "ComputerName").redirectErrorStream(true).start()
            process.waitFor(3, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim().takeIf { process.exitValue() == 0 && it.isNotBlank() }
        }.getOrNull()
            ?: runCatching { java.net.InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()
            ?: "My Mac"

    private val launchAgent: Path get() = home.resolve("Library/LaunchAgents/$AGENT_LABEL.plist")

    override val canStartAtLogin: Boolean get() = Platform.installedExecutable != null

    override fun setStartAtLogin(on: Boolean) {
        val exe = Platform.installedExecutable
        runCatching {
            if (on && exe != null) {
                launchAgent.parent.createDirectories()
                launchAgent.writeText(launchAgentPlist(exe))
            } else {
                launchAgent.deleteIfExists()
            }
            // The agent the app made when it was still called Preform.
            home.resolve("Library/LaunchAgents/$LEGACY_AGENT_LABEL.plist").deleteIfExists()
        }
    }

    /** A LaunchAgent that starts the app quietly at login. */
    fun launchAgentPlist(exe: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>$AGENT_LABEL</string>
            <key>ProgramArguments</key>
            <array>
                <string>${xmlEscape(exe)}</string>
                <string>--background</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    override fun revealCommand(file: Path) = listOf("open", "-R", file.toString())

    override val tailscaleCommand: String
        get() = TAILSCALE_APP.takeIf { Files.isExecutable(Path.of(it)) } ?: "tailscale"

    override val trashName = "Trash"
    override val fileManagerName = "Finder"

    private fun xmlEscape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        const val AGENT_LABEL = "com.perform.library"
        const val LEGACY_AGENT_LABEL = "com.preform.library"
        const val TAILSCALE_APP = "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
    }
}
