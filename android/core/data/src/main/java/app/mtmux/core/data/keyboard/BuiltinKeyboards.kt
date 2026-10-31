package app.mtmux.core.data.keyboard

import app.mtmux.core.model.KeyAction
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.model.KeyboardLayout

/** Built-in layouts (Termius-inspired general layout + tmux/cli variants). */
object BuiltinKeyboards {

    fun all(): List<KeyboardLayout> = listOf(general, tmux, cli)

    fun byId(id: String): KeyboardLayout? = all().find { it.id == id }

    /**
     * Default layout. The bar scrolls horizontally; Enter sits near the front
     * and the user can reorder these ids. The expanded grid is the shortcut panel.
     */
    val general = KeyboardLayout(
        id = "general",
        name = "General",
        version = 4,
        builtin = true,
        barKeys = listOf(
            k("esc", "Esc", key("Escape")),
            k("enter", "Enter", key("Enter")),
            k("ctrl", "Ctrl", latch("ctrl")),
            k("tab", "Tab", key("Tab")),
            k("up", "↑", key("ArrowUp")),
            k("down", "↓", key("ArrowDown")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
            k("shift", "Shift", latch("shift")),
            k("ctrl_x", "Ctrl-X", chord("Control", "x")),
            k("stab", "⇧Tab", chord("Shift", "Tab")),
            k("slash", "/", text("/")),
            k("dash", "-", text("-")),
            k("pgup", "PgUp", key("PageUp")),
            k("pgdn", "PgDn", key("PageDown")),
            k("home", "Home", key("Home")),
            k("end", "End", key("End")),
        ),
        expandedKeys = listOf(
            k("esc", "Esc", key("Escape")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
            k("ctrl_x", "Ctrl-X", chord("Control", "x")),
            k("stab", "⇧Tab", chord("Shift", "Tab")),
            k("ctrl_j", "Ctrl-J", chord("Control", "j")),
            k("enter2", "Enter", key("Enter")),
            k("paste", "Paste", text("")),
            k("hide", "Hide", text("")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("home", "Home", key("Home")),
            k("end", "End", key("End")),
            k("pgup", "Pg Up", key("PageUp")),
            k("pgdn", "Pg Dn", key("PageDown")),
            k("del", "Delete", key("Delete")),
            k("ctrl_l", "Ctrl-L", chord("Control", "l")),
        ),
    )

    val tmux = KeyboardLayout(
        id = "tmux",
        name = "tmux",
        version = 4,
        builtin = true,
        barKeys = listOf(
            k("enter", "Enter", key("Enter")),
            k("ctrl", "Ctrl", latch("ctrl")),
            k("c", "c new", chord("Control", "b", "c")),
            k("n", "n next", chord("Control", "b", "n")),
            k("p", "p prev", chord("Control", "b", "p")),
            k("d", "d det", chord("Control", "b", "d")),
            k("up", "↑", key("ArrowUp")),
            k("down", "↓", key("ArrowDown")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("tab", "Tab", key("Tab")),
            k("shift", "Shift", latch("shift")),
            k("esc", "Esc", key("Escape")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
        ),
        expandedKeys = listOf(
            k("c", "c new", chord("Control", "b", "c")),
            k("n", "n next", chord("Control", "b", "n")),
            k("p", "p prev", chord("Control", "b", "p")),
            k("d", "d det", chord("Control", "b", "d")),
            k("pct", "% vsplit", chord("Control", "b", "%")),
            k("quot", "\" split", chord("Control", "b", "\"")),
            k("z", "z zoom", chord("Control", "b", "z")),
            k("x", "x kill", chord("Control", "b", "x")),
            k("stab", "⇧Tab", chord("Shift", "Tab")),
            k("esc", "Esc", key("Escape")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
            k("ctrl_x", "Ctrl-X", chord("Control", "x")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("home", "Home", key("Home")),
            k("end", "End", key("End")),
            k("pgup", "Pg Up", key("PageUp")),
            k("pgdn", "Pg Dn", key("PageDown")),
            k("paste", "Paste", text("")),
            k("enter2", "Enter", key("Enter")),
        ),
    )

    val cli = KeyboardLayout(
        id = "cli",
        name = "CLI Tools",
        version = 4,
        builtin = true,
        barKeys = listOf(
            k("esc", "Esc", key("Escape")),
            k("enter", "Enter", key("Enter")),
            k("ctrl", "Ctrl", latch("ctrl")),
            k("tab", "Tab", key("Tab")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
            k("up", "↑", key("ArrowUp")),
            k("down", "↓", key("ArrowDown")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("shift", "Shift", latch("shift")),
            k("ctrl_x", "Ctrl-X", chord("Control", "x")),
            k("stab", "⇧Tab", chord("Shift", "Tab")),
            k("ctrl_j", "Ctrl-J", chord("Control", "j")),
            k("slash", "/", text("/")),
            k("dash", "-", text("-")),
        ),
        expandedKeys = listOf(
            k("esc", "Esc", key("Escape")),
            k("ctrl_c", "Ctrl-C", chord("Control", "c")),
            k("ctrl_x", "Ctrl-X", chord("Control", "x")),
            k("stab", "⇧Tab", chord("Shift", "Tab")),
            k("ctrl_j", "Ctrl-J", chord("Control", "j")),
            k("ctrl_l", "Ctrl-L", chord("Control", "l")),
            k("ctrl_r", "Ctrl-R", chord("Control", "r")),
            k("enter2", "Enter", key("Enter")),
            k("left", "←", key("ArrowLeft")),
            k("right", "→", key("ArrowRight")),
            k("home", "Home", key("Home")),
            k("end", "End", key("End")),
            k("pgup", "Pg Up", key("PageUp")),
            k("pgdn", "Pg Dn", key("PageDown")),
            k("paste", "Paste", text("")),
            k("hide", "Hide", text("")),
        ),
    )

    private fun k(id: String, label: String, action: KeyAction) =
        KeyboardKey(id = id, label = label, action = action)

    private fun key(code: String) =
        KeyAction(type = KeyActionType.KEY, keyCode = code)

    private fun text(t: String) =
        KeyAction(type = KeyActionType.TEXT, text = t)

    private fun latch(mod: String) =
        KeyAction(type = KeyActionType.MODIFIER_LATCH, modifier = mod)

    private fun chord(vararg keys: String) =
        KeyAction(type = KeyActionType.CHORD, keys = keys.toList())
}
