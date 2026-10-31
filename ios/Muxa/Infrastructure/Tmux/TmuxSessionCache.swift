import Foundation

struct TmuxSessionCacheItem: Equatable, Identifiable, Sendable {
    let session: TmuxSession
    let isStale: Bool

    var id: String {
        session.id
    }
}

actor TmuxSessionCache {
    private let controller: any TmuxControlling
    private let snapshotRepository: any SessionSnapshotRepository
    private let cachedSessionRepository: any CachedSessionRepository
    private let staleAfter: TimeInterval
    private let now: @Sendable () -> Date

    init(
        controller: any TmuxControlling,
        snapshotRepository: any SessionSnapshotRepository,
        cachedSessionRepository: any CachedSessionRepository,
        staleAfter: TimeInterval = 300,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.controller = controller
        self.snapshotRepository = snapshotRepository
        self.cachedSessionRepository = cachedSessionRepository
        self.staleAfter = max(0, staleAfter)
        self.now = now
    }

    func refresh(
        hostID: String,
        hostAlias: String?,
        socketPath: String?,
        lineLimit: Int = 200
    ) async throws -> [TmuxSessionCacheItem] {
        let listedSessions = try await controller.listSessions(hostID: hostID, socketPath: socketPath)
        var sessions = [TmuxSession]()
        var items = [TmuxSessionCacheItem]()
        let checkedAt = now()

        for listedSession in listedSessions {
            var session = listedSession
            session.hostAlias = hostAlias ?? session.hostAlias
            session.socketPath = socketPath ?? session.socketPath
            let target = TmuxAttachTarget(
                hostID: hostID,
                socketPath: session.socketPath,
                sessionName: session.name
            )
            do {
                let captured = try await controller.capturePane(for: target, lineLimit: lineLimit)
                let text = SnapshotText.forStore(captured) ?? captured
                session.snapshotText = SnapshotText.forCard(text) ?? text
                session.snapshotUpdatedAt = checkedAt
                try await snapshotRepository.save(
                    SessionSnapshot(
                        hostID: hostID,
                        sessionName: session.name,
                        text: text,
                        updatedAt: checkedAt
                    )
                )
                items.append(TmuxSessionCacheItem(session: session, isStale: false))
            } catch {
                let cachedSnapshot = try await snapshotRepository.fetch(
                    hostID: hostID,
                    sessionName: session.name
                )
                session.snapshotText = SnapshotText.forCard(cachedSnapshot?.text) ?? cachedSnapshot?.text
                session.snapshotUpdatedAt = cachedSnapshot?.updatedAt
                items.append(TmuxSessionCacheItem(session: session, isStale: true))
            }
            sessions.append(session)
        }

        try await cachedSessionRepository.replace(hostID: hostID, sessions: sessions, listedAt: checkedAt)
        return items
    }

    func loadCached(hostID: String) async throws -> [TmuxSessionCacheItem] {
        let sessions = try await cachedSessionRepository.fetch(hostID: hostID)
        var items = [TmuxSessionCacheItem]()
        let currentDate = now()
        for var session in sessions {
            let snapshot = try await snapshotRepository.fetch(
                hostID: hostID,
                sessionName: session.name
            )
            session.snapshotText = SnapshotText.forCard(snapshot?.text) ?? snapshot?.text
            session.snapshotUpdatedAt = snapshot?.updatedAt
            let isStale: Bool = if let updatedAt = snapshot?.updatedAt {
                currentDate.timeIntervalSince(updatedAt) > staleAfter
            } else {
                true
            }
            items.append(TmuxSessionCacheItem(session: session, isStale: isStale))
        }
        return items
    }

    func forget(hostID: String) async {
        try? await cachedSessionRepository.deleteAll(hostID: hostID)
        try? await snapshotRepository.deleteAll(hostID: hostID)
    }

    func saveLeaveSnapshot(hostID: String, sessionName: String, text: String) async throws {
        let stored = SnapshotText.forStore(text) ?? text
        guard SnapshotText.isUseful(stored) || !stored.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        try await snapshotRepository.save(
            SessionSnapshot(
                hostID: hostID,
                sessionName: sessionName,
                text: stored,
                updatedAt: now()
            )
        )
    }
}
