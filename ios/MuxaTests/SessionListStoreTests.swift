@testable import Muxa
import XCTest

@MainActor
final class SessionListStoreTests: XCTestCase {
    func testRefreshLoadsSessionsAndSupportsRecentSorting() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        try await hosts.save(Host(id: "host-a", alias: "Alpha", hostname: "a.invalid", username: "dev"))
        try await hosts.save(Host(id: "host-b", alias: "Beta", hostname: "b.invalid", username: "dev"))
        let controller = SessionListStoreTmuxFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database),
            now: { Date(timeIntervalSince1970: 100) }
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )

        await store.refresh()

        XCTAssertEqual(store.statusMessage, "Found 2 sessions.")
        XCTAssertEqual(store.items.map(\.session.hostID), ["host-a", "host-b"])
        XCTAssertEqual(store.items.map(\.session.hostAlias), ["Alpha", "Beta"])
        XCTAssertEqual(store.hostGroups.map(\.title), ["Alpha", "Beta"])

        store.viewMode = .recent
        XCTAssertEqual(store.sortedItems.map(\.session.name), ["latest", "oldest"])

        let recent = await store.launchMostRecent()
        XCTAssertEqual(recent?.session.name, "latest")
    }

    func testRefreshSamplesHostResourcesOntoHostRecords() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        try await hosts.save(Host(id: "host-a", alias: "Alpha", hostname: "a.invalid", username: "dev"))
        let controller = SessionListStoreTmuxFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )

        await store.refresh()
        XCTAssertEqual(store.statusMessage, "Found 1 session.")

        // Sampling is fire-and-forget so the list renders first; wait for it.
        var waited = 0
        while await controller.sampledHostIDs.isEmpty, waited < 500 {
            try await Task.sleep(nanoseconds: 10_000_000)
            waited += 1
        }
        let sampled = await controller.sampledHostIDs
        XCTAssertEqual(sampled, ["host-a"])

        let stored = try await hosts.fetch(id: "host-a")
        XCTAssertEqual(stored?.lastMemAvailableKB, 1_258_291)
        XCTAssertEqual(stored?.lastMemTotalKB, 16_384_000)
        XCTAssertEqual(stored?.lastDiskAvailableKB, 50_331_648)
        XCTAssertNotNil(stored?.lastStatsAt)
    }

    func testRefreshWinsOverAnInFlightInitialReload() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let host = Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        try await GRDBHostRepository(database: database).save(host)
        let hosts = BlockingHostRepository(hosts: [host])
        let controller = SessionListStoreTmuxFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )

        let initialReload = Task { await store.reload() }
        await hosts.waitForFirstFetch()
        await store.refresh()
        await hosts.releaseFirstFetch()
        await initialReload.value

        XCTAssertEqual(store.items.map(\.session.name), ["latest"])
        XCTAssertFalse(store.isLoading)
    }

    func testRecentLaunchFallsBackToListWhenCachedSessionIsGone() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let host = Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        try await hosts.save(host)
        let cached = TmuxSession(
            hostID: host.id,
            name: "gone",
            windowCount: 1,
            attached: false,
            createdAt: nil,
            lastActivityAt: Date(timeIntervalSince1970: 100),
            snapshotText: "cached",
            snapshotUpdatedAt: Date(timeIntervalSince1970: 100),
            hostAlias: host.alias,
            socketPath: nil,
            windows: []
        )
        try await GRDBCachedSessionRepository(database: database).replace(
            hostID: host.id,
            sessions: [cached],
            listedAt: Date(timeIntervalSince1970: 100)
        )
        try await GRDBSessionSnapshotRepository(database: database).save(
            SessionSnapshot(
                hostID: host.id,
                sessionName: cached.name,
                text: "cached",
                updatedAt: Date(timeIntervalSince1970: 100)
            )
        )
        let controller = SessionListStoreTmuxFixture(existingSessions: [])
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )

        await store.reload()
        let launched = await store.launchMostRecent()

        XCTAssertNil(launched)
        XCTAssertEqual(store.errorMessage, "The recent session is unavailable. Showing the session list.")
    }

    func testCreateSessionPassesSelectedTemplateCommand() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let host = Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        try await hosts.save(host)
        let controller = SessionListStoreTmuxFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )
        await store.refresh()

        try await store.createSession(
            hostID: host.id,
            name: "codex-work",
            template: TmuxSessionTemplate(kind: .codex)
        )

        let request = await controller.createdRequest()
        XCTAssertEqual(request?.sessionName, "codex-work")
        XCTAssertEqual(request?.initialCommand, "codex")
    }

    func testHostAndSessionOrderFollowsSavedPreferences() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        try await hosts.save(Host(id: "host-a", alias: "Alpha", hostname: "a.invalid", username: "dev"))
        try await hosts.save(Host(id: "host-b", alias: "Beta", hostname: "b.invalid", username: "dev"))
        guard let defaults = UserDefaults(suiteName: "muxa.session-order.\(UUID().uuidString)") else {
            return XCTFail("Could not create a test defaults suite.")
        }
        let preferences = AppPreferencesStore(defaults: defaults)
        preferences.hostOrder = ["host-b", "host-a"]
        let controller = SessionListStoreTmuxFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller),
            preferences: preferences
        )
        await store.refresh()

        XCTAssertEqual(store.hostGroups.map(\.hostID), ["host-b", "host-a"])
        store.moveHost(id: "host-a", movingUp: true)
        XCTAssertEqual(store.hostGroups.map(\.hostID), ["host-a", "host-b"])
    }

    func testCreateSessionRefreshStaysQuietWhenAnotherHostIsDead() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let live = Host(id: "live", alias: "Live", hostname: "live.invalid", username: "dev")
        let dead = Host(id: "dead", alias: "Dead", hostname: "dead.invalid", username: "dev")
        try await hosts.save(live)
        try await hosts.save(dead)
        let controller = SessionListStoreTmuxFixture(existingSessions: ["latest"], failingHostIDs: ["dead"])
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let store = SessionListStore(
            hostRepository: hosts,
            cache: cache,
            controller: controller,
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: controller)
        )
        await store.reload()

        try await store.createSession(hostID: live.id, name: "workspace", template: TmuxSessionTemplate(kind: .empty))

        XCTAssertNil(store.errorMessage)
        XCTAssertTrue(store.items.contains(where: { $0.session.hostID == live.id }))
    }
}

