package app.mtmux.mosh.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class MoshDirection { TO_SERVER, TO_CLIENT }

/**
 * Plaintext payload layout after decrypt (mosh Network::Packet):
 *   uint16 timestamp | uint16 timestamp_reply | fragment bytes
 */
data class MoshPacket(
    val seq: Long,
    val direction: MoshDirection,
    val timestamp: Int,
    val timestampReply: Int,
    val payload: ByteArray,
) {
    fun toPlaintext(): ByteArray {
        val buf = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(timestamp.toShort())
        buf.putShort(timestampReply.toShort())
        buf.put(payload)
        return buf.array()
    }

    /** Nonce value: direction bit 63 | sequence */
    fun nonceValue(): Long {
        val dirBit = if (direction == MoshDirection.TO_CLIENT) 1L shl 63 else 0L
        return dirBit or (seq and SEQUENCE_MASK)
    }

    companion object {
        const val SEQUENCE_MASK = 0x7FFFFFFFFFFFFFFFL

        fun fromPlaintext(nonceVal: Long, text: ByteArray): MoshPacket {
            require(text.size >= 4) { "packet too short" }
            val seq = nonceVal and SEQUENCE_MASK
            val direction =
                if (nonceVal and (1L shl 63) != 0L) MoshDirection.TO_CLIENT else MoshDirection.TO_SERVER
            val buf = ByteBuffer.wrap(text).order(ByteOrder.BIG_ENDIAN)
            val ts = buf.short.toInt() and 0xFFFF
            val tsr = buf.short.toInt() and 0xFFFF
            val payload = ByteArray(text.size - 4)
            buf.get(payload)
            return MoshPacket(seq, direction, ts, tsr, payload)
        }
    }
}

/** Fragment header: uint64 id | uint16 (final<<15 | frag_num) | contents */
data class Fragment(
    val id: Long,
    val fragmentNum: Int,
    val final: Boolean,
    val contents: ByteArray,
) {
    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(10 + contents.size).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(id)
        val combined = ((if (final) 0x8000 else 0) or (fragmentNum and 0x7FFF)).toShort()
        buf.putShort(combined)
        buf.put(contents)
        return buf.array()
    }

    companion object {
        const val HEADER_LEN = 10

        fun parse(raw: ByteArray): Fragment {
            require(raw.size >= HEADER_LEN)
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
            val id = buf.long
            val combined = buf.short.toInt() and 0xFFFF
            val final = (combined and 0x8000) != 0
            val fragNum = combined and 0x7FFF
            val contents = ByteArray(raw.size - HEADER_LEN)
            buf.get(contents)
            return Fragment(id, fragNum, final, contents)
        }
    }
}
