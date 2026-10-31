package app.mtmux.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Keyboard layout (protocols/keyboards + AND-051/052).
 * Matches docs/schemas/keyboard-layout.schema.json
 */
@Serializable
data class KeyboardLayout(
    val id: String,
    val name: String,
    val version: Int = 1,
    val barKeys: List<KeyboardKey> = emptyList(),
    val expandedKeys: List<KeyboardKey> = emptyList(),
    /** Builtin layouts shipped with the app; not deleted by user. */
    val builtin: Boolean = false,
)

@Serializable
data class KeyboardKey(
    val id: String,
    val label: String,
    val action: KeyAction,
)

@Serializable
data class KeyAction(
    val type: KeyActionType,
    val keyCode: String? = null,
    val text: String? = null,
    val keys: List<String>? = null,
    val modifier: String? = null,
)

@Serializable
enum class KeyActionType {
    @SerialName("key")
    KEY,

    @SerialName("text")
    TEXT,

    @SerialName("chord")
    CHORD,

    @SerialName("modifier_latch")
    MODIFIER_LATCH,
}
