package app.mtmux.mosh.proto

import java.io.ByteArrayOutputStream

/**
 * Minimal proto2 wire codec for the mosh messages we need.
 * Avoids a full protobuf runtime dependency.
 */
object ProtoLite {
    fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    fun writeTag(out: ByteArrayOutputStream, field: Int, wire: Int) {
        writeVarint(out, ((field.toLong() shl 3) or wire.toLong()))
    }

    fun writeUint64(out: ByteArrayOutputStream, field: Int, value: Long) {
        if (value == 0L) return // optional default
        writeTag(out, field, 0)
        writeVarint(out, value)
    }

    fun writeUint32(out: ByteArrayOutputStream, field: Int, value: Int) {
        if (value == 0) return
        writeTag(out, field, 0)
        writeVarint(out, value.toLong() and 0xFFFFFFFFL)
    }

    fun writeBytes(out: ByteArrayOutputStream, field: Int, data: ByteArray) {
        if (data.isEmpty()) return
        writeTag(out, field, 2)
        writeVarint(out, data.size.toLong())
        out.write(data)
    }

    fun writeMessage(out: ByteArrayOutputStream, field: Int, data: ByteArray) {
        writeBytes(out, field, data)
    }

    fun readVarint(buf: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = offset
        while (i < buf.size) {
            val b = buf[i].toInt() and 0xFF
            i++
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result to i
            shift += 7
            if (shift > 63) error("varint too long")
        }
        error("truncated varint")
    }

    data class Field(val number: Int, val wire: Int, val value: ByteArray, val next: Int)

    fun readField(buf: ByteArray, offset: Int): Field? {
        if (offset >= buf.size) return null
        val (key, afterKey) = readVarint(buf, offset)
        val number = (key ushr 3).toInt()
        val wire = (key and 7).toInt()
        return when (wire) {
            0 -> {
                val (v, next) = readVarint(buf, afterKey)
                // store varint as 8-byte big-endian for convenience
                val bytes = ByteArray(8)
                var x = v
                for (i in 7 downTo 0) {
                    bytes[i] = (x and 0xFF).toByte()
                    x = x ushr 8
                }
                Field(number, wire, bytes, next)
            }
            2 -> {
                val (len, afterLen) = readVarint(buf, afterKey)
                val end = afterLen + len.toInt()
                require(end <= buf.size) { "truncated length-delimited" }
                Field(number, wire, buf.copyOfRange(afterLen, end), end)
            }
            5 -> {
                require(afterKey + 4 <= buf.size)
                Field(number, wire, buf.copyOfRange(afterKey, afterKey + 4), afterKey + 4)
            }
            1 -> {
                require(afterKey + 8 <= buf.size)
                Field(number, wire, buf.copyOfRange(afterKey, afterKey + 8), afterKey + 8)
            }
            else -> error("unsupported wire type $wire")
        }
    }

    fun varintValue(field: Field): Long {
        require(field.wire == 0)
        var v = 0L
        for (b in field.value) {
            v = (v shl 8) or (b.toLong() and 0xFF)
        }
        return v
    }

    fun parseMap(buf: ByteArray): Map<Int, List<Field>> {
        val map = linkedMapOf<Int, MutableList<Field>>()
        var i = 0
        while (i < buf.size) {
            val f = readField(buf, i) ?: break
            map.getOrPut(f.number) { mutableListOf() }.add(f)
            i = f.next
        }
        return map
    }
}
