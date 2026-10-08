package com.preform.desktop

import com.preform.sync.Protocol
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Where the app keeps its own data. `PREFORM_DATA_DIR` and `PREFORM_LIBRARY` override the
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
        fun default(): AppPaths {
            val override = System.getenv("PREFORM_DATA_DIR")?.takeIf { it.isNotBlank() }
            val base = override ?: (System.getenv("APPDATA") ?: System.getProperty("user.home")) + "\\Preform"
            return AppPaths(Path.of(base))
        }
    }
}

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
) {
    fun save(paths: AppPaths) = paths.config.writeText(Protocol.json.encodeToString(serializer(), this))

    companion object {
        fun loadOrCreate(paths: AppPaths): AppConfig {
            if (paths.config.exists()) {
                runCatching { return Protocol.json.decodeFromString(serializer(), paths.config.readText()) }
            }
            val library = System.getenv("PREFORM_LIBRARY")?.takeIf { it.isNotBlank() }
                ?: DEFAULT_LIBRARY.takeIf { Path.of(it).exists() }
                ?: (System.getProperty("user.home") + "\\Documents\\Books")
            return AppConfig(
                serverId = UUID.randomUUID().toString(),
                serverName = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: "My PC",
                port = Protocol.DEFAULT_PORT,
                libraryRoot = library,
                keystorePassword = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) },
            ).also { it.save(paths) }
        }

        const val DEFAULT_LIBRARY = "E:\\Books"
    }
}
