@testable import Muxa
import XCTest

final class TmuxSessionCacheTests: XCTestCase {
    func testRefreshCapturesAndPersistsFreshSnapshot() async throws {
        let database = try MuxaDatabase(inMemory: true)
        try await GRDBHostRepository(database: database).save(
            Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        )
        let controller = TmuxCacheControllerFixture()
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database),
            staleAfter: 60,
            now: { Date(timeIntervalSince1970: 100) }
        )

        let items = try await cache.refresh(hostID: "host", hostAlias: "Lab", socketPath: nil)
        XCTAssertEqual(items.count, 1)
        XCTAssertFalse(items[0].isStale)
        XCTAssertEqual(items[0].session.snapshotText, "$ echo ready")
        XCTAssertEqual(items[0].session.hostAlias, "Lab")

        let loaded = try await cache.loadCached(hostID: "host")
        XCTAssertEqual(loaded, items)
        XCTAssertEqual(controller.counts(), 1)
    }

    func testCaptureFailureKeepsCachedSnapshotAndMarksItStale() async throws {
        let database = try MuxaDatabase(inMemory: true)
        try await GRDBHostRepository(database: database).save(
            Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        )
        let controller = TmuxCacheControllerFixture()
        let clock = TmuxCacheClock(Date(timeIntervalSince1970: 100))
        let cache = TmuxSessionCache(
            controller: controller,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database),
            staleAfter: 60,
            now: clock.now
        )

        _ = try await cache.refresh(hostID: "host", hostAlias: "Lab", socketPath: nil)
        controller.setCaptureError(true)
        clock.advance(by: 1)

        let items = try await cache.refresh(hostID: "host", hostAlias: "Lab", socketPath: nil)
        XCTAssertEqual(items.count, 1)
        XCTAssertTrue(items[0].isStale)
        XCTAssertEqual(items[0].session.snapshotText, "$ echo ready")

        clock.advance(by: 120)
        let loaded = try await cache.loadCached(hostID: "host")
        XCTAssertTrue(loaded[0].isStale)
    }
}

private final class TmuxCacheControllerFixture: TmuxControlling, @unchecked Sendable {
    private let lock = NSLock()
    private var captureError = false
    private var captureCount = 0

    func discoverSockets(hostID _: String) -> [TmuxServerSocket] {
        []
    }

    func listSessions(hostID: String, socketPath: String?) -> [TmuxSession] {
        [
            TmuxSession(
                hostID: hostID,
                name: "work",
                windowCount: 1,
                attached: true,
                createdAt: nil,
                lastActivityAt: nil,
                snapshotText: nil,
                snapshotUpdatedAt: nil,
                hostAlias: nil,
                socketPath: socketPath,
                windows: []
            )
        ]
    }

    func listWindows(for _: TmuxAttachTarget) -> [TmuxWindow] {
        []
    }

    func listPanes(for _: TmuxAttachTarget, windowIndex _: Int?) -> [TmuxPane] {
        []
    }

    func probeResources(hostID _: String) throws -> HostResources {
        HostResources()
    }

    func capturePane(for _: TmuxAttachTarget, lineLimit _: Int) throws -> String {
        lock.lock()
        defer { lock.unlock() }
        captureCount += 1
        if captureError {
            throw TmuxCacheFixtureError.captureFailed
        }
        return "$ echo ready"
    }

    func createSession(_: TmuxCreateRequest) -> TmuxAttachTarget {
        fatalError("not used")
    }

    func renameSession(_: TmuxAttachTarget, to _: String) {
        fatalError("not used")
    }

    func terminateSession(_: TmuxAttachTarget) {
        fatalError("not used")
    }

    func attachCommand(for _: TmuxAttachTarget) -> String {
        "tmux attach"
    }

    func setCaptureError(_ value: Bool) {
        lock.lock()
        defer { lock.unlock() }
        captureError = value
    }

    func counts() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return captureCount
    }
}

private enum TmuxCacheFixtureError: Error {
    case captureFailed
}

private final class TmuxCacheClock: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Date

    init(_ value: Date) {
        self.value = value
    }

    func now() -> Date {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func advance(by interval: TimeInterval) {
        lock.lock()
        value.addTimeInterval(interval)
        lock.unlock()
    }
}
