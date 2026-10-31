import Darwin
import Foundation

struct ParsedHostInput: Equatable, Sendable {
    let host: String
    let username: String?
    let port: UInt16?
    let password: String?
    let corrections: [String]
}

enum HostFieldSanitizer {
    static func parse(_ rawValue: String) -> ParsedHostInput {
        var state = ParseState(value: normalizeInvisibleCharacters(rawValue))
        parseLines(into: &state)
        parseRemoteSyntax(into: &state)
        separateGluedPassword(in: &state)
        separateTrailingPassword(in: &state)
        return ParsedHostInput(
            host: state.value.trimmingCharacters(in: .whitespacesAndNewlines),
            username: state.username,
            port: state.port,
            password: state.password,
            corrections: state.corrections
        )
    }

    private static func parseLines(into state: inout ParseState) {
        let lines = state.value.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        if let firstLine = lines.first {
            state.value = firstLine
        }
        if lines.count >= 2, !isPlausibleHost(lines[1]), lines[1].count >= 3 {
            state.password = lines[1]
            state.corrections.append("password-from-second-line")
        }
        if lines.count > 2 {
            state.corrections.append("extra-lines-ignored")
        }
    }

    private static func parseRemoteSyntax(into state: inout ParseState) {
        if state.value.lowercased().hasPrefix("ssh://") {
            parseSSHURL(into: &state)
        } else {
            parsePlainRemoteSyntax(into: &state)
        }
    }

    private static func parseSSHURL(into state: inout ParseState) {
        guard let components = URLComponents(string: state.value), let urlHost = components.host else { return }
        state.value = strippingIPv6Brackets(urlHost)
        state.username = components.user?.removingPercentEncoding
        state.password = state.password ?? components.password?.removingPercentEncoding
        state.port = components.port.flatMap(UInt16.init(exactly:))
        state.corrections.append("ssh-url-parsed")
    }

    private static func parsePlainRemoteSyntax(into state: inout ParseState) {
        state.value = state.value.trimmingCharacters(in: .whitespacesAndNewlines)
        if state.value.hasPrefix("ssh ") {
            let command = parseSSHCommand(state.value)
            state.value = command.destination
            state.username = command.username
            state.port = command.port
            state.corrections.append("ssh-command-parsed")
        }
        state.value = state.value.split(separator: "/", maxSplits: 1).first.map(String.init) ?? state.value
        parseCredentials(into: &state)
        let endpoint = parseEndpoint(state.value)
        state.value = endpoint.host
        state.port = state.port ?? endpoint.port
        if endpoint.port != nil {
            state.corrections.append("port-parsed")
        }
    }

    private static func parseCredentials(into state: inout ParseState) {
        guard let atIndex = state.value.lastIndex(of: "@") else { return }
        let credentials = String(state.value[..<atIndex])
        state.value = String(state.value[state.value.index(after: atIndex)...])
        if let colonIndex = credentials.firstIndex(of: ":") {
            state.username = nonEmpty(String(credentials[..<colonIndex]))
            let passwordStart = credentials.index(after: colonIndex)
            state.password = state.password ?? nonEmpty(String(credentials[passwordStart...]))
        } else {
            state.username = nonEmpty(credentials)
        }
        state.corrections.append("credentials-parsed")
    }

    private static func separateGluedPassword(in state: inout ParseState) {
        guard let match = state.value.wholeMatch(of: /^(\d{1,3}(?:\.\d{1,3}){3})([^.\d\s]\S*)$/) else {
            return
        }
        state.value = String(match.1)
        state.password = state.password ?? String(match.2)
        state.corrections.append("glued-password-separated")
    }

    private static func separateTrailingPassword(in state: inout ParseState) {
        let whitespaceParts = state.value.split(whereSeparator: \.isWhitespace).map(String.init)
        if let first = whitespaceParts.first {
            state.value = first
        }
        if whitespaceParts.count > 1, state.password == nil {
            state.password = whitespaceParts.dropFirst().joined(separator: " ")
            state.corrections.append("trailing-password-separated")
        }
    }

