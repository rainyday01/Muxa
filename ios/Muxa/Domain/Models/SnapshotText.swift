import Foundation

enum SnapshotText {
    /// Visible card height is ~1/3 of the old 6-line preview.
    static let cardLines = 2
    static let cardCharacters = 160
    static let storeCharacters = 2000

    static func forCard(
        _ raw: String?,
        maxLines: Int = cardLines,
        maxCharacters: Int = cardCharacters
    ) -> String? {
        trimmedTail(raw, maxLines: maxLines, maxCharacters: maxCharacters)
    }

    static func forStore(_ raw: String?) -> String? {
        trimmedTail(raw, maxLines: 80, maxCharacters: storeCharacters)
    }

    static func isUseful(_ text: String?) -> Bool {
        guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        let usefulLines = text.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && !isChromeOnly($0) }
        return text.trimmingCharacters(in: .whitespacesAndNewlines).count >= 24 && !usefulLines.isEmpty
    }

    private static func trimmedTail(
        _ raw: String?,
        maxLines: Int,
        maxCharacters: Int
    ) -> String? {
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        let lines = raw.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !isChromeOnly($0) }
        guard !lines.isEmpty else { return nil }
        var text = lines.suffix(max(1, maxLines))
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if text.count > maxCharacters {
            let cut = String(text.suffix(maxCharacters))
            text = cut.split(separator: "\n", maxSplits: 1).dropFirst().first.map(String.init) ?? cut
            text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return text.isEmpty ? nil : text
    }

    private static func isChromeOnly(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty || trimmed.allSatisfy { $0.isWhitespace || boxCharacters.contains($0) }
    }

    private static let boxCharacters: Set<Character> = Set(
        "─━│┃┄┅┆┇┈┉┊┋┌┍┎┏┐┑┒┓└┕┖┗┘┙┚┛├┝┞┟┠┡┢┣┤┥┦┧┨┩┪┫" +
            "┬┭┮┯┰┱┲┳┴┵┶┷┸┹┺┻┼┽┾┿╀╁╂╃╄╅╆╇╈╉╊╋═║╒╓╔╕╖╗╘╙╚╛╜╝" +
            "╞╟╠╡╢╣╤╥╦╧╨╩╪╫╬╭╮╯╰╱╲╳╴╵╶╷╸╹╺╻╼╽╾╿" +
            "▀▁▂▃▄▅▆▇█▉▊▋▌▍▎▏▐░▒▓▔▕▖▗▘▙▚▛▜▝▞▟■□▪▫●○"
    )
}
