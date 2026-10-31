package app.mtmux.core.model

/**
 * Edit-then-send buffer shared by the terminal shortcut bar.
 * The newest distinct draft is kept, capped at [LIMIT], so a failed send
 * can be pulled back into the box.
 */
object ComposeHistory {
    const val LIMIT = 10
    private const val SEPARATOR = "\u001e"

    fun remember(existing: List<String>, text: String): List<String> {
        val trimmed = text.replace("\r\n", "\n").trim()
        if (trimmed.isEmpty()) return existing
        return (listOf(trimmed) + existing.filter { it != trimmed }).take(LIMIT)
    }

    /**
     * Text for the remote input line. No trailing CR or newline: the draft
     * stays in the agent composer. One Enter submits it. A trailing newline
     * would make Claude treat the next Enter as another blank line.
     */
    fun payload(text: String): String {
        var body = text.replace("\r\n", "\n").replace('\r', '\n')
        while (body.endsWith("\n")) body = body.dropLast(1)
        return body
    }

    fun encode(items: List<String>): String = items.take(LIMIT).joinToString(SEPARATOR)

    fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() }?.take(LIMIT).orEmpty()
}

/** User order for the horizontally scrolling shortcut bar. Unknown keys stay at the end. */
object ShortcutOrder {
    fun apply(keys: List<KeyboardKey>, order: List<String>): List<KeyboardKey> {
        if (order.isEmpty() || keys.isEmpty()) return keys
        val byId = LinkedHashMap<String, KeyboardKey>(keys.size)
        keys.forEach { byId[it.id] = it }
        val used = HashSet<String>()
        val ordered = ArrayList<KeyboardKey>(keys.size)
        for (id in order) {
            val key = byId[id] ?: continue
            if (used.add(id)) ordered += key
        }
        keys.forEach { key ->
            if (used.add(key.id)) ordered += key
        }
        return ordered
    }

    fun move(keys: List<KeyboardKey>, order: List<String>, index: Int, delta: Int): List<String> {
        val ids = apply(keys, order).map { it.id }.toMutableList()
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return ids
        val item = ids.removeAt(index)
        ids.add(target, item)
        return ids
    }
}
