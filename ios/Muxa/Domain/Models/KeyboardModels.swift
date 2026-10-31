import Foundation

struct KeyboardLayout: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var name: String
    var version: Int
    var barKeys: [KeyboardKey]
    var expandedKeys: [KeyboardKey]
    var builtin: Bool
    var sortOrder: Int

    init(
        id: String,
        name: String,
        version: Int = 1,
        barKeys: [KeyboardKey] = [],
        expandedKeys: [KeyboardKey] = [],
        builtin: Bool = false,
        sortOrder: Int = 0
    ) {
        self.id = id
        self.name = name
        self.version = version
        self.barKeys = barKeys
        self.expandedKeys = expandedKeys
        self.builtin = builtin
        self.sortOrder = sortOrder
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        version = try container.decodeIfPresent(Int.self, forKey: .version) ?? 1
        barKeys = try container.decodeIfPresent([KeyboardKey].self, forKey: .barKeys) ?? []
        expandedKeys = try container.decodeIfPresent([KeyboardKey].self, forKey: .expandedKeys) ?? []
        builtin = try container.decodeIfPresent(Bool.self, forKey: .builtin) ?? false
        sortOrder = try container.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
    }
}

struct KeyboardKey: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var label: String
    var action: KeyAction
}

struct KeyAction: Codable, Equatable, Sendable {
    var type: KeyActionType
    var keyCode: String?
    var text: String?
    var keys: [String]?
    var modifier: String?
}

enum KeyActionType: String, Codable, CaseIterable, Hashable, Sendable {
    case key
    case text
    case chord
    case modifierLatch = "modifier_latch"
}

enum BuiltInKeyboardLayout: String, CaseIterable, Codable, Sendable {
    case general
    case tmux
    case cli

    var title: String {
        switch self {
        case .general:
            "General"
        case .tmux:
            "tmux"
        case .cli:
            "CLI"
        }
    }
}

extension KeyboardLayout {
    static let general = KeyboardLayout(
        id: BuiltInKeyboardLayout.general.rawValue,
        name: BuiltInKeyboardLayout.general.title,
        version: 4,
        barKeys: [
            keyboardKey("escape", "Esc", key: "escape"),
            keyboardKey("enter", "Enter", key: "enter"),
            keyboardKey("ctrl", "Ctrl", modifier: "ctrl"),
            keyboardKey("tab", "Tab", key: "tab"),
            keyboardKey("up", "↑", key: "up"),
            keyboardKey("down", "↓", key: "down"),
            keyboardKey("left", "←", key: "left"),
            keyboardKey("right", "→", key: "right"),
            keyboardKey("ctrl-c", "Ctrl-C", key: "ctrl-c"),
            keyboardKey("shift", "Shift", modifier: "shift"),
            keyboardKey("alt", "Alt", modifier: "alt"),
            keyboardKey("ctrl-x", "Ctrl-X", key: "ctrl-x"),
            keyboardKey("stab", "⇧Tab", chord: ["Shift", "Tab"]),
            keyboardKey("pipe", "|", text: "|"),
            keyboardKey("slash", "/", text: "/"),
            keyboardKey("dash", "-", text: "-"),
            keyboardKey("page-up", "PgUp", key: "page-up"),
            keyboardKey("page-down", "PgDn", key: "page-down"),
            keyboardKey("home", "Home", key: "home"),
            keyboardKey("end", "End", key: "end")
        ],
        expandedKeys: [
            // Ctrl-C/Ctrl-X plus Shift-Tab / Ctrl-J used by Claude Code, Codex,
            // and Grok TUIs instead of rarely used Ctrl combos.
            keyboardKey("ctrl-c", "Ctrl-C", key: "ctrl-c"),
            keyboardKey("ctrl-x", "Ctrl-X", key: "ctrl-x"),
            keyboardKey("stab", "⇧Tab", chord: ["Shift", "Tab"]),
            keyboardKey("ctrl-j", "Ctrl-J", chord: ["Control", "j"]),
            keyboardKey("ctrl-l", "Ctrl-L", key: "ctrl-l"),
            keyboardKey("backspace", "⌫", key: "backspace"),
            keyboardKey("enter", "Enter", key: "enter")
        ],
        builtin: true
    )

