package app.mtmux.mosh.transport

import app.mtmux.mosh.net.Fragment
import app.mtmux.mosh.proto.ProtoLite
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** TransportBuffers.Instruction fields */
data class TransportInstruction(
    val protocolVersion: Int = 2,
    val oldNum: Long = 0,
    val newNum: Long = 0,
    val ackNum: Long = 0,
    val throwawayNum: Long = 0,
    val diff: ByteArray = ByteArray(0),
    val chaff: ByteArray = ByteArray(0),
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        ProtoLite.writeUint32(out, 1, protocolVersion)
        ProtoLite.writeUint64(out, 2, oldNum)
        ProtoLite.writeUint64(out, 3, newNum)
        ProtoLite.writeUint64(out, 4, ackNum)
        ProtoLite.writeUint64(out, 5, throwawayNum)
        ProtoLite.writeBytes(out, 6, diff)
        ProtoLite.writeBytes(out, 7, chaff)
        return out.toByteArray()
    }

    companion object {
        fun parse(raw: ByteArray): TransportInstruction {
            val m = ProtoLite.parseMap(raw)
            fun u64(field: Int): Long =
                m[field]?.firstOrNull()?.let { ProtoLite.varintValue(it) } ?: 0L
            fun bytes(field: Int): ByteArray =
                m[field]?.firstOrNull { it.wire == 2 }?.value ?: ByteArray(0)
            return TransportInstruction(
                protocolVersion = u64(1).toInt(),
                oldNum = u64(2),
                newNum = u64(3),
                ackNum = u64(4),
                throwawayNum = u64(5),
                diff = bytes(6),
                chaff = bytes(7),
            )
        }
    }
}

/** HostBuffers.HostMessage → list of hoststring chunks + optional resize */
data class HostUpdate(
    val hostBytes: List<ByteArray> = emptyList(),
    val resize: Pair<Int, Int>? = null,
    val echoAck: Long? = null,
)

object HostMessageParser {
    fun parse(diff: ByteArray): HostUpdate {
        if (diff.isEmpty()) return HostUpdate()
        val root = ProtoLite.parseMap(diff)
        val hostChunks = mutableListOf<ByteArray>()
        var resize: Pair<Int, Int>? = null
        var echoAck: Long? = null
        // field 1 = repeated Instruction
        root[1]?.forEach { instField ->
            if (instField.wire != 2) return@forEach
            val inst = ProtoLite.parseMap(instField.value)
            // extension hostbytes = 2 → HostBytes { hoststring = 4 }
            inst[2]?.firstOrNull { it.wire == 2 }?.let { hb ->
                val inner = ProtoLite.parseMap(hb.value)
                inner[4]?.firstOrNull { it.wire == 2 }?.value?.let { hostChunks.add(it) }
            }
            // resize = 3 → width=5 height=6
            inst[3]?.firstOrNull { it.wire == 2 }?.let { rz ->
                val inner = ProtoLite.parseMap(rz.value)
                val w = inner[5]?.firstOrNull()?.let { ProtoLite.varintValue(it).toInt() } ?: 0
                val h = inner[6]?.firstOrNull()?.let { ProtoLite.varintValue(it).toInt() } ?: 0
                if (w > 0 && h > 0) resize = w to h
            }
            // echoack = 7 → echo_ack_num = 8
            inst[7]?.firstOrNull { it.wire == 2 }?.let { ea ->
                val inner = ProtoLite.parseMap(ea.value)
                echoAck = inner[8]?.firstOrNull()?.let { ProtoLite.varintValue(it) }
            }
        }
        return HostUpdate(hostChunks, resize, echoAck)
    }
}

object UserMessageBuilder {
    /** Build ClientBuffers.UserMessage with a single keystroke instruction. */
    fun keystrokes(keys: ByteArray): ByteArray {
        // Keystroke { keys = 4 }
        val keystroke = ByteArrayOutputStream().also {
            ProtoLite.writeBytes(it, 4, keys)
        }.toByteArray()
        // Instruction { keystroke extension = 2 }
        val instruction = ByteArrayOutputStream().also {
            ProtoLite.writeMessage(it, 2, keystroke)
        }.toByteArray()
        // UserMessage { instruction = 1 }
        return ByteArrayOutputStream().also {
            ProtoLite.writeMessage(it, 1, instruction)
        }.toByteArray()
    }

