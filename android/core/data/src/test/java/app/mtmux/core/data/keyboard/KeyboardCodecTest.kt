package app.mtmux.core.data.keyboard

import app.mtmux.core.model.KeyActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-131: keyboard layout schema / codec tests (docs/schemas/keyboard-layout.schema.json).
 */
class KeyboardCodecTest {
    @Test
    fun roundTripGeneral() {
        val json = KeyboardCodec.toJson(BuiltinKeyboards.general)
        val parsed = KeyboardCodec.fromJson(json).getOrThrow()
        assertEquals("general", parsed.id)
        assertEquals(BuiltinKeyboards.general.barKeys.size, parsed.barKeys.size)
        assertTrue(parsed.barKeys.any { it.action.modifier == "ctrl" })
        assertTrue(parsed.barKeys.any { it.action.modifier == "shift" })
    }

    @Test
    fun roundTripAllBuiltins() {
        for (layout in BuiltinKeyboards.all()) {
            val parsed = KeyboardCodec.fromJson(KeyboardCodec.toJson(layout)).getOrThrow()
            assertEquals(layout.id, parsed.id)
            assertEquals(layout.name, parsed.name)
            assertEquals(layout.barKeys.size, parsed.barKeys.size)
            assertEquals(layout.expandedKeys.size, parsed.expandedKeys.size)
            assertFalse("imported layouts are not marked builtin", parsed.builtin)
        }
    }

    @Test
    fun protocolJsonGeneralCompatible() {
        // mirrors protocols/keyboards/general.json shape (subset actions)
        val raw = """
            {
              "id": "general",
              "name": "General",
              "version": 1,
              "barKeys": [
                { "id": "esc", "label": "Esc", "action": { "type": "key", "keyCode": "Escape" } },
                { "id": "ctrl", "label": "Ctrl", "action": { "type": "modifier_latch", "modifier": "ctrl" } }
              ],
              "expandedKeys": [
                { "id": "ctrl_c", "label": "Ctrl-C", "action": { "type": "chord", "keys": ["Control", "c"] } }
              ]
            }
        """.trimIndent()
        val p = KeyboardCodec.fromJson(raw).getOrThrow()
        assertEquals(2, p.barKeys.size)
        assertEquals(KeyActionType.CHORD, p.expandedKeys.single().action.type)
    }

    @Test
    fun forceIdOnImport() {
        val raw = """{"id":"old","name":"N","version":1,"barKeys":[{"id":"a","label":"A","action":{"type":"text","text":"a"}}]}"""
        val p = KeyboardCodec.fromJson(raw, forceId = "custom1").getOrThrow()
        assertEquals("custom1", p.id)
    }

    @Test
    fun chordBytesTmuxNewWindow() {
        val bytes = KeyboardActionResolver.chordToBytes(listOf("Control", "b", "c"))
        assertEquals(2, bytes.size)
        assertEquals(2.toByte(), bytes[0]) // Ctrl-B
        assertEquals('c'.code.toByte(), bytes[1])
    }

    @Test
    fun importRejectsBadId() {
        val r = KeyboardCodec.fromJson("""{"id":"bad id!","name":"x","version":1,"barKeys":[]}""")
        assertTrue(r.isFailure)
    }

    @Test
    fun importRejectsEmptyKeys() {
        val r = KeyboardCodec.fromJson("""{"id":"empty","name":"x","version":1,"barKeys":[]}""")
        assertTrue(r.isFailure)
    }

    @Test
    fun importRejectsUnknownActionType() {
        val r = KeyboardCodec.fromJson(
            """{"id":"x","name":"x","version":1,"barKeys":[{"id":"a","label":"A","action":{"type":"nope"}}]}""",
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun compactJsonRoundTrip() {
        val json = KeyboardCodec.toJson(BuiltinKeyboards.tmux, pretty = false)
        assertFalse(json.contains("\n"))
        val p = KeyboardCodec.fromJson(json).getOrThrow()
        assertEquals("tmux", p.id)
        assertTrue(p.barKeys.isNotEmpty())
    }

    @Test
    fun chordBytesShiftTabIsBacktab() {
        val bytes = KeyboardActionResolver.chordToBytes(listOf("Shift", "Tab"))
        assertEquals("\u001B[Z", bytes.toString(Charsets.US_ASCII))
    }

    @Test
    fun chordBytesCtrlJIsNewline() {
        val bytes = KeyboardActionResolver.chordToBytes(listOf("Control", "j"))
        assertEquals(1, bytes.size)
        assertEquals(10.toByte(), bytes[0])
    }

    @Test
    fun applyShiftLatchTabAndDigit() {
        val tab = KeyboardActionResolver.applyShiftLatch(byteArrayOf(0x09))
        assertEquals("\u001B[Z", tab.toString(Charsets.US_ASCII))
        assertEquals('!'.code, KeyboardActionResolver.applyUsShift('1'.code))
        assertEquals('A'.code, KeyboardActionResolver.applyUsShift('a'.code))
    }

    @Test
    fun resolveLatchShift() {
        val key = BuiltinKeyboards.general.barKeys.first { it.id == "shift" }
        assertEquals(ResolvedKeyAction.LatchShift, KeyboardActionResolver.resolve(key))
    }
}
