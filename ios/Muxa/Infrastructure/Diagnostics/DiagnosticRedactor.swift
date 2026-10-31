import Foundation

enum DiagnosticRedactor {
    static func redact(message: String) -> String {
        patterns.reduce(message) { partial, pattern in
            pattern.expression.stringByReplacingMatches(
                in: partial,
                range: NSRange(partial.startIndex..., in: partial),
                withTemplate: pattern.replacement
            )
        }
    }

    static func redact(metadata: [String: String]) -> [String: String] {
        metadata.mapValues { redact(message: $0) }.mapValues { value in
            value.count > 2048 ? String(value.prefix(2048)) : value
        }.reduce(into: [:]) { result, pair in
            result[pair.key] = sensitiveKeys.contains(normalize(pair.key)) ? "<redacted>" : pair.value
        }
    }

    private static func normalize(_ key: String) -> String {
        key.lowercased().filter(\.isLetter)
    }

    private static let sensitiveKeys: Set<String> = [
        "password",
        "passphrase",
        "privatekey",
        "token",
        "secret",
        "authorization"
    ]

    private static let patterns: [(expression: NSRegularExpression, replacement: String)] = [
        (#"-----BEGIN [^-]*PRIVATE KEY-----[\s\S]*?-----END [^-]*PRIVATE KEY-----"#, "<redacted-private-key>"),
        (#"(?i)\b(password|passphrase|token|secret)\s*[:=]\s*[^\s,;]+"#, "$1=<redacted>"),
        (#"\bgh[pousr]_[A-Za-z0-9]{20,}\b"#, "<redacted-token>"),
        (#"\blh_(?:up|rd|ad)_[A-Za-z0-9_]+\b"#, "<redacted-token>"),
        (#"(?i)bearer\s+[A-Za-z0-9._\-]+"#, "Bearer <redacted>"),
        (#"(https?://[^:/\s]+:)[^@/\s]+@"#, "$1<redacted>@")
    ].compactMap { source, replacement in
        guard let expression = try? NSRegularExpression(pattern: source) else { return nil }
        return (expression, replacement)
    }
}
