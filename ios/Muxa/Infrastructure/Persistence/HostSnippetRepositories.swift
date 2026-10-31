import Foundation
import GRDB

actor GRDBHostRepository: HostRepository {
    private let database: MuxaDatabase
    private var observers = [UUID: AsyncStream<[Host]>.Continuation]()

    init(database: MuxaDatabase) {
        self.database = database
    }

    func observeAll() throws -> AsyncStream<[Host]> {
        let (stream, continuation) = AsyncStream.makeStream(of: [Host].self)
        let id = UUID()
        observers[id] = continuation
        try continuation.yield(fetchAll())
        continuation.onTermination = { [weak self] _ in
            Task { await self?.removeObserver(id) }
        }
        return stream
    }

    func fetchAll() throws -> [Host] {
        try database.queue.read { database in
            try HostRecord.order(Column("alias").collating(.localizedCaseInsensitiveCompare)).fetchAll(database)
                .map(\.domain)
        }
    }

    func fetch(id: String) throws -> Host? {
        try database.queue.read { database in
            try HostRecord.fetchOne(database, key: id)?.domain
        }
    }

    func save(_ host: Host) throws {
        try database.queue.write { database in
            try HostRecord(host).save(database)
        }
        try notifyObservers()
    }

    /// Patches only the last-sampled mem/disk; ignores empty samples.
    func updateResources(hostID: String, resources: HostResources) throws {
        guard !resources.isEmpty else { return }
        guard let existing = try database.queue.read({ database in
            try HostRecord.fetchOne(database, key: hostID)
        }) else { return }
        let updated = existing.applying(resources: resources)
        try database.queue.write { database in
            try updated.save(database)
        }
        try notifyObservers()
    }

    func delete(id: String) throws {
        _ = try database.queue.write { database in
            try HostRecord.deleteOne(database, key: id)
        }
        try notifyObservers()
    }

    func deleteAll() throws {
        _ = try database.queue.write { database in
            try HostRecord.deleteAll(database)
        }
        try notifyObservers()
    }

    private func removeObserver(_ id: UUID) {
        observers.removeValue(forKey: id)
    }

    private func notifyObservers() throws {
        let hosts = try fetchAll()
        observers.values.forEach { $0.yield(hosts) }
    }
}

actor GRDBSnippetRepository: SnippetRepository {
    private let database: MuxaDatabase
    private var observers = [UUID: AsyncStream<[Snippet]>.Continuation]()

    init(database: MuxaDatabase) {
        self.database = database
    }

    func observeAll() throws -> AsyncStream<[Snippet]> {
        let (stream, continuation) = AsyncStream.makeStream(of: [Snippet].self)
        let id = UUID()
        observers[id] = continuation
        try continuation.yield(fetchAll())
        continuation.onTermination = { [weak self] _ in
            Task { await self?.removeObserver(id) }
        }
        return stream
    }

    func fetchAll() throws -> [Snippet] {
        try database.queue.read { database in
            try SnippetRecord.order(Column("name").collating(.localizedCaseInsensitiveCompare)).fetchAll(database)
                .map(\.domain)
        }
    }

    func save(_ snippet: Snippet) throws {
        try database.queue.write { database in
            try SnippetRecord(snippet).save(database)
        }
        try notifyObservers()
    }

    func delete(id: String) throws {
        _ = try database.queue.write { database in
            try SnippetRecord.deleteOne(database, key: id)
        }
        try notifyObservers()
    }

    private func removeObserver(_ id: UUID) {
        observers.removeValue(forKey: id)
    }

    private func notifyObservers() throws {
        let snippets = try fetchAll()
        observers.values.forEach { $0.yield(snippets) }
    }
}

private struct HostRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "hosts"

    let id: String
    let alias: String
    let hostname: String
    let port: Int
    let username: String
    let transport: String
    let hasPrivateKey: Bool
    let hasPassword: Bool
    let tmuxInstalled: Bool?
    let lastLatencyMilliseconds: Int?
    let lastCheckedAt: Double?
    let operatingSystem: String?
    let packageManager: String?
    var lastMemAvailableKB: Int?
    var lastMemTotalKB: Int?
    var lastDiskAvailableKB: Int?
    var lastDiskTotalKB: Int?
    var lastStatsAt: Double?

    init(_ host: Host) {
        id = host.id
        alias = host.alias
        hostname = host.hostname
        port = Int(host.port)
        username = host.username
        transport = host.transport.rawValue
        hasPrivateKey = host.hasPrivateKey
        hasPassword = host.hasPassword
        tmuxInstalled = host.tmuxInstalled
        lastLatencyMilliseconds = host.lastLatencyMilliseconds
        lastCheckedAt = host.lastCheckedAt?.timeIntervalSince1970
        operatingSystem = host.operatingSystem?.rawValue
        packageManager = host.packageManager?.rawValue
        lastMemAvailableKB = host.lastMemAvailableKB
        lastMemTotalKB = host.lastMemTotalKB
        lastDiskAvailableKB = host.lastDiskAvailableKB
        lastDiskTotalKB = host.lastDiskTotalKB
        lastStatsAt = host.lastStatsAt?.timeIntervalSince1970
    }

    func applying(resources: HostResources) -> HostRecord {
        var updated = self
        if let value = resources.memAvailableKB {
            updated.lastMemAvailableKB = value
        }
        if let value = resources.memTotalKB {
            updated.lastMemTotalKB = value
        }
        if let value = resources.diskAvailableKB {
            updated.lastDiskAvailableKB = value
        }
        if let value = resources.diskTotalKB {
            updated.lastDiskTotalKB = value
        }
        updated.lastStatsAt = Date().timeIntervalSince1970
        return updated
    }

    var domain: Host {
        Host(
            id: id,
            alias: alias,
            hostname: hostname,
            port: UInt16(clamping: port),
            username: username,
            transport: TransportPreference(rawValue: transport) ?? .ssh,
            hasPrivateKey: hasPrivateKey,
            hasPassword: hasPassword,
            tmuxInstalled: tmuxInstalled,
            lastLatencyMilliseconds: lastLatencyMilliseconds,
            lastCheckedAt: lastCheckedAt.map(Date.init(timeIntervalSince1970:)),
            operatingSystem: operatingSystem.flatMap(RemoteOperatingSystem.init(rawValue:)),
            packageManager: packageManager.flatMap(RemotePackageManager.init(rawValue:)),
            lastMemAvailableKB: lastMemAvailableKB,
            lastMemTotalKB: lastMemTotalKB,
            lastDiskAvailableKB: lastDiskAvailableKB,
            lastDiskTotalKB: lastDiskTotalKB,
            lastStatsAt: lastStatsAt.map(Date.init(timeIntervalSince1970:))
        )
    }
}

private struct SnippetRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "snippets"

    let id: String
    let name: String
    let body: String
    let tagsJSON: String
    let runOnSelect: Bool

    init(_ snippet: Snippet) {
        id = snippet.id
        name = snippet.name
        body = snippet.body
        let tagsData = (try? JSONEncoder().encode(snippet.tags)) ?? Data("[]".utf8)
        tagsJSON = String(data: tagsData, encoding: .utf8) ?? "[]"
        runOnSelect = snippet.runOnSelect
    }

    var domain: Snippet {
        let tags = (try? JSONDecoder().decode([String].self, from: Data(tagsJSON.utf8))) ?? []
        return Snippet(id: id, name: name, body: body, tags: tags, runOnSelect: runOnSelect)
    }
}