private actor BlockingHostRepository: HostRepository {
    private let values: [Host]
    private var shouldBlockFirstFetch = true
    private var firstFetchStarted = false
    private var releaseContinuation: CheckedContinuation<Void, Never>?

    init(hosts: [Host]) {
        values = hosts
    }

    func observeAll() async throws -> AsyncStream<[Host]> {
        AsyncStream { continuation in
            continuation.yield(values)
            continuation.finish()
        }
    }

    func fetchAll() async throws -> [Host] {
        if shouldBlockFirstFetch {
            shouldBlockFirstFetch = false
            firstFetchStarted = true
            await withCheckedContinuation { continuation in
                releaseContinuation = continuation
            }
        }
        return values
    }

    func fetch(id: String) async throws -> Host? {
        values.first(where: { $0.id == id })
    }

    func save(_: Host) async throws {}
    func updateResources(hostID _: String, resources _: HostResources) async throws {}
    func delete(id _: String) async throws {}
    func deleteAll() async throws {}

    func waitForFirstFetch() async {
        while !firstFetchStarted {
            await Task.yield()
        }
    }

    func releaseFirstFetch() {
        releaseContinuation?.resume()
        releaseContinuation = nil
    }
}

private actor SessionListStoreTmuxFixture: TmuxControlling {
    private var request: TmuxCreateRequest?
    private let existingSessions: [String]
    private let failingHostIDs: Set<String>
    private(set) var sampledHostIDs = [String]()

    init(existingSessions: [String] = ["oldest", "latest"], failingHostIDs: Set<String> = []) {
        self.existingSessions = existingSessions
        self.failingHostIDs = failingHostIDs
    }

    func probeResources(hostID: String) async throws -> HostResources {
        sampledHostIDs.append(hostID)
        return HostResources(
            memAvailableKB: 1_258_291,
            memTotalKB: 16_384_000,
            diskAvailableKB: 50_331_648,
            diskTotalKB: 104_857_600
        )
    }

    func discoverSockets(hostID _: String) async throws -> [TmuxServerSocket] {
        []
    }

    func listSessions(hostID: String, socketPath: String?) async throws -> [TmuxSession] {
        if failingHostIDs.contains(hostID) {
            throw SessionListStoreError.hostNotFound
        }
        let isAlpha = hostID == "host-a"
        let name = isAlpha ? "oldest" : "latest"
        guard existingSessions.contains(name) else { return [] }
        return [
            TmuxSession(
                hostID: hostID,
                name: name,
                windowCount: 1,
                attached: false,
                createdAt: nil,
                lastActivityAt: Date(timeIntervalSince1970: isAlpha ? 10 : 90),
                snapshotText: nil,
                snapshotUpdatedAt: nil,
                hostAlias: nil,
                socketPath: socketPath,
                windows: []
            )
        ]
    }

    func listWindows(for _: TmuxAttachTarget) async throws -> [TmuxWindow] {
        []
    }

    func listPanes(for _: TmuxAttachTarget, windowIndex _: Int?) async throws -> [TmuxPane] {
        []
    }

    func capturePane(for _: TmuxAttachTarget, lineLimit _: Int) async throws -> String {
        "snapshot"
    }

    func createSession(_ request: TmuxCreateRequest) async throws -> TmuxAttachTarget {
        self.request = request
        return TmuxAttachTarget(
            hostID: request.hostID,
            socketPath: request.socketPath,
            sessionName: request.sessionName
        )
    }

    func renameSession(_: TmuxAttachTarget, to _: String) async throws {}
    func terminateSession(_: TmuxAttachTarget) async throws {}

    nonisolated func attachCommand(for target: TmuxAttachTarget) -> String {
        TmuxCommandBuilder().attach(target: target)
    }

    func createdRequest() -> TmuxCreateRequest? {
        request
    }
}
