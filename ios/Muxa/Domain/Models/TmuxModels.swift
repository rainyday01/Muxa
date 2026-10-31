import Foundation

struct TmuxSession: Codable, Equatable, Identifiable, Sendable {
    var id: String {
        "\(hostID)\t\(socketPath ?? "")\t\(name)"
    }

    let hostID: String
    var name: String
    var windowCount: Int
    var attached: Bool
    var createdAt: Date?
    var lastActivityAt: Date?
    var snapshotText: String?
    var snapshotUpdatedAt: Date?
    var hostAlias: String?
    var socketPath: String?
    var windows: [TmuxWindow]
}

struct TmuxWindow: Codable, Equatable, Identifiable, Sendable {
    var id: String {
        "\(sessionName)\t\(index)"
    }

    let sessionName: String
    let index: Int
    var name: String
    var active: Bool
    var layout: String?
    var paneCount: Int
    var panes: [TmuxPane]
}

struct TmuxPane: Codable, Equatable, Identifiable, Sendable {
    var id: String {
        "\(sessionName)\t\(windowIndex)\t\(index)"
    }

    let sessionName: String
    let windowIndex: Int
    let index: Int
    var active: Bool
    var title: String?
    var currentCommand: String?
    var currentPath: String?
    var width: Int?
    var height: Int?
    var processID: Int32?
}
