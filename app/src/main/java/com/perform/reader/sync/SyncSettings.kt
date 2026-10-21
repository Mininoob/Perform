package com.perform.reader.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.perform.sync.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** A paired library (a PC or a Mac): where it is, the one certificate to trust, and this phone's token. */
@Serializable
data class Connection(
    val serverId: String,
    val serverName: String,
    val port: Int,
    /** SHA-256 of the computer's certificate, hex. Nothing else is trusted. */
    val certSha256: String,
    /** Every address the computer gave, best first. */
    val addresses: List<String>,
    val token: String,
    /** The address that answered last time; tried first. */
    val lastGood: String? = null,
    /** Full sync: keep every book from this library on the phone, not just the ones opened. */
    val keepAll: Boolean = false,
) {
    /** Last good address first, then the rest in the computer's order. */
    val addressOrder: List<String> get() = listOfNotNull(lastGood) + (addresses - setOfNotNull(lastGood))
}

/**
 * Private-library state, kept apart from the reading preferences: tokens are credentials,
 * and none of it belongs in settings backups or exports. The phone can be paired with
 * several libraries; each keeps its own place in its catalog.
 */
class SyncSettings(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.syncStore

    /** Every paired library, in the order they were paired. */
    val connections: Flow<List<Connection>> = store.data.map(::decodeConnections)

    suspend fun connectionsNow(): List<Connection> = connections.first()

    suspend fun connection(serverId: String): Connection? = connectionsNow().firstOrNull { it.serverId == serverId }

    /** Adds a library, or updates the one with the same id (pairing it again). */
    suspend fun saveConnection(connection: Connection) = store.edit { prefs ->
        val list = decodeConnections(prefs)
        val updated = if (list.any { it.serverId == connection.serverId }) {
            list.map { if (it.serverId == connection.serverId) connection else it }
        } else {
            list + connection
        }
        prefs[CONNECTIONS] = encode(updated)
    }

    /** Forgets a library and where its catalog had got to. */
    suspend fun removeConnection(serverId: String) = store.edit { prefs ->
        prefs[CONNECTIONS] = encode(decodeConnections(prefs).filter { it.serverId != serverId })
        prefs.remove(cursorKey(serverId))
        prefs.remove(lastSyncKey(serverId))
        if (prefs[UPLOAD_TO] == serverId) prefs.remove(UPLOAD_TO)
    }

    suspend fun setKeepAll(serverId: String, on: Boolean) {
        connection(serverId)?.let { saveConnection(it.copy(keepAll = on)) }
    }

    suspend fun rememberGoodAddress(serverId: String, address: String) {
        val current = connection(serverId) ?: return
        if (current.lastGood != address) saveConnection(current.copy(lastGood = address))
    }

    /** This phone's id in every stamp it makes; made once, kept for good. */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        store.edit { if (it[DEVICE_ID] == null) it[DEVICE_ID] = fresh }
        return store.data.first()[DEVICE_ID]!!
    }

    /** Where to resume [serverId]'s catalog. */
    suspend fun catalogCursor(serverId: String): Long = store.data.first()[cursorKey(serverId)] ?: 0L

    suspend fun setCatalogCursor(serverId: String, value: Long) = store.edit { it[cursorKey(serverId)] = value }

    /** When each library last synced, by id. */
    val lastSyncs: Flow<Map<String, Long>> = store.data.map { prefs ->
        decodeConnections(prefs).mapNotNull { c -> prefs[lastSyncKey(c.serverId)]?.let { c.serverId to it } }.toMap()
    }

    suspend fun setLastSync(serverId: String, value: Long) = store.edit { it[lastSyncKey(serverId)] = value }

    /** Downloads beyond this are cleared, least recently read first. 0 means no limit. */
    val storageLimit: Flow<Long> = store.data.map { it[STORAGE_LIMIT] ?: DEFAULT_STORAGE_LIMIT }
    suspend fun setStorageLimit(bytes: Long) = store.edit { it[STORAGE_LIMIT] = bytes }

    /** Send books added on this phone (its folder and imports) into a library. */
    val shareLocal: Flow<Boolean> = store.data.map { it[SHARE_LOCAL] ?: true }
    suspend fun setShareLocal(on: Boolean) = store.edit { it[SHARE_LOCAL] = on }

    /** Which library the phone's own books go to; the first paired when not chosen. */
    val uploadTo: Flow<String?> = store.data.map { prefs ->
        val list = decodeConnections(prefs)
        prefs[UPLOAD_TO]?.takeIf { id -> list.any { it.serverId == id } } ?: list.firstOrNull()?.serverId
    }
    suspend fun setUploadTo(serverId: String) = store.edit { it[UPLOAD_TO] = serverId }

    /**
     * The list, after moving a phone paired before several libraries were possible over to
     * it, with its place in the catalog. A phone whose catalog predates shelves and genres
     * reads it from the start once.
     */
    private fun decodeConnections(prefs: Preferences): List<Connection> {
        prefs[CONNECTIONS]?.let { json ->
            return runCatching { Protocol.json.decodeFromString(LIST, json) }.getOrDefault(emptyList())
        }
        return listOfNotNull(prefs[OLD_CONNECTION]?.let { runCatching { Protocol.json.decodeFromString(Connection.serializer(), it) }.getOrNull() })
    }

    /** Moves the old single-library settings into the list; run once at start. */
    suspend fun migrate() = store.edit { prefs -> migrate(prefs) }

    private fun encode(list: List<Connection>) = Protocol.json.encodeToString(LIST, list)

    companion object {
        const val DEFAULT_STORAGE_LIMIT = 2L * 1024 * 1024 * 1024

        private val LIST = ListSerializer(Connection.serializer())
        private val CONNECTIONS = stringPreferencesKey("connections")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val STORAGE_LIMIT = longPreferencesKey("storage_limit")
        private val SHARE_LOCAL = booleanPreferencesKey("share_local")
        private val UPLOAD_TO = stringPreferencesKey("upload_to")

        internal fun cursorKey(serverId: String) = longPreferencesKey("cursor:$serverId")
        private fun lastSyncKey(serverId: String) = longPreferencesKey("last_sync:$serverId")

        // From when the phone had one library.
        internal val OLD_CONNECTION = stringPreferencesKey("connection")
        internal val OLD_CATALOG_CURSOR = longPreferencesKey("catalog_cursor")
        internal val OLD_CATALOG_VERSION = intPreferencesKey("catalog_version")
        private val OLD_LAST_SYNC = longPreferencesKey("last_sync")
        internal val OLD_KEEP_ALL = booleanPreferencesKey("keep_all")

        internal fun connectionsIn(prefs: Preferences): List<Connection> =
            prefs[CONNECTIONS]?.let { runCatching { Protocol.json.decodeFromString(LIST, it) }.getOrDefault(emptyList()) } ?: emptyList()

        /** The single-library settings of an older version, moved into the list; nothing happens twice. */
        internal fun migrate(prefs: MutablePreferences) {
            if (prefs[CONNECTIONS] != null) return
            fun encode(list: List<Connection>) = Protocol.json.encodeToString(LIST, list)
            val old = prefs[OLD_CONNECTION]?.let { runCatching { Protocol.json.decodeFromString(Connection.serializer(), it) }.getOrNull() }
            prefs[CONNECTIONS] = encode(listOfNotNull(old))
            if (old != null) {
                val current = (prefs[OLD_CATALOG_VERSION] ?: 1) >= 2
                if (current) prefs[OLD_CATALOG_CURSOR]?.let { prefs[cursorKey(old.serverId)] = it }
                prefs[OLD_LAST_SYNC]?.let { prefs[lastSyncKey(old.serverId)] = it }
                prefs[OLD_KEEP_ALL]?.let { keep -> if (keep) prefs[CONNECTIONS] = encode(listOf(old.copy(keepAll = true))) }
            }
            listOf(OLD_CONNECTION, OLD_CATALOG_CURSOR, OLD_LAST_SYNC).forEach { prefs.remove(it) }
            prefs.remove(OLD_CATALOG_VERSION)
            prefs.remove(OLD_KEEP_ALL)
        }
    }
}

private val Context.syncStore by preferencesDataStore(name = "private_library")
