import Foundation
import Observation

enum SessionListStoreError: Error, Equatable, Sendable {
    case invalidSessionName
    case hostNotFound
    case recentSessionUnavailable
}

@MainActor
@Observable
final class SessionListStore {
    enum ViewMode: String, CaseIterable, Identifiable {
        case byHost
        case recent

        var id: String {
            rawValue
        }

        var title: String {
            switch self {
            case .byHost: "By Host"
            case .recent: "Recent"
            }
        }
    }

    struct HostGroup: Identifiable {
        let hostID: String
        let title: String
        let items: [TmuxSessionCacheItem]

        var id: String {
            hostID
        }
    }

    private let hostRepository: any HostRepository
    private let cache: TmuxSessionCache
    private let controller: any TmuxControlling
    private let attachmentCoordinator: TmuxSessionAttachmentCoordinator
    private let preferences: AppPreferencesStore?
    private let diagnostics: (any DiagnosticLogging)?
    private var hasStarted = false
    private var hasRefreshed = false
    private var loadGeneration = 0

    private(set) var hosts = [Host]()
    private(set) var items = [TmuxSessionCacheItem]()
    private(set) var isLoading = true
    private(set) var errorMessage: String?
    private(set) var statusMessage: String?
    private(set) var orderRevision = 0
    /// In-process last-opened timestamps. Background/session-list refreshes
    /// must not make an untouched session look recently used.
    private(set) var lastOpened = [String: Date]()
    var viewMode: ViewMode {
        didSet {
            guard oldValue != viewMode else { return }
            preferences?.sessionViewModeRaw = viewMode.rawValue
        }
    }

    init(
        hostRepository: any HostRepository,
        cache: TmuxSessionCache,
        controller: any TmuxControlling,
        attachmentCoordinator: TmuxSessionAttachmentCoordinator,
        preferences: AppPreferencesStore? = nil,
        diagnostics: (any DiagnosticLogging)? = nil
    ) {
        self.hostRepository = hostRepository
        self.cache = cache
        self.controller = controller
        self.attachmentCoordinator = attachmentCoordinator
        self.preferences = preferences
        self.diagnostics = diagnostics
        let storedMode = preferences?.sessionViewModeRaw ?? ViewMode.byHost.rawValue
        viewMode = ViewMode(rawValue: storedMode) ?? .byHost
    }

    var sessionKeys: [String] {
        items.map { ListOrder.sessionKey(hostID: $0.session.hostID, sessionName: $0.session.name) }
    }

    var sortedItems: [TmuxSessionCacheItem] {
        switch viewMode {
        case .byHost:
            orderedItems(items)
        case .recent:
            items.sorted {
                isMoreRecent($0, than: $1)
            }
        }
    }

    var hostGroups: [HostGroup] {
        let grouped = Dictionary(grouping: sortedItems) { $0.session.hostID }
        let fallbackIDs = grouped.keys.sorted { lhs, rhs in
            let left = grouped[lhs]?.first?.session.hostAlias ?? ""
            let right = grouped[rhs]?.first?.session.hostAlias ?? ""
            return left.localizedCaseInsensitiveCompare(right) == .orderedAscending
        }
        let hostIDs = ListOrder.merge(saved: preferences?.hostOrder ?? [], all: fallbackIDs)
        return hostIDs.compactMap { hostID in
            guard let group = grouped[hostID] else { return nil }
            return HostGroup(
                hostID: hostID,
                title: group.first?.session.hostAlias ?? "Host",
                items: orderedItems(group)
            )
        }
    }

    func listWindows(for item: TmuxSessionCacheItem) async throws -> [TmuxWindow] {
        try await controller.listWindows(
            for: TmuxAttachTarget(
                hostID: item.session.hostID,
                socketPath: item.session.socketPath,
                sessionName: item.session.name
            )
        )
    }

    func moveHost(id: String, movingUp: Bool) {
        preferences?.moveHost(id: id, movingUp: movingUp, allIDs: hosts.map(\.id))
        orderRevision += 1
    }

    func moveSession(_ item: TmuxSessionCacheItem, movingUp: Bool) {
        preferences?.moveSession(
            hostID: item.session.hostID,
            sessionName: item.session.name,
            movingUp: movingUp,
            allKeys: sessionKeys
        )
        orderRevision += 1
    }

    func markSessionOpened(_ item: TmuxSessionCacheItem) {
        markSessionOpened(hostID: item.session.hostID, sessionName: item.session.name)
    }

    func markSessionOpened(hostID: String, sessionName: String) {
        let key = ListOrder.sessionKey(hostID: hostID, sessionName: sessionName)
        lastOpened[key] = Date()
        preferences?.lastOpenedSessionKey = key
    }

    func start(openRecent: Bool = false) async -> TmuxSessionCacheItem? {
        guard !hasStarted else { return nil }
        hasStarted = true
        // A host login may already have filled the list. Reloading the cache
        // here would replace that result with an older snapshot.
        if hasRefreshed {
            guard openRecent else { return nil }
            return await launchMostRecent(expectedGeneration: nil)
        }
        let generation = loadGeneration + 1
        await reload()
        guard isCurrent(generation), openRecent else { return nil }
        return await launchMostRecent(expectedGeneration: generation)
    }

