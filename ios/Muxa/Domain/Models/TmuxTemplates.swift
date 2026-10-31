import Foundation

enum TmuxSessionTemplateKind: String, CaseIterable, Codable, Identifiable, Sendable {
    case empty
    case claude
    case codex
    case grok
    case agy
    case custom

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .empty: "Empty shell"
        case .claude: "Claude"
        case .codex: "Codex"
        case .grok: "Grok"
        case .agy: "Antigravity"
        case .custom: "Custom command"
        }
    }

    var defaultCommand: String? {
        switch self {
        case .empty: nil
        case .claude: "claude"
        case .codex: "codex"
        case .grok: "grok"
        case .agy: "agy"
        case .custom: nil
        }
    }
}

enum TmuxSessionDirectoryMode: String, CaseIterable, Codable, Identifiable, Sendable {
    case createAndEnter
    case enterExisting

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .createAndEnter: "Create and enter"
        case .enterExisting: "Enter existing"
        }
    }
}

struct TmuxSessionTemplate: Codable, Equatable, Identifiable, Sendable {
    let kind: TmuxSessionTemplateKind
    let customCommand: String?
    let directory: String?
    let directoryMode: TmuxSessionDirectoryMode
    let skipDangerousPermissions: Bool
    let yolo: Bool

    var id: String {
        kind.id
    }

    var title: String {
        kind.title
    }

    var initialCommand: String? {
        var commands = [String]()
        if let directory {
            let value = directory.trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty {
                if directoryMode == .createAndEnter {
                    commands.append("mkdir -p \(Self.quotePath(value))")
                }
                commands.append("cd \(Self.quotePath(value))")
            }
        }

        switch kind {
        case .custom:
            let value = customCommand?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !value.isEmpty {
                commands.append(value)
            }
        default:
            if let command = kind.defaultCommand {
                var commandLine = command
                if skipDangerousPermissions, kind == .claude || kind == .agy {
                    commandLine += " --dangerously-skip-permissions"
                } else if yolo, kind == .codex {
                    commandLine += " --yolo"
                }
                commands.append(commandLine)
            }
        }
        return commands.isEmpty ? nil : commands.joined(separator: " && ")
    }

    init(
        kind: TmuxSessionTemplateKind,
        customCommand: String? = nil,
        directory: String? = nil,
        directoryMode: TmuxSessionDirectoryMode = .createAndEnter,
        skipDangerousPermissions: Bool = false,
        yolo: Bool = false
    ) {
        self.kind = kind
        self.customCommand = customCommand
        self.directory = directory
        self.directoryMode = directoryMode
        self.skipDangerousPermissions = skipDangerousPermissions
        self.yolo = yolo
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        kind = try container.decode(TmuxSessionTemplateKind.self, forKey: .kind)
        customCommand = try container.decodeIfPresent(String.self, forKey: .customCommand)
        directory = try container.decodeIfPresent(String.self, forKey: .directory)
        directoryMode = try container.decodeIfPresent(
            TmuxSessionDirectoryMode.self,
            forKey: .directoryMode
        ) ?? .createAndEnter
        skipDangerousPermissions = try container.decodeIfPresent(
            Bool.self,
            forKey: .skipDangerousPermissions
        ) ?? false
        yolo = try container.decodeIfPresent(Bool.self, forKey: .yolo) ?? false
    }

    private static func quotePath(_ path: String) -> String {
        let safeCharacters = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "/._-~"))
        if path.unicodeScalars.allSatisfy(safeCharacters.contains) {
            return path
        }
        return "'" + path.replacingOccurrences(of: "'", with: "'\"'\"'") + "'"
    }
}

struct TmuxSessionTemplateCatalog: Sendable {
    let templates: [TmuxSessionTemplate]

    init(templates: [TmuxSessionTemplate] = TmuxSessionTemplateKind.allCases.map { TmuxSessionTemplate(kind: $0) }) {
        self.templates = templates
    }

    func template(for kind: TmuxSessionTemplateKind) -> TmuxSessionTemplate {
        templates.first(where: { $0.kind == kind }) ?? TmuxSessionTemplate(kind: kind)
    }
}
