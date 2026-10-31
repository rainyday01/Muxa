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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Optional live interop. Set:
 *   MT_MOSH_HOST=203.0.113.177 MT_MOSH_PORT=60001 MT_MOSH_KEY=...
 */
class MoshLiveInteropTest {
    @Test
    fun liveHandshakeAndReceive() {
        val host = System.getenv("MT_MOSH_HOST")
        val port = System.getenv("MT_MOSH_PORT")?.toIntOrNull()
        val keyPrint = System.getenv("MT_MOSH_KEY")
        Assume.assumeTrue("skip live", host != null && port != null && keyPrint != null)

        val crypto = MoshCrypto(MoshCrypto.keyFromPrintable(keyPrint!!))
        val socket = DatagramSocket()
        socket.soTimeout = 3000
        val addr = InetAddress.getByName(host)
        val seq = AtomicLong(1)
        val fragmenter = Fragmenter()
        val assembly = FragmentAssembly()

        // send resize + empty keystroke stream init
        fun send(inst: TransportInstruction) {
            for (f in fragmenter.makeFragments(inst, 500)) {
                val packet = MoshPacket(
                    seq = seq.getAndIncrement(),
                    direction = MoshDirection.TO_SERVER,
                    timestamp = (System.currentTimeMillis() and 0xFFFF).toInt(),
                    timestampReply = 0xFFFF,
                    payload = f.toBytes(),
                )
                val wire = crypto.encrypt(packet.nonceValue(), packet.toPlaintext())
                socket.send(DatagramPacket(wire, wire.size, addr, port!!))
            }
        }

        send(
            TransportInstruction(
                protocolVersion = 2,
                oldNum = 0,
                newNum = 1,
                ackNum = 0,
                throwawayNum = 0,
                diff = UserMessageBuilder.resize(80, 24),
            ),
        )

        val buf = ByteArray(2048)
        var gotHost = false
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline && !gotHost) {
            try {
                val dp = DatagramPacket(buf, buf.size)
                socket.receive(dp)
                val (nonce, plain) = crypto.decrypt(buf.copyOf(dp.length))
                val pkt = MoshPacket.fromPlaintext(nonce, plain)
                if (pkt.direction != MoshDirection.TO_CLIENT) continue
                val frag = Fragment.parse(pkt.payload)
                val inst = assembly.add(frag) ?: continue
                println("got inst new=${inst.newNum} old=${inst.oldNum} diffLen=${inst.diff.size}")
                val update = HostMessageParser.parse(inst.diff)
                if (update.hostBytes.isNotEmpty()) {
                    val text = update.hostBytes.joinToString("") { String(it) }
                    println("hosttext=[$text]")
                    gotHost = true
                }
                // ack
                send(
                    TransportInstruction(
                        protocolVersion = 2,
                        oldNum = 1,
                        newNum = 1,
                        ackNum = inst.newNum,
                        throwawayNum = 0,
                    ),
                )
            } catch (e: java.net.SocketTimeoutException) {
                // resend resize
                send(
                    TransportInstruction(
                        protocolVersion = 2,
                        oldNum = 0,
                        newNum = 1,
                        ackNum = 0,
                        throwawayNum = 0,
                        diff = UserMessageBuilder.resize(80, 24),
                    ),
                )
            }
        }
        socket.close()
        assertTrue("expected host bytes from mosh-server", gotHost)
    }
}
