package com.perform.reader.sync

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.perform.sync.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A phone paired before several libraries were possible keeps its library, place and choice. */
class SyncSettingsTest {

    private val pc = Connection("pc", "Study PC", 47474, "ab", listOf("192.168.1.2"), "token")

    @Test
    fun `the one library moves into the list with its place in the catalog`() {
        val prefs = mutablePreferencesOf()
        prefs[SyncSettings.OLD_CONNECTION] = Protocol.json.encodeToString(Connection.serializer(), pc)
        prefs[SyncSettings.OLD_CATALOG_CURSOR] = 42L
        prefs[SyncSettings.OLD_CATALOG_VERSION] = 2
        prefs[SyncSettings.OLD_KEEP_ALL] = true

        SyncSettings.migrate(prefs)

        val list = SyncSettings.connectionsIn(prefs)
        assertEquals(listOf("pc"), list.map { it.serverId })
        assertTrue("Keep every book carries over to that library", list.single().keepAll)
        assertEquals(42L, prefs[SyncSettings.cursorKey("pc")])
        assertNull(prefs[SyncSettings.OLD_CONNECTION])
    }

    @Test
    fun `a catalog from before shelves is read again from the start`() {
        val prefs = mutablePreferencesOf()
        prefs[SyncSettings.OLD_CONNECTION] = Protocol.json.encodeToString(Connection.serializer(), pc)
        prefs[SyncSettings.OLD_CATALOG_CURSOR] = 42L
        SyncSettings.migrate(prefs)
        assertNull(prefs[SyncSettings.cursorKey("pc")])
    }

    @Test
    fun `migrating twice, or with nothing paired, changes nothing`() {
        val empty = mutablePreferencesOf()
        SyncSettings.migrate(empty)
        assertTrue(SyncSettings.connectionsIn(empty).isEmpty())

        val prefs = mutablePreferencesOf()
        prefs[SyncSettings.OLD_CONNECTION] = Protocol.json.encodeToString(Connection.serializer(), pc)
        SyncSettings.migrate(prefs)
        // A second library paired since must survive a second run.
        val both = SyncSettings.connectionsIn(prefs) + pc.copy(serverId = "mac", serverName = "Mac")
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("connections")] =
            Protocol.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Connection.serializer()), both)
        SyncSettings.migrate(prefs)
        assertEquals(listOf("pc", "mac"), SyncSettings.connectionsIn(prefs).map { it.serverId })
    }
}