    func reload() async {
        let generation = beginLoad()
        do {
            let fetchedHosts = try await hostRepository.fetchAll()
            var loaded = [TmuxSessionCacheItem]()
            for host in fetchedHosts {
                try await loaded.append(contentsOf: cache.loadCached(hostID: host.id))
            }
            guard isCurrent(generation) else { return }
            hosts = fetchedHosts
            items = loaded
            errorMessage = nil
        } catch {
            guard isCurrent(generation) else { return }
            errorMessage = "Sessions could not be loaded."
        }
        finishLoad(generation)
    }

    func refresh(hostIDs: Set<String>? = nil, quiet: Bool = false) async {
        hasRefreshed = true
        let generation = beginLoad()
        var refreshed = [Host]()
        do {
            let fetchedHosts = try await hostRepository.fetchAll()
            let fetchedIDs = Set(fetchedHosts.map(\.id))
            var next = hostIDs == nil
                ? [TmuxSessionCacheItem]()
                : items.filter { fetchedIDs.contains($0.session.hostID) }
            var failed = [String]()
            for host in fetchedHosts {
                if let hostIDs, !hostIDs.contains(host.id) {
                    continue
                }
                do {
                    let fresh = try await cache.refresh(
                        hostID: host.id,
                        hostAlias: host.alias,
                        socketPath: nil
                    )
                    next.removeAll { $0.session.hostID == host.id }
                    next.append(contentsOf: fresh)
                    refreshed.append(host)
                } catch {
                    let cached = try await cache.loadCached(hostID: host.id)
                    next.removeAll { $0.session.hostID == host.id }
                    next.append(contentsOf: cached)
                    if !quiet {
                        failed.append(host.alias)
                    }
                    let detail = String(describing: error)
                    let clipped = detail.count > 500 ? String(detail.prefix(500)) : detail
                    await diagnostics?.log(
                        level: .error,
                        category: "sessions",
                        message: "Refresh failed for \(host.alias): \(clipped)",
                        metadata: [:]
                    )
                }
            }
            guard isCurrent(generation) else { return }
            hosts = fetchedHosts
            items = next
            if quiet {
                // Keep the previous banner so a successful create is not covered by a dead host.
            } else if failed.isEmpty {
                errorMessage = nil
                let counted = hostIDs == nil
                    ? next
                    : next.filter { hostIDs?.contains($0.session.hostID) == true }
                statusMessage = Self.countSentence(counted.count)
            } else {
                errorMessage = "\(failed.joined(separator: ", ")) could not be refreshed."
                statusMessage = nil
            }
        } catch {
            guard isCurrent(generation) else { return }
            if !quiet {
                errorMessage = "Sessions could not be refreshed."
            }
        }
        finishLoad(generation)
        // Best-effort mem/disk sample over the connections just exercised,
        // mirroring the Android probe/attach sampling points.
        sampleResources(for: refreshed, generation: generation)
    }

