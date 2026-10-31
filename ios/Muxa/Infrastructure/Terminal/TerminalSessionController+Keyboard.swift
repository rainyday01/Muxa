import Foundation

enum TerminalKeyboardModifier: String, CaseIterable, Hashable, Sendable {
    case ctrl
    case shift
    case alt
}

extension TerminalSessionController {
    func setKeyboardLayout(_ layout: KeyboardLayout) {
        keyboardLayout = layout
        refreshOrderedBar()
        syncExpandedKeyboardActions()
    }

    func setBuiltInKeyboardLayout(_ kind: BuiltInKeyboardLayout) {
        setKeyboardLayout(.builtIn(kind))
    }

    var expandedKeyboardActions: [TerminalShortcutAction] {
        keyboardLayout.expandedKeys.map(TerminalShortcutAction.init)
    }

    @discardableResult
    func applyKeyAction(_ action: KeyAction) -> [UInt8] {
        defer { refreshModifierIndicators() }
        switch action.type {
        case .modifierLatch:
            toggleModifier(named: action.modifier)
            return []
        case .key:
            let bytes = encodeKeyCode(action.keyCode ?? "", modifiers: latchedModifiers)
            return sendEncoded(bytes)
        case .text:
            let bytes = encodeText(action.text ?? "", modifiers: latchedModifiers)
            return sendEncoded(bytes)
        case .chord:
            let bytes = encodeChord(action.keys ?? [], modifiers: latchedModifiers)
            return sendEncoded(bytes)
        }
    }

    private func toggleModifier(named value: String?) {
        guard let modifier = modifier(named: value) else { return }
        if latchedModifiers.contains(modifier) {
            latchedModifiers.remove(modifier)
        } else {
            latchedModifiers.insert(modifier)
        }
    }

    private func sendEncoded(_ bytes: [UInt8]) -> [UInt8] {
        guard !bytes.isEmpty else { return [] }
        send(Data(bytes))
        latchedModifiers.removeAll()
        return bytes
    }

    private func encodeChord(
        _ tokens: [String],
        modifiers: Set<TerminalKeyboardModifier>
    ) -> [UInt8] {
        var pendingModifiers = modifiers
        var result = [UInt8]()
        for token in tokens {
            if let modifier = modifier(named: token) {
                pendingModifiers.insert(modifier)
                continue
            }
            result.append(contentsOf: encodeKeyCode(token, modifiers: pendingModifiers))
            pendingModifiers.removeAll()
        }
        return result
    }

    private func encodeText(
        _ text: String,
        modifiers: Set<TerminalKeyboardModifier>
    ) -> [UInt8] {
        guard !text.isEmpty else { return [] }
        var bytes = Array(text.utf8)
        if modifiers.contains(.shift), !modifiers.contains(.ctrl), bytes.count == 1 {
            // One-shot sticky Shift: IME letters/digits/punctuation use the US map.
            let mapped = Self.applyUsShift(Int(bytes[0]))
            if mapped != Int(bytes[0]) {
                bytes = [UInt8(mapped)]
            }
        }
        guard modifiers.contains(.ctrl) else {
            return modifiers.contains(.alt) ? [0x1B] + bytes : bytes
        }
        guard bytes.count == 1 else { return modifiers.contains(.alt) ? [0x1B] + bytes : bytes }
        let control = controlByte(for: bytes[0])
        return modifiers.contains(.alt) ? [0x1B, control] : [control]
    }

    private func encodeKeyCode(
        _ rawKeyCode: String,
        modifiers: Set<TerminalKeyboardModifier>
    ) -> [UInt8] {
        let keyCode = rawKeyCode.lowercased()
        if keyCode.hasPrefix("ctrl-") {
            let value = String(keyCode.dropFirst(5))
            return encodeText(value, modifiers: [.ctrl])
        }
        if keyCode.hasPrefix("alt-") {
            let value = String(keyCode.dropFirst(4))
            return encodeText(value, modifiers: [.alt])
        }

        let base = Self.keySequences[keyCode] ?? Array(keyCode.utf8)
        var result = base
        // One-shot sticky Shift: Tab → Backtab, Enter → LF, arrows/Home/End → CSI 1;2.
        let shifted = modifiers.contains(.shift) && !modifiers.contains(.ctrl)
            ? Self.shiftedSequence(for: base)
            : nil
        if let shifted {
            result = shifted
        } else if modifiers.contains(.ctrl), base.count == 1 {
            result = [controlByte(for: base[0])]
        }
        if modifiers.contains(.alt) {
            result.insert(0x1B, at: 0)
        }
        return result
    }

    /// One-shot Shift applied to bytes already resolved from a bar/expanded key
    /// (Tab → Backtab, Enter → LF, arrows → CSI 1;2, letters/digits → US Shift map).
    static func applyShiftLatch(_ data: [UInt8]) -> [UInt8] {
        shiftedSequence(for: data) ?? data
    }

