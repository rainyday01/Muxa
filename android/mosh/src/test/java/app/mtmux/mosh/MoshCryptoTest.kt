package app.mtmux.mosh

import app.mtmux.mosh.crypto.MoshCrypto
import app.mtmux.mosh.net.Fragment
import app.mtmux.mosh.net.MoshDirection
import app.mtmux.mosh.net.MoshPacket
import app.mtmux.mosh.transport.FragmentAssembly
import app.mtmux.mosh.transport.Fragmenter
import app.mtmux.mosh.transport.HostMessageParser
import app.mtmux.mosh.transport.TransportInstruction
import app.mtmux.mosh.transport.UserMessageBuilder
import app.mtmux.mosh.transport.ZlibUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoshCryptoTest {
    @Test
    fun keyFromPrintableRoundtripLength() {
        // 16 zero bytes → known base64
        val key = ByteArray(16)
        val crypto = MoshCrypto(key)
        val plain = "hello mosh".toByteArray()
        val wire = crypto.encrypt(1L, plain)
        val (nonce, dec) = crypto.decrypt(wire)
        assertEquals(1L, nonce)
        assertArrayEquals(plain, dec)
    }

    @Test
    fun packetRoundtrip() {
        val p = MoshPacket(
            seq = 42,
            direction = MoshDirection.TO_SERVER,
            timestamp = 100,
            timestampReply = 200,
            payload = byteArrayOf(1, 2, 3),
        )
        val plain = p.toPlaintext()
        val parsed = MoshPacket.fromPlaintext(p.nonceValue(), plain)
        assertEquals(42L, parsed.seq)
        assertEquals(MoshDirection.TO_SERVER, parsed.direction)
        assertArrayEquals(byteArrayOf(1, 2, 3), parsed.payload)
    }

    @Test
    fun fragmentAssembly() {
        val inst = TransportInstruction(
            protocolVersion = 2,
            oldNum = 0,
            newNum = 1,
            ackNum = 0,
            diff = UserMessageBuilder.keystrokes("a".toByteArray()),
        )
        val frags = Fragmenter().makeFragments(inst, 120)
        assertTrue(frags.isNotEmpty())
        val asm = FragmentAssembly()
        var result: TransportInstruction? = null
        for (f in frags) {
            result = asm.add(f) ?: result
        }
        assertTrue(result != null)
        assertEquals(2, result!!.protocolVersion)
        assertEquals(1L, result.newNum)
    }

    @Test
    fun zlibRoundtrip() {
        val data = "x".repeat(500).toByteArray()
        val c = ZlibUtil.compress(data)
        assertArrayEquals(data, ZlibUtil.uncompress(c))
    }

    @Test
    fun hostMessageEmpty() {
        val u = HostMessageParser.parse(ByteArray(0))
        assertTrue(u.hostBytes.isEmpty())
    }

    @Test
    fun fragmentHeader() {
        val f = Fragment(99, 0, true, byteArrayOf(7, 8))
        val parsed = Fragment.parse(f.toBytes())
        assertEquals(99L, parsed.id)
        assertEquals(0, parsed.fragmentNum)
        assertTrue(parsed.final)
        assertArrayEquals(byteArrayOf(7, 8), parsed.contents)
    }
}
