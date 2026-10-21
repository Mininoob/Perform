package com.perform.desktop

import com.perform.sync.Protocol
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Where the app keeps its own data. `PERFORM_DATA_DIR` and `PERFORM_LIBRARY` override the
 * defaults so a test run never touches the real settings or library.
 */
class AppPaths(val data: Path) {
    val config: Path = data.resolve("config.json")
    val database: Path = data.resolve("library.db")
    val identity: Path = data.resolve("identity.jks")
    val covers: Path = data.resolve("covers")
    val temp: Path = data.resolve("temp")
    /** Originals of book files that information was written into. */
    val backups: Path = data.resolve("originals")

    init {
        data.createDirectories()
        temp.createDirectories()
    }

    companion object {
        fun default(platform: Platform = Platform.current): AppPaths {
            val override = System.getenv("PERFORM_DATA_DIR")?.takeIf { it.isNotBlank() }
            if (override != null) return AppPaths(Path.of(override))
            adoptLegacyData(platform.dataDir)
            return AppPaths(platform.dataDir)
        }

        /**
         * The app was called Preform before, and kept its data in a folder of that name.
         * The first run under the new name moves that folder across, so libraries, settings,
         * paired phones and the certificate they trust all carry over.
         */
        fun adoptLegacyData(dataDir: Path) {
            val legacy = dataDir.resolveSibling("Preform")
            if (dataDir.exists() || !legacy.exists()) return
            runCatching { java.nio.file.Files.move(legacy, dataDir) }
        }
    }
}

/** The organizer's look: the same bases and accents as the phone. */
enum class ThemeBase(val dark: Boolean) {
    MONO(false), PAPER(false), PARCHMENT(false),
    NOIR(true), INK(true), SLATE(true),
}

enum class ThemeAccent { MONO, RED, PINK, YELLOW, GREEN, TEAL, BLUE, INDIGO, VIOLET }

@Serializable
data class AppConfig(
    val serverId: String,
    val serverName: String,
    val port: Int,
    val libraryRoot: String,
    /** Protects the TLS key file on disk; generated once, never shown. */
    val keystorePassword: String,
    val startWithWindows: Boolean = false,
    /** Ask Open Library for what books don't say about themselves (genres, author, cover...). */
    val lookUpOnline: Boolean = true,
    /** Also write what was found into the book files, keeping the originals as backups. */
    val writeIntoFiles: Boolean = true,
    val themeBase: ThemeBase = ThemeBase.INK,
    val themeAccent: ThemeAccent = ThemeAccent.BLUE,
    /** Genres made here, alongside the standard ones; they travel to phones like any other. */
    val customGenres: List<String> = emptyList(),
) {
    fun save(paths: AppPaths) = paths.config.writeText(Protocol.json.encodeToString(serializer(), this))

    companion object {
        fun loadOrCreate(paths: AppPaths, platform: Platform = Platform.current): AppConfig {
            if (paths.config.exists()) {
                runCatching { return Protocol.json.decodeFromString(serializer(), paths.config.readText()) }
            }
            val library = System.getenv("PERFORM_LIBRARY")?.takeIf { it.isNotBlank() } ?: platform.defaultLibrary.toString()
            return AppConfig(
                serverId = UUID.randomUUID().toString(),
                serverName = platform.computerName(),
                port = Protocol.DEFAULT_PORT,
                libraryRoot = library,
                keystorePassword = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) },
            ).also { it.save(paths) }
        }
    }
}
