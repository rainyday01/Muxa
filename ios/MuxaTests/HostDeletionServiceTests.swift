@testable import Muxa
import XCTest

final class HostDeletionServiceTests: XCTestCase {
    func testDeletesSecretsBeforeCascadingDatabaseRows() async throws {
        let repository = HostDeletionRepositoryFixture()
        let secretStore = HostDeletionSecretStoreFixture()
        let host = Host(id: "host-id", alias: "Server", hostname: "example.invalid", username: "root")
        try await repository.save(host)
        try await secretStore.store(
            Data("password".utf8),
            for: SecretReference(hostID: host.id, kind: .password)
        )

        try await HostDeletionService(repository: repository, secretStore: secretStore).delete(hostID: host.id)

        let deletedHost = try await repository.fetch(id: host.id)
        let deletedPassword = try await secretStore.retrieve(
            SecretReference(hostID: host.id, kind: .password)
        )
        XCTAssertNil(deletedHost)
        XCTAssertNil(deletedPassword)
    }

    func testDeleteAllDataRemovesEveryHostAndSecret() async throws {
        let repository = HostDeletionRepositoryFixture()
        let secretStore = HostDeletionSecretStoreFixture()
        let hostA = Host(id: "a", alias: "A", hostname: "a.example", username: "root")
        let hostB = Host(id: "b", alias: "B", hostname: "b.example", username: "root")
        try await repository.save(hostA)
        try await repository.save(hostB)
        try await secretStore.store(Data("pa".utf8), for: SecretReference(hostID: "a", kind: .password))
        try await secretStore.store(Data("pb".utf8), for: SecretReference(hostID: "b", kind: .password))

        try await HostDeletionService(repository: repository, secretStore: secretStore).deleteAllData()

        let remainingHosts = await repository.fetchAll()
        let remainingA = await secretStore.retrieve(SecretReference(hostID: "a", kind: .password))
        let remainingB = await secretStore.retrieve(SecretReference(hostID: "b", kind: .password))
        XCTAssertTrue(remainingHosts.isEmpty)
        XCTAssertNil(remainingA)
        XCTAssertNil(remainingB)
    }

    func testDeleteAllDataClearsEveryLocalRecordExceptBuiltInLayouts() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let repository = GRDBHostRepository(database: database)
        let secretStore = HostDeletionSecretStoreFixture()
        let host = Host(id: "local-host", alias: "Local", hostname: "host.invalid", username: "dev")
        let identity = HostKeyIdentity(
            hostname: host.hostname,
            port: host.port,
            keyType: "ssh-ed25519",
            sha256Fingerprint: "SHA256:local"
        )

        try await seedLocalRecords(in: database, host: host, identity: identity)

        try await HostDeletionService(
            repository: repository,
            secretStore: secretStore,
            database: database
        ).deleteAllData()

        let remainingHosts = try await repository.fetchAll()
        let snippets = GRDBSnippetRepository(database: database)
        let keyboards = GRDBKeyboardLayoutRepository(database: database)
        let knownHosts = GRDBKnownHostsStore(database: database)
        let history = GRDBCommandHistoryRepository(database: database)
        let snapshots = GRDBSessionSnapshotRepository(database: database)
        let cached = GRDBCachedSessionRepository(database: database)
        let remainingSnippets = try await snippets.fetchAll()
        let remainingLayouts = try await keyboards.fetchAll()
        let remainingKnownHost = try await knownHosts.verify(identity, seenAt: Date())
        let remainingHistory = try await history.fetchRecent(hostID: host.id, limit: 10)
        let remainingSnapshot = try await snapshots.fetch(hostID: host.id, sessionName: "work")
        let remainingSessions = try await cached.fetch(hostID: host.id)
        XCTAssertTrue(remainingHosts.isEmpty)
        XCTAssertTrue(remainingSnippets.isEmpty)
        XCTAssertEqual(remainingLayouts, [KeyboardLayout.general])
        XCTAssertEqual(remainingKnownHost, .unknown(identity))
        XCTAssertTrue(remainingHistory.isEmpty)
        XCTAssertNil(remainingSnapshot)
        XCTAssertTrue(remainingSessions.isEmpty)
    }
}

private func seedLocalRecords(
    in database: MuxaDatabase,
    host: Host,
    identity: HostKeyIdentity
) async throws {
    let repository = GRDBHostRepository(database: database)
    try await repository.save(host)
    try await GRDBSnippetRepository(database: database).save(
        Snippet(id: "snippet", name: "Status", body: "status")
    )
    let keyboards = GRDBKeyboardLayoutRepository(database: database)
    try await keyboards.save(KeyboardLayout(id: "custom", name: "Custom"))
    try await keyboards.save(KeyboardLayout.general)
    try await GRDBKnownHostsStore(database: database).acceptFirst(identity, seenAt: Date())
    try await GRDBCommandHistoryRepository(database: database).record(
        hostID: host.id,
        command: "status",
        usedAt: Date()
    )
    try await GRDBSessionSnapshotRepository(database: database).save(
        SessionSnapshot(hostID: host.id, sessionName: "work", text: "output", updatedAt: Date())
    )
    try await GRDBCachedSessionRepository(database: database).replace(
        hostID: host.id,
        sessions: [TmuxSession(
            hostID: host.id,
            name: "work",
            windowCount: 1,
            attached: false,
            createdAt: nil,
            lastActivityAt: nil,
            snapshotText: nil,
            snapshotUpdatedAt: nil,
            hostAlias: host.alias,
            socketPath: nil,
            windows: []
        )],
        listedAt: Date()
    )
}

private actor HostDeletionRepositoryFixture: HostRepository {
    private var hosts = [String: Host]()

    func observeAll() -> AsyncStream<[Host]> {
        AsyncStream { $0.finish() }
    }

    func fetchAll() -> [Host] {
        Array(hosts.values)
    }

    func fetch(id: String) -> Host? {
        hosts[id]
    }

    func save(_ host: Host) {
        hosts[host.id] = host
    }

    func updateResources(hostID: String, resources: HostResources) {
        guard !resources.isEmpty, let existing = hosts[hostID] else { return }
        hosts[hostID] = existing.applying(resources: resources)
    }

    func delete(id: String) {
        hosts.removeValue(forKey: id)
    }

    func deleteAll() {
        hosts.removeAll()
    }
}

private actor HostDeletionSecretStoreFixture: SecretStoring {
    private var values = [SecretReference: Data]()

    func store(_ value: Data, for reference: SecretReference) {
        values[reference] = value
    }

    func retrieve(_ reference: SecretReference) -> Data? {
        values[reference]
    }

    func delete(_ reference: SecretReference) {
        values.removeValue(forKey: reference)
    }

    func deleteAll(hostID: String) {
        values = values.filter { $0.key.hostID != hostID }
    }

    func deleteAllSecrets() {
        values.removeAll()
    }
}