    func createSession(
        hostID: String,
        name: String,
        template: TmuxSessionTemplate
    ) async throws {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw SessionListStoreError.invalidSessionName
        }
        guard hosts.contains(where: { $0.id == hostID }) else {
            throw SessionListStoreError.hostNotFound
        }
        _ = try await attachmentCoordinator.attachOrCreate(
            hostID: hostID,
            sessionName: trimmedName,
            template: template
        )
        await refresh(hostIDs: [hostID], quiet: true)
    }

    func forgetHost(_ hostID: String) async {
        hosts.removeAll { $0.id == hostID }
        items.removeAll { $0.session.hostID == hostID }
        await cache.forget(hostID: hostID)
    }

    static func countSentence(_ count: Int) -> String {
        switch count {
        case 0:
            "Found no tmux sessions."
        case 1:
            "Found 1 session."
        default:
            "Found \(count) sessions."
        }
    }

    func attachCommand(for item: TmuxSessionCacheItem) -> String {
        controller.attachCommand(
            for: TmuxAttachTarget(
                hostID: item.session.hostID,
                socketPath: item.session.socketPath,
                sessionName: item.session.name
            )
        )
    }

    func host(for item: TmuxSessionCacheItem) -> Host? {
        hosts.first(where: { $0.id == item.session.hostID })
    }

    func renameSession(_ item: TmuxSessionCacheItem, to name: String) async throws {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw SessionListStoreError.invalidSessionName
        }
        try await controller.renameSession(
            TmuxAttachTarget(
                hostID: item.session.hostID,
                socketPath: item.session.socketPath,
                sessionName: item.session.name
            ),
            to: trimmedName
        )
        await refresh(hostIDs: [item.session.hostID], quiet: true)
    }

    func terminateSession(_ item: TmuxSessionCacheItem) async throws {
        try await controller.terminateSession(
            TmuxAttachTarget(
                hostID: item.session.hostID,
                socketPath: item.session.socketPath,
                sessionName: item.session.name
            )
        )
        await refresh(hostIDs: [item.session.hostID], quiet: true)
    }

    func saveLeaveSnapshot(for item: TmuxSessionCacheItem, text: String) async {
        do {
            try await cache.saveLeaveSnapshot(
                hostID: item.session.hostID,
                sessionName: item.session.name,
                text: text
            )
            if let index = items.firstIndex(where: { $0.id == item.id }) {
                var session = items[index].session
                session.snapshotText = SnapshotText.forCard(text) ?? text
                session.snapshotUpdatedAt = Date()
                items[index] = TmuxSessionCacheItem(session: session, isStale: false)
            }
        } catch {
            // Leave-time snapshot is best-effort and must not block dismiss.
        }
    }

    func launchMostRecent() async -> TmuxSessionCacheItem? {
        await launchMostRecent(expectedGeneration: nil)
    }

    private func launchMostRecent(expectedGeneration: Int?) async -> TmuxSessionCacheItem? {
        if items.isEmpty {
            guard expectedGeneration == nil else { return nil }
            await reload()
        }
        if let expectedGeneration, !isCurrent(expectedGeneration) {
            return nil
        }
        let item: TmuxSessionCacheItem?
        if let preferences {
            let key = preferences.lastOpenedSessionKey
            guard !key.isEmpty else { return nil }
            item = items.first {
                ListOrder.sessionKey(hostID: $0.session.hostID, sessionName: $0.session.name) == key
            }
            if item == nil {
                errorMessage = "The recent session is unavailable. Showing the session list."
                return nil
            }
        } else {
            item = items.max(by: { isMoreRecent($1, than: $0) })
        }
        guard let item else { return nil }
        markSessionOpened(item)
        do {
            let target = try await attachmentCoordinator.attachExisting(
                hostID: item.session.hostID,
                sessionName: item.session.name,
                knownSocketPath: item.session.socketPath
            )
            guard target != nil else {
                throw SessionListStoreError.recentSessionUnavailable
            }
            if let expectedGeneration, !isCurrent(expectedGeneration) {
                return nil
            }
            errorMessage = nil
            return item
        } catch {
            if let expectedGeneration {
                if isCurrent(expectedGeneration) {
                    errorMessage = "The recent session is unavailable. Showing the session list."
                }
            } else {
                errorMessage = "The recent session is unavailable. Showing the session list."
            }
            return nil
        }
    }
}

extension SessionListStore {
    /// Samples last-seen memory/disk for hosts whose sessions just refreshed and
    /// patches them onto the host records so group headers can render stats.
    private func sampleResources(for refreshed: [Host], generation: Int) {
        guard !refreshed.isEmpty else { return }
        Task { [weak self] in
            await self?.runResourceSampling(for: refreshed, generation: generation)
        }
    }

    private func runResourceSampling(for refreshed: [Host], generation: Int) async {
        var updatedAny = false
        for host in refreshed {
            guard let resources = try? await controller.probeResources(hostID: host.id),
                  !resources.isEmpty else { continue }
            do {
                try await hostRepository.updateResources(hostID: host.id, resources: resources)
                updatedAny = true
            } catch {
                // Stats are cosmetic; a failed patch must not disturb the list.
            }
        }
        guard updatedAny else { return }
        // Reload hosts so group headers pick up the freshly sampled numbers.
        if let latestHosts = try? await hostRepository.fetchAll(), isCurrent(generation) {
            hosts = latestHosts
        }
    }

    private func beginLoad() -> Int {
        loadGeneration += 1
        isLoading = true
        return loadGeneration
    }

    private func isCurrent(_ generation: Int) -> Bool {
        generation == loadGeneration
    }

    private func finishLoad(_ generation: Int) {
        guard isCurrent(generation) else { return }
        isLoading = false
    }

    private func orderedItems(_ source: [TmuxSessionCacheItem]) -> [TmuxSessionCacheItem] {
        let order = ListOrder.merge(
            saved: preferences?.sessionOrder ?? [],
            all: source.map { ListOrder.sessionKey(hostID: $0.session.hostID, sessionName: $0.session.name) }
        )
        let index = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, $0.offset) })
        return source.sorted { lhs, rhs in
            let left = ListOrder.sessionKey(hostID: lhs.session.hostID, sessionName: lhs.session.name)
            let right = ListOrder.sessionKey(hostID: rhs.session.hostID, sessionName: rhs.session.name)
            let leftIndex = index[left] ?? .max
            let rightIndex = index[right] ?? .max
            if leftIndex != rightIndex {
                return leftIndex < rightIndex
            }
            if lhs.session.hostAlias != rhs.session.hostAlias {
                return (lhs.session.hostAlias ?? "")
                    .localizedCaseInsensitiveCompare(rhs.session.hostAlias ?? "") == .orderedAscending
            }
            return lhs.session.name < rhs.session.name
        }
    }
}
