package com.preform.reader.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.preform.sync.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/** A paired PC: where it is, the one certificate to trust, and this phone's token. */
@Serializable
data class Connection(
    val serverId: String,
    val serverName: String,
    val port: Int,
    /** SHA-256 of the PC's certificate, hex. Nothing else is trusted. */
    val certSha256: String,
    /** Every address the PC gave, best first. */
    val addresses: List<String>,
    val token: String,
    /** The address that answered last time; tried first. */
    val lastGood: String? = null,
) {
    /** Last good address first, then the rest in the PC's order. */
    val addressOrder: List<String> get() = listOfNotNull(lastGood) + (addresses - setOfNotNull(lastGood))
}

/**
 * Private-library state, kept apart from the reading preferences: the token is a
 * credential, and none of it belongs in settings backups or exports.
 */
class SyncSettings(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.syncStore

    val connection: Flow<Connection?> = store.data.map { prefs ->
        prefs[CONNECTION]?.let { runCatching { Protocol.json.decodeFromString(Connection.serializer(), it) }.getOrNull() }
    }

    suspend fun connectionNow(): Connection? = connection.first()

    suspend fun setConnection(connection: Connection?) {
        store.edit { prefs ->
            if (connection == null) {
                prefs.remove(CONNECTION)
                // A new pairing starts from scratch.
                prefs.remove(CATALOG_CURSOR)
                prefs.remove(STATE_CURSOR)
                prefs.remove(LAST_SYNC)
            } else {
                prefs[CONNECTION] = Protocol.json.encodeToString(Connection.serializer(), connection)
            }
        }
    }

    suspend fun rememberGoodAddress(address: String) {
        val current = connectionNow() ?: return
        if (current.lastGood != address) setConnection(current.copy(lastGood = address))
    }

    /** This phone's id in every stamp it makes; made once, kept for good. */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        store.edit { if (it[DEVICE_ID] == null) it[DEVICE_ID] = fresh }
        return store.data.first()[DEVICE_ID]!!
    }

    suspend fun catalogCursor(): Long = store.data.first()[CATALOG_CURSOR] ?: 0L
    suspend fun setCatalogCursor(value: Long) = store.edit { it[CATALOG_CURSOR] = value }

    suspend fun stateCursor(): Long = store.data.first()[STATE_CURSOR] ?: 0L
    suspend fun setStateCursor(value: Long) = store.edit { it[STATE_CURSOR] = value }

    val lastSync: Flow<Long?> = store.data.map { it[LAST_SYNC] }
    suspend fun setLastSync(value: Long) = store.edit { it[LAST_SYNC] = value }

    /** Downloads beyond this are cleared, least recently read first. 0 means no limit. */
    val storageLimit: Flow<Long> = store.data.map { it[STORAGE_LIMIT] ?: DEFAULT_STORAGE_LIMIT }
    suspend fun setStorageLimit(bytes: Long) = store.edit { it[STORAGE_LIMIT] = bytes }

    companion object {
        const val DEFAULT_STORAGE_LIMIT = 2L * 1024 * 1024 * 1024

        private val CONNECTION = stringPreferencesKey("connection")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val CATALOG_CURSOR = longPreferencesKey("catalog_cursor")
        private val STATE_CURSOR = longPreferencesKey("state_cursor")
        private val LAST_SYNC = longPreferencesKey("last_sync")
        private val STORAGE_LIMIT = longPreferencesKey("storage_limit")
    }
}

private val Context.syncStore by preferencesDataStore(name = "private_library")
