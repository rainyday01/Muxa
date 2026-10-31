import Foundation

enum AutocompleteEngine {
    static let historyLimit = 500
    static let suggestionLimit = 12

    static let builtins = [
        "git status",
        "git diff",
        "git pull",
        "git push",
        "tmux ls",
        "tmux attach -t ",
        "htop",
        "ls -la",
        "cd ",
        "ssh ",
        "claude",
        "codex",
        "systemctl status ",
        "docker ps"
    ]

    static func applyVars(_ body: String, vars: [String: String]) -> String {
        vars.reduce(body) { partial, pair in
            partial.replacingOccurrences(of: "{{\(pair.key)}}", with: pair.value)
        }
    }

    static func currentLine(from input: String) -> String {
        input.split(whereSeparator: \.isNewline).last.map(String.init) ?? input
    }

    static func looksLikePathToken(_ token: String, fullLine: String = "") -> Bool {
        if token.isEmpty {
            let trimmed = fullLine.trimmingCharacters(in: .whitespaces)
            return trimmed == "cd"
                || trimmed.hasSuffix(" cd")
                || trimmed.hasSuffix("ls")
                || trimmed.hasSuffix(" cat")
                || trimmed.hasSuffix(" vim")
                || trimmed.hasSuffix(" nvim")
                || trimmed.hasSuffix(" nano")
        }
        return token.hasPrefix("/")
            || token.hasPrefix("./")
            || token.hasPrefix("../")
            || token.hasPrefix("~/")
            || token.contains("/")
            || fullLine.trimmingCharacters(in: .whitespaces).hasPrefix("cd ")
    }

    static func remoteCompgenCommand(linePrefix: String) -> String? {
        let line = currentLine(from: linePrefix)
        let token = line.trimmingCharacters(in: .whitespaces).split(separator: " ").last.map(String.init) ?? ""
        let queryToken = line.hasSuffix(" ") ? "" : token
        guard looksLikePathToken(queryToken, fullLine: line) else { return nil }
        let dirsOnly = line.trimmingCharacters(in: .whitespaces).hasPrefix("cd ")
            || line.trimmingCharacters(in: .whitespaces) == "cd"
        let flag = dirsOnly ? "-d" : "-f"
        let script = "compgen \(flag) -- \(ShellQuoting.singleQuote(queryToken)) 2>/dev/null | head -n 24"
        return "bash -c \(ShellQuoting.singleQuote(script))"
    }

    static func parseCompgenOutput(_ raw: String) -> [String] {
        var seen = Set<String>()
        var values = [String]()
        for line in raw.split(whereSeparator: \.isNewline) {
            let value = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty, seen.insert(value).inserted else { continue }
            values.append(value)
            if values.count == 24 {
                break
            }
        }
        return values
    }

    static func pathCompletionInsertion(token: String, completed: String) -> String {
        if token.isEmpty {
            return completed
        }
        return completed.hasPrefix(token) ? String(completed.dropFirst(token.count)) : completed
    }

    static func isTuiMode(alternateScreen: Bool, snapshot: String, lineBuffer: String) -> Bool {
        if alternateScreen {
            return true
        }
        let markers = ["╭", "╰", "│", "claude code", "codex", "───"]
        let lower = snapshot.lowercased()
        let dense = markers.filter { lower.contains($0) || snapshot.contains($0) }.count >= 2
        if dense, lineBuffer.count < 2 {
            return true
        }
        let head = snapshot.split(whereSeparator: \.isNewline).prefix(3).joined(separator: "\n").lowercased()
        return head.contains("htop") || head.contains("btop") || head.contains("nvtop")
    }

    static func shouldRecordHistory(_ command: String) -> Bool {
        let trimmed = command.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 500 else { return false }
        let lowered = trimmed.lowercased()
        if lowered.contains("password") || lowered.contains("passphrase") || lowered.contains("secret") {
            return false
        }
        return !trimmed.contains("-----BEGIN")
    }

    struct Query: Sendable {
        let linePrefix: String
        let tuiMode: Bool
        let snippets: [Snippet]
        let history: [CommandHistoryEntry]
        let pathHints: [String]
        let vars: [String: String]
        var limit: Int = suggestionLimit
    }

    static func suggest(_ query: Query) -> [Suggestion] {
        if query.tuiMode {
            return []
        }
        let line = currentLine(from: query.linePrefix).trimmingCharacters(in: .whitespaces)
        guard !line.isEmpty else { return [] }
        let token = line.split(separator: " ").last.map(String.init) ?? line
        var bag = SuggestionBag()
        appendPaths(token: token, line: line, hints: query.pathHints, bag: &bag)
        appendSnippets(query.snippets, line: line, token: token, vars: query.vars, bag: &bag)
        appendHistory(query.history, line: line, token: token, bag: &bag)
        appendBuiltins(token: token, vars: query.vars, bag: &bag)
        return Array(bag.ordered.prefix(max(0, query.limit)))
    }

    private struct SuggestionBag {
        var ordered = [Suggestion]()
        var seen = Set<String>()

        mutating func add(_ suggestion: Suggestion) {
            guard seen.insert(suggestion.id).inserted else { return }
            ordered.append(suggestion)
        }
    }

    private static func appendPaths(token: String, line: String, hints: [String], bag: inout SuggestionBag) {
        guard !hints.isEmpty, looksLikePathToken(token, fullLine: line) else { return }
        for path in hints {
            bag.add(
                Suggestion(
                    id: "path_\(path)",
                    label: path,
                    insertion: pathCompletionInsertion(token: token, completed: path),
                    source: .remotePath,
                    runAfterInsert: false,
                    score: 1.0
                )
            )
        }
    }

    private static func appendSnippets(
        _ snippets: [Snippet],
        line: String,
        token: String,
        vars: [String: String],
        bag: inout SuggestionBag
    ) {
        for snippet in snippets where matches(snippet, query: line, token: token) {
            bag.add(
                Suggestion(
                    id: "snip_\(snippet.id)",
                    label: snippet.name,
                    insertion: applyVars(snippet.body, vars: vars),
                    source: .snippet,
                    runAfterInsert: snippet.runOnSelect,
                    score: 0.8
                )
            )
        }
    }

    private static func appendHistory(
        _ history: [CommandHistoryEntry],
        line: String,
        token: String,
        bag: inout SuggestionBag
    ) {
        let needle = token.isEmpty ? line : token
        for entry in history where entry.command.localizedCaseInsensitiveContains(needle) {
            bag.add(
                Suggestion(
                    id: "hist_\(entry.id)",
                    label: entry.command,
                    insertion: entry.command,
                    source: .history,
                    runAfterInsert: false,
                    score: 0.6
                )
            )
        }
    }

    private static func appendBuiltins(token: String, vars: [String: String], bag: inout SuggestionBag) {
        for command in builtins {
            let matchesToken = command.localizedCaseInsensitiveContains(token)
                || command.lowercased().hasPrefix(token.lowercased())
            guard matchesToken else { continue }
            bag.add(
                Suggestion(
                    id: "bin_\(command)",
                    label: command,
                    insertion: applyVars(command, vars: vars),
                    source: .builtIn,
                    runAfterInsert: false,
                    score: 0.4
                )
            )
        }
    }

    private static func matches(_ snippet: Snippet, query: String, token: String) -> Bool {
        snippet.name.localizedCaseInsensitiveContains(query)
            || snippet.name.localizedCaseInsensitiveContains(token)
            || snippet.body.localizedCaseInsensitiveContains(query)
    }
}
