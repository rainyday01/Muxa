import GRDB
@testable import Muxa
import XCTest

final class PersistenceRepositoriesTests: XCTestCase {
    func testMigrationCreatesExpectedTables() throws {
        let database = try MuxaDatabase(inMemory: true)

        let tables = try database.queue.read { database in
            try String.fetchAll(
                database,
                sql: "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
            )
        }

        XCTAssertTrue(tables.contains("hosts"))
        XCTAssertTrue(tables.contains("snippets"))
        XCTAssertTrue(tables.contains("commandHistory"))
        XCTAssertTrue(tables.contains("sessionSnapshots"))
        XCTAssertTrue(tables.contains("cachedSessions"))
        XCTAssertTrue(tables.contains("keyboardLayouts"))
        XCTAssertTrue(tables.contains("knownHosts"))
    }

    func testRepositoriesRoundTripObserveAndCascade() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let snippets = GRDBSnippetRepository(database: database)
        let history = GRDBCommandHistoryRepository(database: database)
        let snapshots = GRDBSessionSnapshotRepository(database: database)
        let cached = GRDBCachedSessionRepository(database: database)
        let keyboards = GRDBKeyboardLayoutRepository(database: database)
        let host = Host(id: "host-1", alias: "Lab", hostname: "lab.invalid", username: "dev")

        let hostStream = try await hosts.observeAll()
        var hostIterator = hostStream.makeAsyncIterator()
        let initialHosts = await hostIterator.next()
        XCTAssertEqual(initialHosts, [])
        try await hosts.save(host)
        let updatedHosts = await hostIterator.next()
        XCTAssertEqual(updatedHosts, [host])

        let snippet = Snippet(id: "snippet-1", name: "List", body: "ls", tags: ["shell"])
        try await snippets.save(snippet)
        let savedSnippets = try await snippets.fetchAll()
        XCTAssertEqual(savedSnippets, [snippet])

        try await history.record(hostID: host.id, command: "one", usedAt: Date(timeIntervalSince1970: 1))
        try await history.record(hostID: host.id, command: "two", usedAt: Date(timeIntervalSince1970: 2))
        try await history.record(hostID: host.id, command: "three", usedAt: Date(timeIntervalSince1970: 3))
        try await history.prune(hostID: host.id, keeping: 2)
        let recentCommands = try await history.fetchRecent(hostID: host.id, limit: 10).map(\.command)
        XCTAssertEqual(recentCommands, ["three", "two"])

        let snapshot = SessionSnapshot(
            hostID: host.id,
            sessionName: "work",
            text: "$ status",
            updatedAt: Date(timeIntervalSince1970: 4)
        )
        try await snapshots.save(snapshot)
        let savedSnapshot = try await snapshots.fetch(hostID: host.id, sessionName: "work")
        XCTAssertEqual(savedSnapshot, snapshot)

        let session = makeSession(hostID: host.id)
        try await cached.replace(hostID: host.id, sessions: [session], listedAt: Date())
        let cachedSessions = try await cached.fetch(hostID: host.id)
        XCTAssertEqual(cachedSessions.first?.name, "work")

        let layout = KeyboardLayout(id: "custom", name: "Custom")
        try await keyboards.save(layout)
        let savedLayouts = try await keyboards.fetchAll()
        XCTAssertEqual(savedLayouts, [layout])

        try await hosts.delete(id: host.id)
        let remainingHistory = try await history.fetchRecent(hostID: host.id, limit: 10)
        let remainingSnapshot = try await snapshots.fetch(hostID: host.id, sessionName: "work")
        let remainingSessions = try await cached.fetch(hostID: host.id)
        XCTAssertEqual(remainingHistory, [])
        XCTAssertNil(remainingSnapshot)
        XCTAssertEqual(remainingSessions, [])
    }

    func testUpdateResourcesPatchesHostWithoutWipingFields() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let original = Host(
            id: "host-1",
            alias: "Lab",
            hostname: "lab.invalid",
            username: "dev",
            tmuxInstalled: true,
            lastLatencyMilliseconds: 42,
            lastMemAvailableKB: 1_048_576,
            lastDiskAvailableKB: 8 * 1_048_576
        )
        try await hosts.save(original)

        // Partial sample: memory only — disk keeps its previous value.
        try await hosts.updateResources(
            hostID: original.id,
            resources: HostResources(memAvailableKB: 2_048_000)
        )
        var stored = try await hosts.fetch(id: original.id)
        XCTAssertEqual(stored?.lastMemAvailableKB, 2_048_000)
        XCTAssertEqual(stored?.lastMemTotalKB, nil)
        XCTAssertEqual(stored?.lastDiskAvailableKB, 8 * 1_048_576)
        XCTAssertEqual(stored?.tmuxInstalled, true)
        XCTAssertEqual(stored?.lastLatencyMilliseconds, 42)
        XCTAssertNotNil(stored?.lastStatsAt)

        // Empty samples are ignored.
        try await hosts.updateResources(hostID: original.id, resources: HostResources())
        stored = try await hosts.fetch(id: original.id)
        XCTAssertEqual(stored?.lastMemAvailableKB, 2_048_000)

        // Unknown hosts are a no-op.
        try await hosts.updateResources(
            hostID: "missing",
            resources: HostResources(memAvailableKB: 1)
        )
        let allHosts = try await hosts.fetchAll()
        XCTAssertEqual(allHosts.count, 1)
    }

    func testHostResourceMigrationAddsColumnsWithoutWipingHosts() throws {
        let database = try MuxaDatabase(inMemory: true)
        try database.queue.write { database in
            let columns = try String.fetchAll(
                database,
                sql: "SELECT name FROM pragma_table_info('hosts') ORDER BY name"
            )
            for column in [
                "lastMemAvailableKB",
                "lastMemTotalKB",
                "lastDiskAvailableKB",
                "lastDiskTotalKB",
                "lastStatsAt"
            ] {
                XCTAssertTrue(columns.contains(column), "missing column \(column)")
            }
        }
    }

    private func makeSession(hostID: String) -> TmuxSession {
        TmuxSession(
            hostID: hostID,
            name: "work",
            windowCount: 2,
            attached: false,
            createdAt: nil,
            lastActivityAt: nil,
            snapshotText: nil,
            snapshotUpdatedAt: nil,
            hostAlias: "Lab",
            socketPath: "/tmp/tmux/default",
            windows: []
        )
    }
}
