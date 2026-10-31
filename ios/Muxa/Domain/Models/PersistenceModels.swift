import Foundation

struct CommandHistoryEntry: Codable, Equatable, Identifiable, Sendable {
    let id: Int64
    let hostID: String
    let command: String
    let usedAt: Date
}

struct SessionSnapshot: Codable, Equatable, Identifiable, Sendable {
    var id: String {
        "\(hostID)\t\(sessionName)"
    }

    let hostID: String
    let sessionName: String
    let text: String
    let updatedAt: Date
}
