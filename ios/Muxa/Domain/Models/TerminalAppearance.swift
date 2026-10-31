import Foundation
import SwiftUI
import UIKit

struct TerminalColorScheme: Identifiable, Equatable, Sendable {
    let id: String
    let displayName: String
    let foreground: String
    let background: String
    let cursor: String
    let ansi: [String]
}

enum TerminalColorCatalog {
    static let defaultID = "pulse_mono"

    static let all: [TerminalColorScheme] = [
        scheme(
            "pulse_mono",
            "Pulse Mono",
            fg: "#E4E4E7",
            bg: "#050506",
            cursor: "#FAFAFA",
            ansi: [
                "#09090B", "#F87171", "#A1A1AA", "#A1A1AA",
                "#A1A1AA", "#A1A1AA", "#A1A1AA", "#E4E4E7",
                "#52525B", "#FCA5A5", "#D4D4D8", "#D4D4D8",
                "#D4D4D8", "#D4D4D8", "#D4D4D8", "#FAFAFA"
            ]
        ),
        scheme(
            "termius_dark",
            "Termius Dark",
            fg: "#7DCF7A",
            bg: "#0B1410",
            cursor: "#7DCF7A",
            ansi: [
                "#0B1410", "#E06C75", "#7DCF7A", "#E5C07B",
                "#61AFEF", "#C678DD", "#56B6C2", "#A8B5A0",
                "#4A5C50", "#E06C75", "#98E096", "#E5C07B",
                "#61AFEF", "#C678DD", "#56B6C2", "#D4E8D0"
            ]
        ),
        scheme(
            "termius_light",
            "Termius Light",
            fg: "#1C1C1E",
            bg: "#F5F5F7",
            cursor: "#007AFF",
            ansi: [
                "#1C1C1E", "#D70015", "#248A3D", "#9A6700",
                "#0040DD", "#8944AB", "#0071A4", "#8E8E93",
                "#636366", "#FF453A", "#30D158", "#FFD60A",
                "#0A84FF", "#BF5AF2", "#64D2FF", "#1C1C1E"
            ]
        ),
        scheme(
            "dracula",
            "Dracula",
            fg: "#F8F8F2",
            bg: "#282A36",
            cursor: "#F8F8F2",
            ansi: [
                "#21222C", "#FF5555", "#50FA7B", "#F1FA8C",
                "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
                "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5",
                "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF"
            ]
        ),
        scheme(
            "nord",
            "Nord",
            fg: "#D8DEE9",
            bg: "#2E3440",
            cursor: "#D8DEE9",
            ansi: [
                "#3B4252", "#BF616A", "#A3BE8C", "#EBCB8B",
                "#81A1C1", "#B48EAD", "#88C0D0", "#E5E9F0",
                "#4C566A", "#BF616A", "#A3BE8C", "#EBCB8B",
                "#81A1C1", "#B48EAD", "#8FBCBB", "#ECEFF4"
            ]
        ),
        scheme(
            "solarized_dark",
            "Solarized Dark",
            fg: "#839496",
            bg: "#002B36",
            cursor: "#839496",
            ansi: [
                "#073642", "#DC322F", "#859900", "#B58900",
                "#268BD2", "#D33682", "#2AA198", "#EEE8D5",
                "#002B36", "#CB4B16", "#586E75", "#657B83",
                "#839496", "#6C71C4", "#93A1A1", "#FDF6E3"
            ]
        ),
        scheme(
            "gruvbox_dark",
            "Gruvbox Dark",
            fg: "#EBDBB2",
            bg: "#282828",
            cursor: "#EBDBB2",
            ansi: [
                "#282828", "#CC241D", "#98971A", "#D79921",
                "#458588", "#B16286", "#689D6A", "#A89984",
                "#928374", "#FB4934", "#B8BB26", "#FABD2F",
                "#83A598", "#D3869B", "#8EC07C", "#EBDBB2"
            ]
        ),
        scheme(
            "kanagawa_wave",
            "Kanagawa Wave",
            fg: "#DCD7BA",
            bg: "#1F1F28",
            cursor: "#C8C093",
            ansi: [
                "#090618", "#C34043", "#76946A", "#C0A36E",
                "#7E9CD8", "#957FB8", "#6A9589", "#C8C093",
                "#727169", "#E82424", "#98BB6C", "#E6C384",
                "#7FB4CA", "#938AA9", "#7AA89F", "#DCD7BA"
            ]
        ),
        scheme(
            "everforest_dark",
            "Everforest Dark",
            fg: "#D3C6AA",
            bg: "#2D353B",
            cursor: "#D3C6AA",
            ansi: [
                "#343F44", "#E67E80", "#A7C080", "#DBBC7F",
                "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA",
                "#5C6A72", "#E67E80", "#A7C080", "#DBBC7F",
                "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA"
            ]
        ),
        scheme(
            "night_owl",
            "Night Owl",
            fg: "#D6DEEB",
            bg: "#011627",
            cursor: "#80A4C2",
            ansi: [
                "#011627", "#EF5350", "#22DA6E", "#ADDB67",
                "#82AAFF", "#C792EA", "#21C7A8", "#FFFFFF",
                "#575656", "#EF5350", "#22DA6E", "#FFEB95",
                "#82AAFF", "#C792EA", "#7FDBCA", "#FFFFFF"
            ]
        ),
        scheme(
            "flexoki_dark",
            "Flexoki Dark",
            fg: "#CECDC3",
            bg: "#100F0F",
            cursor: "#CECDC3",
            ansi: [
                "#100F0F", "#D14D41", "#879A39", "#D0A215",
                "#4385BE", "#CE5D97", "#3AA99F", "#878580",
                "#575653", "#D14D41", "#879A39", "#D0A215",
                "#4385BE", "#CE5D97", "#3AA99F", "#CECDC3"
            ]
        )
    ]

    static func scheme(id: String) -> TerminalColorScheme {
        all.first(where: { $0.id == id }) ?? all[0]
    }

    static let previewLines = [
        "RM(1)                    User Commands                    RM(1)",
        "",
        "NAME",
        "       rm - remove files or directories",
        "",
        "SYNOPSIS",
        "       rm [OPTION]... FILE..."
    ]

    // swiftlint:disable function_parameter_count identifier_name
    private static func scheme(
        _ id: String,
        _ name: String,
        fg: String,
        bg: String,
        cursor: String,
        ansi: [String]
    ) -> TerminalColorScheme {
        TerminalColorScheme(
            id: id,
            displayName: name,
            foreground: fg,
            background: bg,
            cursor: cursor,
            ansi: ansi
        )
    }

    // swiftlint:enable function_parameter_count identifier_name
}

extension UIColor {
    convenience init?(muxaHex: String) {
        var hex = muxaHex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        if hex.count == 6 {
            hex = "FF" + hex
        }
        guard hex.count == 8, let value = UInt64(hex, radix: 16) else { return nil }
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }
}
