package app.mtmux.autocomplete

/**
 * Follows the shell line being typed from the bytes sent to the PTY, so
 * completion has a prefix to match. The remote line editor is the source of
 * truth; once the cursor moves (arrows, Ctrl-A, Tab completion, Alt keys) the
 * local copy is unreliable and stays empty until the line is submitted or
 * cleared.
 */
class InputLineTracker {

    private val buf = StringBuilder()
    /** Cursor left the end of the line: we no longer know its contents. */
    private var lost = false
    private var state = State.Normal
    private val csi = StringBuilder()
    private var inPaste = false
    /** Pending bytes of a UTF-8 sequence split across writes. */
    private var pending = ByteArray(0)

    private enum class State { Normal, Esc, Csi, Ss3 }

    /** Current line, or empty while the tracker has lost track of it. */
    val line: String get() = if (lost) "" else buf.toString()

    fun reset() {
        buf.setLength(0)
        lost = false
        state = State.Normal
        csi.setLength(0)
        inPaste = false
        pending = ByteArray(0)
    }

    /** @return the line that was submitted with Enter in [bytes], if any (blank lines excluded). */
    fun feed(bytes: ByteArray): String? {
        val data = if (pending.isEmpty()) bytes else pending + bytes
        val complete = completeUtf8Length(data)
        pending = data.copyOfRange(complete, data.size)
        var submitted: String? = null
        val text = String(data, 0, complete, Charsets.UTF_8)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            submitted = step(cp) ?: submitted
        }
        return submitted
    }

    private fun step(cp: Int): String? {
        when (state) {
            State.Esc -> {
                state = when (cp) {
                    '['.code -> State.Csi.also { csi.setLength(0) }
                    'O'.code -> State.Ss3
                    else -> State.Normal.also { lose() } // Alt+key
                }
                return null
            }
            State.Csi -> {
                if (cp in 0x40..0x7E) {
                    state = State.Normal
                    val seq = csi.toString() + cp.toChar()
                    when (seq) {
                        "200~" -> inPaste = true
                        "201~" -> inPaste = false
                        else -> lose() // arrows, Home/End, Delete…
                    }
                } else if (csi.length < 16) {
                    csi.appendCodePoint(cp)
                }
                return null
            }
            State.Ss3 -> {
                state = State.Normal
                lose()
                return null
            }
            State.Normal -> Unit
        }
        if (cp == 0x1B) {
            state = State.Esc
            return null
        }
        if (inPaste) {
            if (cp == '\r'.code || cp == '\n'.code) lose() else append(cp)
            return null
        }
        return when (cp) {
            '\r'.code, '\n'.code -> submit()
            0x7F, 0x08 -> { if (!lost) dropLast(); null }
            0x03, 0x15 -> { clear(); null } // Ctrl-C, Ctrl-U
            else -> {
                if (cp < 0x20) lose() else append(cp)
                null
            }
        }
    }

    private fun submit(): String? {
        val out = if (lost) null else buf.toString().trim().takeIf { it.isNotEmpty() }
        clear()
        return out
    }

    private fun clear() {
        buf.setLength(0)
        lost = false
    }

    private fun lose() {
        buf.setLength(0)
        lost = true
    }

    private fun append(cp: Int) {
        if (lost) return
        if (buf.length >= MAX_LINE) {
            lose()
            return
        }
        buf.appendCodePoint(cp)
    }

    private fun dropLast() {
        if (buf.isEmpty()) return
        val cp = buf.codePointBefore(buf.length)
        buf.setLength(buf.length - Character.charCount(cp))
    }

    private companion object {
        const val MAX_LINE = 500

        /** Length of [data] that ends on a whole UTF-8 sequence. */
        fun completeUtf8Length(data: ByteArray): Int {
            var i = data.size - 1
            var back = 0
            while (i >= 0 && back < 4) {
                val b = data[i].toInt() and 0xFF
                if (b and 0xC0 != 0x80) {
                    val need = when {
                        b and 0x80 == 0 -> 1
                        b and 0xE0 == 0xC0 -> 2
                        b and 0xF0 == 0xE0 -> 3
                        b and 0xF8 == 0xF0 -> 4
                        else -> 1
                    }
                    return if (data.size - i >= need) data.size else i
                }
                i--
                back++
            }
            return data.size
        }
    }
}
