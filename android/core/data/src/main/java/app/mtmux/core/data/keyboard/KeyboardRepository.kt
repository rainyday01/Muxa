package app.mtmux.core.data.keyboard

import android.content.Context
import app.mtmux.core.data.R
import app.mtmux.core.model.locale.AppLocale
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mtmux.core.model.KeyboardLayout
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.keyboardStore by preferencesDataStore("mtmux_keyboards")

/**
 * Built-in + custom keyboard layouts (AND-051/052).
 * Custom layouts: filesDir/keyboards/<id>.json
 * Active id + index of custom ids in DataStore.
 */
class KeyboardRepository(private val context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "keyboards").also { it.mkdirs() }
    private val activeKey = stringPreferencesKey("active_layout_id")
    private val customIndexKey = stringPreferencesKey("custom_ids_json")

    val activeLayoutId: Flow<String> = app.keyboardStore.data.map { prefs ->
        prefs[activeKey] ?: "general"
    }

    fun observeAll(): Flow<List<KeyboardLayout>> =
        app.keyboardStore.data.map { prefs ->
            val customIds = parseIndex(prefs[customIndexKey])
            val custom = customIds.mapNotNull { loadCustom(it) }
            BuiltinKeyboards.all() + custom
        }

    suspend fun setActive(id: String) {
        app.keyboardStore.edit { it[activeKey] = id }
    }

    fun getActiveSync(activeId: String, all: List<KeyboardLayout>): KeyboardLayout =
        all.find { it.id == activeId }
            ?: BuiltinKeyboards.byId(activeId)
            ?: BuiltinKeyboards.general

    suspend fun saveCustom(layout: KeyboardLayout): Result<KeyboardLayout> = runCatching {
        require(!layout.builtin) { AppLocale.getString(app, R.string.kb_cannot_overwrite) }
        val sanitized = layout.copy(
            id = layout.id.lowercase().replace(Regex("[^a-z0-9_-]"), "-"),
            builtin = false,
        )
        require(BuiltinKeyboards.byId(sanitized.id) == null) {
            AppLocale.getString(app, R.string.kb_id_conflict, sanitized.id)
        }
        val json = KeyboardCodec.toJson(sanitized, pretty = true)
        File(dir, "${sanitized.id}.json").writeText(json)
        app.keyboardStore.edit { prefs ->
            val ids = parseIndex(prefs[customIndexKey]).toMutableList()
            if (sanitized.id !in ids) ids.add(sanitized.id)
            prefs[customIndexKey] = JSONArray(ids).toString()
        }
        sanitized
    }

    suspend fun deleteCustom(id: String): Result<Unit> = runCatching {
        require(BuiltinKeyboards.byId(id) == null) {
            AppLocale.getString(app, R.string.kb_cannot_delete_builtin)
        }
        File(dir, "$id.json").delete()
        app.keyboardStore.edit { prefs ->
            val ids = parseIndex(prefs[customIndexKey]).filter { it != id }
            prefs[customIndexKey] = JSONArray(ids).toString()
            if (prefs[activeKey] == id) prefs[activeKey] = "general"
        }
    }

    /** Duplicate any layout as a new custom one. */
    suspend fun duplicate(source: KeyboardLayout, newName: String? = null): Result<KeyboardLayout> {
        val id = "custom-" + UUID.randomUUID().toString().take(8)
        val copy = source.copy(
            id = id,
            name = newName ?: AppLocale.getString(app, R.string.kb_copy_name, source.name),
            builtin = false,
            version = source.version,
        )
        return saveCustom(copy)
    }

    fun exportJson(layout: KeyboardLayout): String = KeyboardCodec.toJson(layout, pretty = true)

    suspend fun importJson(raw: String): Result<KeyboardLayout> {
        val parsed = KeyboardCodec.fromJson(raw).getOrElse { return Result.failure(it) }
        // avoid clobbering builtin ids
        val id = if (BuiltinKeyboards.byId(parsed.id) != null) {
            "imported-" + parsed.id + "-" + UUID.randomUUID().toString().take(4)
        } else {
            parsed.id
        }
        return saveCustom(parsed.copy(id = id, builtin = false))
    }

    private fun loadCustom(id: String): KeyboardLayout? {
        val f = File(dir, "$id.json")
        if (!f.isFile) return null
        return KeyboardCodec.fromJson(f.readText()).getOrNull()?.copy(builtin = false)
    }

    private fun parseIndex(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }
}
