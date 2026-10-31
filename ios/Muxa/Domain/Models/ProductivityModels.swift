import Foundation

struct Snippet: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var name: String
    var body: String
    var tags: [String]
    var runOnSelect: Bool

    init(
        id: String = UUID().uuidString,
        name: String,
        body: String,
        tags: [String] = [],
        runOnSelect: Bool = false
    ) {
        self.id = id
        self.name = name
        self.body = body
        self.tags = tags
        self.runOnSelect = runOnSelect
    }
}

enum SuggestionSource: String, Codable, Sendable {
    case snippet
    case history
    case builtIn
    case remotePath
}

struct Suggestion: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let label: String
    let insertion: String
    let source: SuggestionSource
    let runAfterInsert: Bool
    let score: Double
}