    static let tmux = KeyboardLayout(
        id: BuiltInKeyboardLayout.tmux.rawValue,
        name: BuiltInKeyboardLayout.tmux.title,
        version: 4,
        barKeys: [
            keyboardKey("enter", "Enter", key: "enter"),
            keyboardKey("prefix", "Prefix", chord: ["ctrl", "b"]),
            keyboardKey("new-window", "New", chord: ["ctrl", "b", "c"]),
            keyboardKey("next-window", "Next", chord: ["ctrl", "b", "n"]),
            keyboardKey("previous-window", "Prev", chord: ["ctrl", "b", "p"]),
            keyboardKey("detach", "Detach", chord: ["ctrl", "b", "d"]),
            keyboardKey("copy-mode", "Copy", chord: ["ctrl", "b", "["]),
            keyboardKey("paste-buffer", "Paste", chord: ["ctrl", "b", "]"]),
            keyboardKey("ctrl", "Ctrl", modifier: "ctrl"),
            keyboardKey("shift", "Shift", modifier: "shift"),
            keyboardKey("up", "↑", key: "up"),
            keyboardKey("down", "↓", key: "down"),
            keyboardKey("left", "←", key: "left"),
            keyboardKey("right", "→", key: "right"),
            keyboardKey("tab", "Tab", key: "tab"),
            keyboardKey("escape", "Esc", key: "escape"),
            keyboardKey("ctrl-c", "Ctrl-C", key: "ctrl-c")
        ],
        expandedKeys: [
            keyboardKey("split-horizontal", "Split H", chord: ["ctrl", "b", "%"]),
            keyboardKey("split-vertical", "Split V", chord: ["ctrl", "b", "\""]),
            keyboardKey("zoom", "Zoom", chord: ["ctrl", "b", "z"]),
            keyboardKey("kill-pane", "Kill", chord: ["ctrl", "b", "x"]),
            keyboardKey("stab", "⇧Tab", chord: ["Shift", "Tab"]),
            keyboardKey("escape", "Esc", key: "escape"),
            keyboardKey("ctrl-c", "Ctrl-C", key: "ctrl-c"),
            keyboardKey("ctrl-x", "Ctrl-X", key: "ctrl-x")
        ],
        builtin: true
    )

    static let cli = KeyboardLayout(
        id: BuiltInKeyboardLayout.cli.rawValue,
        name: BuiltInKeyboardLayout.cli.title,
        version: 4,
        barKeys: [
            keyboardKey("escape", "Esc", key: "escape"),
            keyboardKey("enter", "Enter", key: "enter"),
            keyboardKey("ctrl", "Ctrl", modifier: "ctrl"),
            keyboardKey("tab", "Tab", key: "tab"),
            keyboardKey("ctrl-c", "Ctrl-C", key: "ctrl-c"),
            keyboardKey("up", "↑", key: "up"),
            keyboardKey("down", "↓", key: "down"),
            keyboardKey("left", "←", key: "left"),
            keyboardKey("right", "→", key: "right"),
            keyboardKey("shift", "Shift", modifier: "shift"),
            keyboardKey("ctrl-x", "Ctrl-X", key: "ctrl-x"),
            keyboardKey("stab", "⇧Tab", chord: ["Shift", "Tab"]),
            keyboardKey("ctrl-j", "Ctrl-J", chord: ["Control", "j"]),
            keyboardKey("ctrl-d", "Ctrl-D", key: "ctrl-d"),
            keyboardKey("ctrl-r", "Ctrl-R", key: "ctrl-r"),
            keyboardKey("pipe", "|", text: "|"),
            keyboardKey("slash", "/", text: "/"),
            keyboardKey("dash", "-", text: "-")
        ],
        expandedKeys: [
            // ⇧Tab (completion) and Ctrl-J (newline) for Claude Code / Codex /
            // Grok TUIs instead of the rarely used Ctrl-A/E/U/W combos.
            keyboardKey("ctrl-x", "Ctrl-X", key: "ctrl-x"),
            keyboardKey("stab", "⇧Tab", chord: ["Shift", "Tab"]),
            keyboardKey("ctrl-j", "Ctrl-J", chord: ["Control", "j"]),
            keyboardKey("ctrl-l", "Ctrl-L", key: "ctrl-l"),
            keyboardKey("home", "Home", key: "home"),
            keyboardKey("end", "End", key: "end"),
            keyboardKey("enter", "Enter", key: "enter")
        ],
        builtin: true
    )

    static let builtIns = [general, tmux, cli]

    static func builtIn(_ kind: BuiltInKeyboardLayout) -> KeyboardLayout {
        switch kind {
        case .general:
            general
        case .tmux:
            tmux
        case .cli:
            cli
        }
    }
}

private func keyboardKey(
    _ id: String,
    _ label: String,
    key: String? = nil,
    text: String? = nil,
    chord: [String]? = nil,
    modifier: String? = nil
) -> KeyboardKey {
    let type: KeyActionType = if modifier != nil {
        .modifierLatch
    } else if chord != nil {
        .chord
    } else if text != nil {
        .text
    } else {
        .key
    }
    return KeyboardKey(
        id: id,
        label: label,
        action: KeyAction(
            type: type,
            keyCode: key,
            text: text,
            keys: chord,
            modifier: modifier
        )
    )
}
