import Foundation

enum DiagnosticLevel: String, Codable, Sendable {
    case debug
    case info
    case notice
    case error
}

struct DiagnosticEntry: Codable, Equatable, Identifiable, Sendable {
    let id: UUID
    let timestamp: Date
    let level: DiagnosticLevel
    let category: String
    let message: String
    let metadata: [String: String]
}

protocol DiagnosticLogging: Sendable {
    func log(
        level: DiagnosticLevel,
        category: String,
        message: String,
        metadata: [String: String]
    ) async
    func entries() async -> [DiagnosticEntry]
    func export() async throws -> Data
    func clear() async
}
