import Foundation

struct TmuxOutputParser: Sendable {
    func sessions(
        _ output: String,
        hostID: String,
        hostAlias: String? = nil,
        socketPath: String? = nil
    ) -> [TmuxSession] {
        output.lines.compactMap { line -> TmuxSession? in
            let fields = line.fields
            guard fields.count >= 2 else { return nil }
            let name = fields[0]
            guard !name.isEmpty else { return nil }
            return TmuxSession(
                hostID: hostID,
                name: name,
                windowCount: Int(fields[1]) ?? 0,
                attached: Self.isActive(fields[safe: 2]),
                createdAt: Self.date(fields[safe: 3]),
                lastActivityAt: Self.date(fields[safe: 4]),
                snapshotText: nil,
                snapshotUpdatedAt: nil,
                hostAlias: hostAlias,
                socketPath: Self.optional(fields[safe: 5]) ?? socketPath,
                windows: []
            )
        }
    }

    func windows(_ output: String, target: TmuxAttachTarget) -> [TmuxWindow] {
        output.lines.compactMap { line -> TmuxWindow? in
            let fields = line.fields
            guard fields.count >= 5, let index = Int(fields[0]) else { return nil }
            return TmuxWindow(
                sessionName: target.sessionName,
                index: index,
                name: fields[1],
                active: Self.isActive(fields[safe: 2]),
                layout: Self.optional(fields[safe: 3]),
                paneCount: Int(fields[4]) ?? 0,
                panes: []
            )
        }
    }

    func panes(
        _ output: String,
        target: TmuxAttachTarget,
        windowIndex: Int
    ) -> [TmuxPane] {
        output.lines.compactMap { line -> TmuxPane? in
            let fields = line.fields
            guard fields.count >= 8, let index = Int(fields[0]) else { return nil }
            return TmuxPane(
                sessionName: target.sessionName,
                windowIndex: windowIndex,
                index: index,
                active: Self.isActive(fields[safe: 1]),
                title: Self.optional(fields[safe: 2]),
                currentCommand: Self.optional(fields[safe: 3]),
                currentPath: Self.optional(fields[safe: 4]),
                width: Int(fields[5]),
                height: Int(fields[6]),
                processID: Self.processID(fields[7])
            )
        }
    }

    private static func isActive(_ field: String?) -> Bool {
        guard let field else { return false }
        return field == "1" || field == "*" || field.caseInsensitiveCompare("true") == .orderedSame
    }

    private static func date(_ field: String?) -> Date? {
        guard let field, let seconds = TimeInterval(field), seconds > 0 else { return nil }
        return Date(timeIntervalSince1970: seconds)
    }

    private static func optional(_ field: String?) -> String? {
        guard let field, !field.isEmpty else { return nil }
        return field
    }

    private static func processID(_ field: String) -> Int32? {
        guard let value = Int32(field), value > 0 else { return nil }
        return value
    }
}

private extension String {
    var lines: [String] {
        split(whereSeparator: \.isNewline).map(String.init)
    }

    var fields: [String] {
        split(separator: "\t", omittingEmptySubsequences: false).map {
            String($0).trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
}

private extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
