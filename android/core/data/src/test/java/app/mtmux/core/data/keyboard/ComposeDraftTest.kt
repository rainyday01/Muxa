package app.mtmux.core.data.keyboard

import app.mtmux.core.model.ComposeHistory
import app.mtmux.core.model.KeyAction
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.model.ShortcutOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeDraftTest {
    @Test
    fun historyKeepsTenNewestDistinct() {
        var items = emptyList<String>()
        for (n in 1..12) {
            items = ComposeHistory.remember(items, "cmd$n")
        }
        items = ComposeHistory.remember(items, "cmd12")
        assertEquals(10, items.size)
        assertEquals("cmd12", items.first())
        assertEquals("cmd3", items.last())
    }

    @Test
    fun historyRoundTripKeepsNewlines() {
        val items = ComposeHistory.remember(emptyList(), "echo a\necho b")
        assertEquals(items, ComposeHistory.decode(ComposeHistory.encode(items)))
    }

    @Test
    fun payloadStaysInTheInputLine() {
        assertEquals("ls", ComposeHistory.payload("ls"))
        assertEquals("ls", ComposeHistory.payload("ls\n"))
        assertEquals("ls", ComposeHistory.payload("ls\r"))
        assertEquals("echo a\necho b", ComposeHistory.payload("echo a\r\necho b\n"))
    }

    @Test
    fun orderFollowsSavedIdsAndAppendsNewKeys() {
        val keys = listOf(key("esc"), key("enter"), key("tab"))
        val ordered = ShortcutOrder.apply(keys, listOf("enter", "missing", "esc"))
        assertEquals(listOf("enter", "esc", "tab"), ordered.map { it.id })
    }

    @Test
    fun moveSwapsNeighbors() {
        val keys = listOf(key("esc"), key("enter"), key("tab"))
        val moved = ShortcutOrder.move(keys, emptyList(), index = 2, delta = -2)
        assertEquals(listOf("tab", "esc", "enter"), moved)
    }

    private fun key(id: String) = KeyboardKey(
        id = id,
        label = id,
        action = KeyAction(type = KeyActionType.KEY, keyCode = id),
    )
}