    /// US keyboard Shift map for IME letters/digits/punctuation.
    static func applyUsShift(_ codePoint: Int) -> Int {
        if (97 ... 122).contains(codePoint) {
            return codePoint - 32
        }
        guard let byte = UInt8(exactly: codePoint), let mapped = usShiftMap[byte] else {
            return codePoint
        }
        return Int(mapped)
    }

    private static func shiftedSequence(for bytes: [UInt8]) -> [UInt8]? {
        if bytes == [0x09] {
            return [0x1B, 0x5B, 0x5A] // Tab → Backtab (ESC [ Z)
        }
        if bytes == [0x0D] {
            return [0x0A] // Enter → LF
        }
        if bytes.count == 3, bytes[0] == 0x1B, bytes[1] == UInt8(ascii: "[") {
            if let scalar = Unicode.Scalar(UInt32(bytes[2])), "ABCDHF".contains(Character(scalar)) {
                return [0x1B, 0x5B, UInt8(ascii: "1"), UInt8(ascii: ";"), UInt8(ascii: "2"), bytes[2]]
            }
        }
        if bytes.count == 1 {
            let mapped = applyUsShift(Int(bytes[0]))
            if mapped != Int(bytes[0]) {
                return [UInt8(mapped)]
            }
        }
        return nil
    }

    private static let usShiftMap: [UInt8: UInt8] = [
        UInt8(ascii: "`"): UInt8(ascii: "~"),
        UInt8(ascii: "1"): UInt8(ascii: "!"),
        UInt8(ascii: "2"): UInt8(ascii: "@"),
        UInt8(ascii: "3"): UInt8(ascii: "#"),
        UInt8(ascii: "4"): UInt8(ascii: "$"),
        UInt8(ascii: "5"): UInt8(ascii: "%"),
        UInt8(ascii: "6"): UInt8(ascii: "^"),
        UInt8(ascii: "7"): UInt8(ascii: "&"),
        UInt8(ascii: "8"): UInt8(ascii: "*"),
        UInt8(ascii: "9"): UInt8(ascii: "("),
        UInt8(ascii: "0"): UInt8(ascii: ")"),
        UInt8(ascii: "-"): UInt8(ascii: "_"),
        UInt8(ascii: "="): UInt8(ascii: "+"),
        UInt8(ascii: "["): UInt8(ascii: "{"),
        UInt8(ascii: "]"): UInt8(ascii: "}"),
        UInt8(ascii: "\\"): UInt8(ascii: "|"),
        UInt8(ascii: ";"): UInt8(ascii: ":"),
        UInt8(ascii: "'"): UInt8(ascii: "\""),
        UInt8(ascii: ","): UInt8(ascii: "<"),
        UInt8(ascii: "."): UInt8(ascii: ">"),
        UInt8(ascii: "/"): UInt8(ascii: "?")
    ]

    private static let keySequences: [String: [UInt8]] = [
        "escape": [0x1B],
        "esc": [0x1B],
        "tab": [0x09],
        "enter": [0x0D],
        "return": [0x0D],
        "backspace": [0x7F],
        "delete": [0x1B, 0x5B, 0x33, 0x7E],
        "delete-forward": [0x1B, 0x5B, 0x33, 0x7E],
        "space": [0x20],
        "up": [0x1B, 0x5B, 0x41],
        "down": [0x1B, 0x5B, 0x42],
        "right": [0x1B, 0x5B, 0x43],
        "left": [0x1B, 0x5B, 0x44],
        "home": [0x1B, 0x5B, 0x48],
        "end": [0x1B, 0x5B, 0x46],
        "page-up": [0x1B, 0x5B, 0x35, 0x7E],
        "pageup": [0x1B, 0x5B, 0x35, 0x7E],
        "page-down": [0x1B, 0x5B, 0x36, 0x7E],
        "pagedown": [0x1B, 0x5B, 0x36, 0x7E],
        "f1": [0x1B, 0x4F, 0x50],
        "f2": [0x1B, 0x4F, 0x51],
        "f3": [0x1B, 0x4F, 0x52],
        "f4": [0x1B, 0x4F, 0x53],
        "ctrl-c": [0x03],
        "ctrl-d": [0x04],
        "ctrl-z": [0x1A],
        "ctrl-r": [0x12],
        "ctrl-u": [0x15],
        "ctrl-w": [0x17],
        "ctrl-l": [0x0C]
    ]

    private func controlByte(for byte: UInt8) -> UInt8 {
        switch byte {
        case 0x40 ... 0x5F:
            byte &- 0x40
        case 0x60 ... 0x7E:
            byte &- 0x60
        case 0x20:
            0x00
        case 0x3F:
            0x7F
        default:
            byte
        }
    }

    private func modifier(named value: String?) -> TerminalKeyboardModifier? {
        guard let value else { return nil }
        switch value.lowercased() {
        case "ctrl", "control", "^":
            return .ctrl
        case "shift":
            return .shift
        case "alt", "meta", "option", "esc":
            return .alt
        default:
            return nil
        }
    }
}
