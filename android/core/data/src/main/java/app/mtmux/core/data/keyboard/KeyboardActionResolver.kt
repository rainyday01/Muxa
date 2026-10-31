package app.mtmux.core.data.keyboard

import app.mtmux.core.model.KeyAction
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey

/**
 * Resolves schema actions into terminal I/O effects.
 */
sealed class ResolvedKeyAction {
    data object LatchCtrl : ResolvedKeyAction()
    data object LatchShift : ResolvedKeyAction()
    data object LatchAlt : ResolvedKeyAction() // reserved; no-op for now
    data class SendBytes(val data: ByteArray) : ResolvedKeyAction() {
        override fun equals(other: Any?): Boolean =
            other is SendBytes && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }

    data object None : ResolvedKeyAction()
}

object KeyboardActionResolver {

    fun resolve(key: KeyboardKey): ResolvedKeyAction = resolve(key.action)

    fun resolve(action: KeyAction): ResolvedKeyAction = when (action.type) {
        KeyActionType.MODIFIER_LATCH -> when (action.modifier?.lowercase()) {
            "ctrl", "control" -> ResolvedKeyAction.LatchCtrl
            "shift" -> ResolvedKeyAction.LatchShift
            "alt" -> ResolvedKeyAction.LatchAlt
            else -> ResolvedKeyAction.None
        }
        KeyActionType.TEXT -> {
            val t = action.text.orEmpty()
            if (t.isEmpty()) ResolvedKeyAction.None
            else ResolvedKeyAction.SendBytes(t.toByteArray(Charsets.UTF_8))
        }
        KeyActionType.KEY -> {
            val bytes = keyCodeToBytes(action.keyCode.orEmpty())
            if (bytes == null) ResolvedKeyAction.None
            else ResolvedKeyAction.SendBytes(bytes)
        }
        KeyActionType.CHORD -> {
            val keys = action.keys.orEmpty()
            if (keys.isEmpty()) ResolvedKeyAction.None
            else ResolvedKeyAction.SendBytes(chordToBytes(keys))
        }
    }

    fun keyCodeToBytes(code: String): ByteArray? = when (code) {
        "Escape", "Esc" -> "\u001B".toByteArray()
        "Tab" -> "\t".toByteArray()
        "Enter", "Return" -> "\r".toByteArray() // CR is what most PTYs expect for Enter
        "Backspace" -> "\u007F".toByteArray()
        "Delete", "Del" -> "\u001B[3~".toByteArray()
        "Insert", "Ins" -> "\u001B[2~".toByteArray()
        "ArrowUp", "Up" -> "\u001B[A".toByteArray()
        "ArrowDown", "Down" -> "\u001B[B".toByteArray()
        "ArrowRight", "Right" -> "\u001B[C".toByteArray()
        "ArrowLeft", "Left" -> "\u001B[D".toByteArray()
        "Home" -> "\u001B[H".toByteArray()
        "End" -> "\u001B[F".toByteArray()
        "PageUp", "PgUp", "Pg Up" -> "\u001B[5~".toByteArray()
        "PageDown", "PgDn", "Pg Dn" -> "\u001B[6~".toByteArray()
        "Space" -> " ".toByteArray()
        else -> if (code.length == 1) code.toByteArray(Charsets.UTF_8) else null
    }

    /**
     * Chord rules:
     * - ["Control","c"] → Ctrl-C
     * - ["Control","b","c"] → Ctrl-B then 'c' (tmux prefix)
     * - bare char sequences concatenate
     */
    fun chordToBytes(keys: List<String>): ByteArray {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < keys.size) {
            val k = keys[i]
            val lower = k.lowercase()
            if (lower == "control" || lower == "ctrl") {
                val next = keys.getOrNull(i + 1) ?: break
                if (next.length == 1 && next[0].isLetter()) {
                    val c = next.lowercase()[0]
                    out.add((c.code - 96).toByte())
                    i += 2
                    continue
                }
                i += 1
                continue
            }
            if (lower == "shift") {
                val next = keys.getOrNull(i + 1) ?: break
                val shifted = shiftModifiedBytes(next)
                if (shifted != null) {
                    out.addAll(shifted.toList())
                    i += 2
                    continue
                }
                i += 1
                continue
            }
            if (lower == "alt" || lower == "meta") {
                val next = keys.getOrNull(i + 1) ?: break
                out.add(0x1B)
                out.addAll(next.toByteArray(Charsets.UTF_8).toList())
                i += 2
                continue
            }
            keyCodeToBytes(k)?.let { out.addAll(it.toList()) }
                ?: out.addAll(k.toByteArray(Charsets.UTF_8).toList())
            i++
        }
        return out.toByteArray()
    }

    /**
     * One-shot Shift applied to bytes already resolved from a bar/expanded key
     * (Tab → Backtab, Enter → LF, arrows → CSI 1;2, letters → uppercase).
     */
    fun applyShiftLatch(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        if (data.contentEquals(byteArrayOf(0x09))) return "\u001B[Z".toByteArray()
        if (data.contentEquals(byteArrayOf(0x0D))) return byteArrayOf(0x0A)
        if (data.size == 3 && data[0] == 0x1B.toByte() && data[1] == '['.code.toByte()) {
            val last = data[2].toInt().toChar()
            if (last in "ABCDHF") return "\u001B[1;2$last".toByteArray()
        }
        if (data.size == 1) {
            val mapped = applyUsShift(data[0].toInt() and 0xFF)
            if (mapped != (data[0].toInt() and 0xFF)) return byteArrayOf(mapped.toByte())
        }
        return data
    }

    /** US keyboard Shift map for IME letters/digits/punctuation. */
    fun applyUsShift(codePoint: Int): Int {
        if (codePoint in 'a'.code..'z'.code) return codePoint - 32
        return US_SHIFT[codePoint] ?: Character.toUpperCase(codePoint)
    }

    private fun shiftModifiedBytes(next: String): ByteArray? {
        if (next.equals("Tab", ignoreCase = true)) return "\u001B[Z".toByteArray()
        if (next.equals("Enter", ignoreCase = true) || next.equals("Return", ignoreCase = true)) {
            return byteArrayOf(0x0A)
        }
        val arrow = when (next.lowercase()) {
            "arrowup", "up" -> 'A'
            "arrowdown", "down" -> 'B'
            "arrowright", "right" -> 'C'
            "arrowleft", "left" -> 'D'
            "home" -> 'H'
            "end" -> 'F'
            else -> null
        }
        if (arrow != null) return "\u001B[1;2$arrow".toByteArray()
        if (next.length == 1) {
            val mapped = applyUsShift(next[0].code)
            return byteArrayOf(mapped.toByte())
        }
        return null
    }

    private val US_SHIFT: Map<Int, Int> = mapOf(
        '`'.code to '~'.code,
        '1'.code to '!'.code, '2'.code to '@'.code, '3'.code to '#'.code,
        '4'.code to '$'.code, '5'.code to '%'.code, '6'.code to '^'.code,
        '7'.code to '&'.code, '8'.code to '*'.code, '9'.code to '('.code,
        '0'.code to ')'.code,
        '-'.code to '_'.code, '='.code to '+'.code,
        '['.code to '{'.code, ']'.code to '}'.code, '\\'.code to '|'.code,
        ';'.code to ':'.code, '\''.code to '"'.code,
        ','.code to '<'.code, '.'.code to '>'.code, '/'.code to '?'.code,
    )
}