    fun resize(width: Int, height: Int): ByteArray {
        val rz = ByteArrayOutputStream().also {
            ProtoLite.writeUint32(it, 5, width)
            ProtoLite.writeUint32(it, 6, height)
        }.toByteArray()
        val instruction = ByteArrayOutputStream().also {
            ProtoLite.writeMessage(it, 3, rz)
        }.toByteArray()
        return ByteArrayOutputStream().also {
            ProtoLite.writeMessage(it, 1, instruction)
        }.toByteArray()
    }
}

object ZlibUtil {
    fun compress(input: ByteArray): ByteArray {
        val def = Deflater()
        def.setInput(input)
        def.finish()
        val buf = ByteArray(64 * 1024)
        val out = ByteArrayOutputStream()
        while (!def.finished()) {
            val n = def.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        def.end()
        return out.toByteArray()
    }

    fun uncompress(input: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(input)
        val buf = ByteArray(128 * 1024)
        val out = ByteArrayOutputStream()
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) {
                    if (inf.needsInput()) break
                } else {
                    out.write(buf, 0, n)
                }
            }
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }
}

/** Reassemble mosh fragments into a TransportInstruction. */
class FragmentAssembly {
    private var currentId: Long = -1
    private val fragments = mutableMapOf<Int, Fragment>()
    private var fragmentsTotal = -1

    /** @return assembled instruction when complete, else null */
    fun add(frag: Fragment): TransportInstruction? {
        if (currentId != frag.id) {
            fragments.clear()
            fragmentsTotal = -1
            currentId = frag.id
        }
        fragments[frag.fragmentNum] = frag
        if (frag.final) {
            fragmentsTotal = frag.fragmentNum + 1
        }
        if (fragmentsTotal < 0 || fragments.size < fragmentsTotal) return null
        // ensure contiguous
        for (i in 0 until fragmentsTotal) {
            if (!fragments.containsKey(i)) return null
        }
        val encoded = ByteArrayOutputStream()
        for (i in 0 until fragmentsTotal) {
            encoded.write(fragments[i]!!.contents)
        }
        fragments.clear()
        fragmentsTotal = -1
        currentId = -1
        val raw = ZlibUtil.uncompress(encoded.toByteArray())
        return TransportInstruction.parse(raw)
    }
}

class Fragmenter {
    private var nextId = 0L
    private var lastInst: TransportInstruction? = null

    fun makeFragments(inst: TransportInstruction, mtu: Int): List<Fragment> {
        val header = Fragment.HEADER_LEN
        val bodyMtu = (mtu - header).coerceAtLeast(64)
        if (lastInst == null ||
            lastInst!!.oldNum != inst.oldNum ||
            lastInst!!.newNum != inst.newNum ||
            lastInst!!.ackNum != inst.ackNum ||
            lastInst!!.throwawayNum != inst.throwawayNum ||
            !lastInst!!.chaff.contentEquals(inst.chaff) ||
            lastInst!!.protocolVersion != inst.protocolVersion
        ) {
            nextId++
        }
        lastInst = inst
        var payload = ZlibUtil.compress(inst.serialize())
        val id = nextId
        val out = mutableListOf<Fragment>()
        var fragNum = 0
        while (payload.isNotEmpty()) {
            val take = minOf(bodyMtu, payload.size)
            val chunk = payload.copyOfRange(0, take)
            payload = payload.copyOfRange(take, payload.size)
            val final = payload.isEmpty()
            out.add(Fragment(id, fragNum, final, chunk))
            fragNum++
        }
        if (out.isEmpty()) {
            out.add(Fragment(id, 0, true, ByteArray(0)))
        }
        return out
    }
}
