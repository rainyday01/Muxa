import Foundation

protocol HostRepository: Sendable {
    func observeAll() async throws -> AsyncStream<[Host]>
    func fetchAll() async throws -> [Host]
    func fetch(id: String) async throws -> Host?
    func save(_ host: Host) async throws
    /// Patch only the last-sampled mem/disk stats for a host.
    func updateResources(hostID: String, resources: HostResources) async throws
    func delete(id: String) async throws
    func deleteAll() async throws
}

protocol SnippetRepository: Sendable {
    func observeAll() async throws -> AsyncStream<[Snippet]>
    func fetchAll() async throws -> [Snippet]
    func save(_ snippet: Snippet) async throws
    func delete(id: String) async throws
}

protocol KeyboardLayoutRepository: Sendable {
    func fetchAll() async throws -> [KeyboardLayout]
    func save(_ layout: KeyboardLayout) async throws
    func delete(id: String) async throws
}

protocol CommandHistoryRepository: Sendable {
    func record(hostID: String, command: String, usedAt: Date) async throws
    func fetchRecent(hostID: String, limit: Int) async throws -> [CommandHistoryEntry]
    func prune(hostID: String, keeping limit: Int) async throws
}

protocol SessionSnapshotRepository: Sendable {
    func fetch(hostID: String, sessionName: String) async throws -> SessionSnapshot?
    func save(_ snapshot: SessionSnapshot) async throws
    func deleteAll(hostID: String) async throws
}

protocol CachedSessionRepository: Sendable {
    func fetch(hostID: String) async throws -> [TmuxSession]
    func replace(hostID: String, sessions: [TmuxSession], listedAt: Date) async throws
    func deleteAll(hostID: String) async throws
}
