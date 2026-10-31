package app.mtmux.core.data.keyboard

import app.mtmux.core.model.KeyAction
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.model.KeyboardLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for keyboard layouts (AND-052).
 * Compatible with docs/schemas/keyboard-layout.schema.json
 */
object KeyboardCodec {

    fun toJson(layout: KeyboardLayout, pretty: Boolean = true): String {
        val root = JSONObject()
        root.put("id", layout.id)
        root.put("name", layout.name)
        root.put("version", layout.version)
        root.put("barKeys", keysToArray(layout.barKeys))
        root.put("expandedKeys", keysToArray(layout.expandedKeys))
        return if (pretty) root.toString(2) else root.toString()
    }

    fun fromJson(raw: String, forceId: String? = null): Result<KeyboardLayout> = runCatching {
        val root = JSONObject(raw.trim())
        val id = (forceId ?: root.getString("id")).trim()
        require(id.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "id may only contain letters, digits, _ and -" }
        val name = root.optString("name", id)
        val version = root.optInt("version", 1).coerceAtLeast(1)
        val bar = parseKeys(root.getJSONArray("barKeys"))
        val expanded = if (root.has("expandedKeys")) {
            parseKeys(root.getJSONArray("expandedKeys"))
        } else {
            emptyList()
        }
        require(bar.isNotEmpty() || expanded.isNotEmpty()) { "at least one key is required" }
        KeyboardLayout(
            id = id.lowercase(),
            name = name,
            version = version,
            barKeys = bar,
            expandedKeys = expanded,
            builtin = false,
        )
    }

    private fun keysToArray(keys: List<KeyboardKey>): JSONArray {
        val arr = JSONArray()
        keys.forEach { key ->
            arr.put(
                JSONObject()
                    .put("id", key.id)
                    .put("label", key.label)
                    .put("action", actionToJson(key.action)),
            )
        }
        return arr
    }

    private fun actionToJson(a: KeyAction): JSONObject {
        val o = JSONObject()
        o.put(
            "type",
            when (a.type) {
                KeyActionType.KEY -> "key"
                KeyActionType.TEXT -> "text"
                KeyActionType.CHORD -> "chord"
                KeyActionType.MODIFIER_LATCH -> "modifier_latch"
            },
        )
        a.keyCode?.let { o.put("keyCode", it) }
        a.text?.let { o.put("text", it) }
        a.modifier?.let { o.put("modifier", it) }
        a.keys?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            o.put("keys", arr)
        }
        return o
    }

    private fun parseKeys(arr: JSONArray): List<KeyboardKey> {
        val out = ArrayList<KeyboardKey>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id", "k$i").ifBlank { "k$i" }
            val label = o.optString("label", id)
            val action = parseAction(o.getJSONObject("action"))
            out.add(KeyboardKey(id = id, label = label, action = action))
        }
        return out
    }

    private fun parseAction(o: JSONObject): KeyAction {
        val typeStr = o.getString("type")
        val type = when (typeStr) {
            "key" -> KeyActionType.KEY
            "text" -> KeyActionType.TEXT
            "chord" -> KeyActionType.CHORD
            "modifier_latch" -> KeyActionType.MODIFIER_LATCH
            else -> error("unknown action.type: $typeStr")
        }
        val keys = if (o.has("keys")) {
            val a = o.getJSONArray("keys")
            (0 until a.length()).map { a.getString(it) }
        } else {
            null
        }
        return KeyAction(
            type = type,
            keyCode = if (o.has("keyCode")) o.optString("keyCode").takeIf { it.isNotBlank() } else null,
            text = if (o.has("text")) o.optString("text").takeIf { it.isNotBlank() } else null,
            keys = keys,
            modifier = if (o.has("modifier")) o.optString("modifier").takeIf { it.isNotBlank() } else null,
        )
    }
}
