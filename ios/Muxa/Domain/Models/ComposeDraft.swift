import Foundation

enum ComposeHistory {
    static let limit = 10

    static func remember(_ existing: [String], text: String) -> [String] {
        let trimmed = text.replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return existing }
        return Array(([trimmed] + existing.filter { $0 != trimmed }).prefix(limit))
    }

    /// Text for the remote input line. No trailing CR: one Enter submits.
    static func payload(_ text: String) -> String {
        var body = text.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        while body.hasSuffix("\n") {
            body.removeLast()
        }
        return body
    }

    static func encode(_ items: [String]) -> String {
        items.prefix(limit).joined(separator: "\u{1e}")
    }

    static func decode(_ raw: String?) -> [String] {
        guard let raw, !raw.isEmpty else { return [] }
        return Array(
            raw.components(separatedBy: "\u{1e}")
                .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .prefix(limit)
        )
    }
}

enum ShortcutOrder {
    static func apply<T>(_ keys: [T], order: [String], id: (T) -> String) -> [T] {
        guard !order.isEmpty, !keys.isEmpty else { return keys }
        var byID: [String: T] = [:]
        for key in keys {
            byID[id(key)] = key
        }
        var used = Set<String>()
        var ordered: [T] = []
        for keyID in order {
            guard let key = byID[keyID], used.insert(keyID).inserted else { continue }
            ordered.append(key)
        }
        for key in keys where used.insert(id(key)).inserted {
            ordered.append(key)
        }
        return ordered
    }

    static func move(_ ids: [String], index: Int, delta: Int) -> [String] {
        var items = ids
        let target = index + delta
        guard items.indices.contains(index), items.indices.contains(target) else { return items }
        let item = items.remove(at: index)
        items.insert(item, at: target)
        return items
    }
}