    static func isPlausibleHost(_ value: String) -> Bool {
        let host = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, host.utf8.count <= 253 else { return false }
        let containsInvalidWhitespace = host.unicodeScalars.contains {
            CharacterSet.whitespacesAndNewlines.contains($0) || $0.value < 32
        }
        guard !containsInvalidWhitespace else {
            return false
        }
        if isIPAddress(host) {
            return true
        }
        let ipv4Parts = host.split(separator: ".", omittingEmptySubsequences: false)
        if ipv4Parts.count == 4, ipv4Parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) }) {
            return false
        }
        guard !host.hasPrefix("."), !host.hasSuffix("."), !host.hasPrefix("-") else { return false }
        return host.split(separator: ".", omittingEmptySubsequences: false).allSatisfy { label in
            guard !label.isEmpty, label.utf8.count <= 63, !label.hasPrefix("-"), !label.hasSuffix("-") else {
                return false
            }
            return label.unicodeScalars.allSatisfy {
                CharacterSet.alphanumerics.contains($0) || $0 == "-" || $0 == "_"
            }
        }
    }

    static func isPlausibleUsername(_ value: String) -> Bool {
        let username = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return !username.isEmpty && username.utf8.count <= 255 && !username.unicodeScalars.contains {
            CharacterSet.whitespacesAndNewlines.contains($0) || $0.value < 32 || $0 == "@"
        }
    }

    private static func normalizeInvisibleCharacters(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .replacingOccurrences(of: "\u{200B}", with: "")
            .replacingOccurrences(of: "\u{200C}", with: "")
            .replacingOccurrences(of: "\u{200D}", with: "")
            .replacingOccurrences(of: "\u{00A0}", with: " ")
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func parseEndpoint(_ value: String) -> (host: String, port: UInt16?) {
        if value.hasPrefix("["), let closingBracket = value.firstIndex(of: "]") {
            let host = String(value[value.index(after: value.startIndex) ..< closingBracket])
            let suffix = value[value.index(after: closingBracket)...]
            let port = suffix.hasPrefix(":") ? UInt16(suffix.dropFirst()) : nil
            return (host, port)
        }
        guard value.filter({ $0 == ":" }).count == 1, let separator = value.lastIndex(of: ":") else {
            return (value, nil)
        }
        let candidatePort = value[value.index(after: separator)...]
        guard let port = UInt16(candidatePort), port > 0 else { return (value, nil) }
        return (String(value[..<separator]), port)
    }

    private static func parseSSHCommand(_ value: String) -> ParsedSSHCommand {
        let tokens = value.split(whereSeparator: \.isWhitespace).map(String.init)
        var destination = ""
        var username: String?
        var port: UInt16?
        var index = 1
        while index < tokens.count {
            switch tokens[index] {
            case "-p" where index + 1 < tokens.count:
                port = UInt16(tokens[index + 1])
                index += 2
            case "-l" where index + 1 < tokens.count:
                username = tokens[index + 1]
                index += 2
            default:
                if !tokens[index].hasPrefix("-") {
                    destination = tokens[index]
                }
                index += 1
            }
        }
        return ParsedSSHCommand(destination: destination, username: username, port: port)
    }

    private static func isIPAddress(_ value: String) -> Bool {
        var ipv4 = in_addr()
        var ipv6 = in6_addr()
        return value.withCString { pointer in
            inet_pton(AF_INET, pointer, &ipv4) == 1 || inet_pton(AF_INET6, pointer, &ipv6) == 1
        }
    }

    private static func nonEmpty(_ value: String) -> String? {
        value.isEmpty ? nil : value
    }

    private static func strippingIPv6Brackets(_ value: String) -> String {
        guard value.hasPrefix("["), value.hasSuffix("]") else { return value }
        return String(value.dropFirst().dropLast())
    }

    private struct ParseState {
        var value: String
        var username: String?
        var password: String?
        var port: UInt16?
        var corrections = [String]()
    }

    private struct ParsedSSHCommand {
        let destination: String
        let username: String?
        let port: UInt16?
    }
}
