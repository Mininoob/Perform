package com.perform.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    private fun pos(p: Double, at: Long, by: String = "phone") = Stamped(Position("{}", p), at, by)

    @Test
    fun `the newest change to each group wins`() {
        val phone = BookState("a".repeat(64), position = pos(0.5, at = 200), bookMode = Stamped(false, 100, "phone"))
        val pc = BookState("a".repeat(64), position = pos(0.3, at = 100), bookMode = Stamped(true, 300, "fold"))

        val merged = phone.mergedWith(pc)

        assertEquals(0.5, merged.position!!.value.progression, 0.0)
        assertEquals(true, merged.bookMode!!.value)
    }

    @Test
    fun `merging is the same whichever side goes first`() {
        val a = BookState("b".repeat(64), position = pos(0.2, 10, "flip"), settings = Stamped("x", 50, "flip"))
        val b = BookState("b".repeat(64), position = pos(0.9, 20, "fold"), settings = Stamped(null, 40, "fold"))

        assertEquals(a.mergedWith(b), b.mergedWith(a))
    }

    @Test
    fun `a tie is broken by device id the same way everywhere`() {
        val x = pos(0.1, at = 100, by = "aaa")
        val y = pos(0.7, at = 100, by = "zzz")

        assertEquals(y, newest(x, y))
        assertEquals(y, newest(y, x))
    }

    @Test
    fun `a missing group never erases a present one`() {
        val withPosition = BookState("c".repeat(64), position = pos(0.4, 5))
        val empty = BookState("c".repeat(64))

        assertEquals(withPosition.position, empty.mergedWith(withPosition).position)
        assertNull(empty.mergedWith(empty).position)
    }

    @Test
    fun `last opened keeps the latest`() {
        val a = BookState("d".repeat(64), lastOpenedAt = 10)
        val b = BookState("d".repeat(64), lastOpenedAt = 30)
        assertEquals(30L, a.mergedWith(b).lastOpenedAt)
    }

    @Test
    fun `content ids are stable sha-256 hex`() {
        val id = contentIdOf("Pride and Prejudice".byteInputStream())
        assertEquals(id, contentIdOf("Pride and Prejudice".byteInputStream()))
        assertTrue(isContentId(id))
        assertFalse(isContentId("../../etc/passwd"))
        assertFalse(isContentId(id.uppercase()))
    }

    @Test
    fun `an invite survives the qr round trip and nothing else decodes`() {
        val invite = PairingInvite(
            serverId = "srv",
            serverName = "Study PC",
            port = Protocol.DEFAULT_PORT,
            certSha256 = "ab".repeat(32),
            code = "482913",
            addresses = listOf("192.168.1.36", "100.101.102.103"),
        )
        assertEquals(invite, PairingInvite.decode(invite.encode()))
        assertNull(PairingInvite.decode("https://example.com"))
        assertNull(PairingInvite.decode(Protocol.INVITE_SCHEME + "{not json"))
    }
}
