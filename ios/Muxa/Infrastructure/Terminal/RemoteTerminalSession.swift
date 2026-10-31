import Combine
import Network
import SwiftUI
import UIKit

// This type intentionally keeps transport, persistence, and terminal input
// lifecycle together so the session state machine remains auditable in one file.
// swiftlint:disable file_length

struct RemoteTerminalLaunch: Equatable, Identifiable, Sendable {
    let id: String
    let host: Host
    let title: String
    let command: String
    let sessionName: String?
    let initialSnapshot: Data?
    let prefill: String?

    init(
        host: Host,
        title: String,
        command: String,
        sessionName: String?,
        initialSnapshot: Data?,
        prefill: String?
    ) {
        id = "\(host.id)|\(title)|\(command)"
        self.host = host
        self.title = title
        self.command = command
        self.sessionName = sessionName
        self.initialSnapshot = initialSnapshot
        self.prefill = prefill
    }
}

@MainActor
final class RemoteTerminalSession: ObservableObject {
    let controller: TerminalSessionController

    @Published private(set) var transportState: InteractiveTransportState = .idle
    @Published private(set) var lastDiagnostic: String?
    @Published private(set) var errorMessage: String?
    @Published private(set) var networkStatus: RemoteNetworkStatus = .unknown
    @Published private(set) var suggestions = [Suggestion]()

    private let launch: RemoteTerminalLaunch
    private let preference: TransportPreference
    private let transportFactory: any InteractiveTransportFactory
    private let secretStore: any SecretStoring
    let preferences: AppPreferencesStore
    private let snippetRepository: (any SnippetRepository)?
    private let historyRepository: (any CommandHistoryRepository)?
    private let pathCompleter: (any RemotePathCompleting)?
    private let keyboardLayoutRepository: (any KeyboardLayoutRepository)?
    private let tmuxController: (any TmuxControlling)?
    private let tmuxTarget: TmuxAttachTarget?
    private let checkpointMaxAge: TimeInterval = 24 * 60 * 60
    private var transport: (any InteractiveTransport)?
    private var eventTask: Task<Void, Never>?
    private var suggestTask: Task<Void, Never>?
    private var hasStarted = false
    private var backgroundCheckpoint: InteractiveTransportCheckpoint?
    private var lineBuffer = ""
    private var pagingTask: Task<Void, Never>?
    private var tmuxHistoryPaging = false
    private var tmuxCopyModeDepth = 0
    private var lastTmuxPageAt = Date.distantPast
    /// Latest viewport size waiting to be sent. Overlapping keyboard and font
    /// changes must not let an older resize finish last and leave the pane short.
    private var pendingResize: TerminalGeometry?
    private var resizeTask: Task<Void, Never>?
    private let pathMonitor = NWPathMonitor()
    private let pathMonitorQueue = DispatchQueue(label: "app.mtmux.ios.remote-terminal-network")

    var host: Host {
        launch.host
    }

    var sessionName: String? {
        launch.sessionName
    }

    convenience init(
        host: Host,
        target: TmuxAttachTarget,
        preference: TransportPreference,
        initialSnapshot: Data?,
        transportFactory: any InteractiveTransportFactory,
        secretStore: any SecretStoring,
        preferences: AppPreferencesStore = .shared,
        snippetRepository: (any SnippetRepository)? = nil,
        historyRepository: (any CommandHistoryRepository)? = nil,
        pathCompleter: (any RemotePathCompleting)? = nil,
        keyboardLayoutRepository: (any KeyboardLayoutRepository)? = nil,
        tmuxController: (any TmuxControlling)? = nil
    ) {
        self.init(
            launch: RemoteTerminalLaunch(
                host: host,
                title: target.sessionName,
                command: TmuxCommandBuilder().attach(target: target),
                sessionName: target.sessionName,
                initialSnapshot: initialSnapshot,
                prefill: nil
            ),
            preference: preference,
            transportFactory: transportFactory,
            secretStore: secretStore,
            preferences: preferences,
            snippetRepository: snippetRepository,
            historyRepository: historyRepository,
            pathCompleter: pathCompleter,
            keyboardLayoutRepository: keyboardLayoutRepository,
            tmuxController: tmuxController,
            tmuxTarget: target
        )
    }

    init(
        launch: RemoteTerminalLaunch,
        preference: TransportPreference,
        transportFactory: any InteractiveTransportFactory,
        secretStore: any SecretStoring,
        preferences: AppPreferencesStore = .shared,
        snippetRepository: (any SnippetRepository)? = nil,
        historyRepository: (any CommandHistoryRepository)? = nil,
        pathCompleter: (any RemotePathCompleting)? = nil,
        keyboardLayoutRepository: (any KeyboardLayoutRepository)? = nil,
        tmuxController: (any TmuxControlling)? = nil,
        tmuxTarget: TmuxAttachTarget? = nil
    ) {
        self.launch = launch
        self.preference = preference
        self.transportFactory = transportFactory
        self.secretStore = secretStore
        self.preferences = preferences
        self.snippetRepository = snippetRepository
        self.historyRepository = historyRepository
        self.pathCompleter = pathCompleter
        self.keyboardLayoutRepository = keyboardLayoutRepository
        self.tmuxController = tmuxController
        self.tmuxTarget = tmuxTarget
        controller = TerminalSessionController(scrollback: preferences.bufferLines)
        controller.onInput = { [weak self] data in
            self?.handleInput(data)
        }
        controller.onSendDraft = { [weak self] text in
            self?.sendDraft(text) ?? false
        }
        controller.onComposeHistoryChange = { [weak self] items in
            self?.preferences.composeHistory = items
        }
        controller.onComposeEnabledChange = { [weak self] enabled in
            self?.preferences.composeBoxEnabled = enabled
        }
        controller.onShortcutOrderChange = { [weak self] ids in
            self?.preferences.shortcutBarOrder = ids
        }
        controller.applyShortcutOrder(preferences.shortcutBarOrder)
        controller.shortcutBar.composeEnabled = preferences.composeBoxEnabled
        controller.shortcutBar.composeHistory = preferences.composeHistory
        applyAppearance()
        pathMonitor.pathUpdateHandler = { [weak self] path in
            let status: RemoteNetworkStatus = switch path.status {
            case .satisfied:
                .available
            case .unsatisfied, .requiresConnection:
                .unavailable
            @unknown default:
                .unknown
            }
            Task { @MainActor in
                self?.networkStatus = status
            }
        }
        pathMonitor.start(queue: pathMonitorQueue)
    }

    var title: String {
        launch.title
    }

    var statusText: String {
        switch transportState {
        case .idle: "Idle"
        case .connecting: "Connecting…"
        case .connected: "SSH"
        case .frozen: "Paused"
        case .reconnecting: "Reconnecting…"
        case .closed: "Closed"
        case .failed: "Failed"
        }
    }

    func applyAppearance() {
        controller.applyAppearance(
            scheme: preferences.terminalScheme,
            fontSize: preferences.resolvedFontSize,
            scrollback: preferences.bufferLines
        )
    }

    func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        UIApplication.shared.isIdleTimerDisabled = preferences.preventSleeping
        await applyKeyboardLayout()
        applyAppearance()
        let transport = await transportFactory.makeTransport(for: preference)
        self.transport = transport
        let stream = await transport.events()
        eventTask = Task { @MainActor [weak self] in
            for await event in stream {
                self?.consume(event)
            }
        }

        let savedCheckpoint = await loadPersistedCheckpoint()
        do {
            try await transport.start(
                InteractiveTransportRequest(
                    host: launch.host,
                    command: launch.command,
                    terminal: preferences.termType,
                    geometry: safeGeometry(controller.geometry),
                    preference: preference,
                    initialSnapshot: launch.initialSnapshot,
                    initialCheckpoint: savedCheckpoint
                )
            )
            let connectedState = await transport.state()
            transportState = connectedState
            if let prefill = launch.prefill, !prefill.isEmpty {
                try? await transport.send(Data(prefill.utf8))
            }
            if savedCheckpoint != nil, case .connected = connectedState {
                await deletePersistedCheckpoint()
                lastDiagnostic = "Restored terminal session"
            }
            scheduleTmuxPagingHint()
        } catch {
            errorMessage = Self.message(for: error)
            transportState = await transport.state()
        }
    }

    func updateGeometry(_ geometry: TerminalGeometry) {
        guard geometry.columns > 0, geometry.rows > 0 else { return }
        // SwiftUI can report the first layout before SSHInteractiveTransport
        // has finished opening its PTY. The start request already carries the
        // latest geometry, so an early resize must be ignored rather than
        // surfacing an "invalid state" error to the user.
        guard case .connected = transportState, transport != nil else { return }
        pendingResize = geometry
        guard resizeTask == nil else { return }
        resizeTask = Task { @MainActor [weak self] in
            await self?.drainPendingResizes()
        }
    }

    private func drainPendingResizes() async {
        while let geometry = pendingResize {
            pendingResize = nil
            guard case .connected = transportState, let transport else { break }
            do {
                try await transport.resize(to: geometry)
            } catch {
                if pendingResize == nil {
                    errorMessage = Self.message(for: error)
                }
            }
        }
        resizeTask = nil
        if pendingResize != nil {
            updateGeometry(pendingResize!)
        }
    }

    func scenePhaseChanged(_ phase: ScenePhase) async {
        switch phase {
        case .background:
            await freezeForBackground()
        case .active:
            await resumeFromBackground()
        case .inactive:
            break
        @unknown default:
            break
        }
    }

    func leaveSnapshotText() -> String? {
        SnapshotText.forStore(controller.visibleSnapshotText())
    }

    /// Handles a one-page finger gesture while an inline CLI is displayed in
    /// tmux's client alternate screen. Returning true prevents the gesture
    /// from being sent as PgUp/PgDn to the CLI itself.
    func handleAltFingerScroll(scrollUp: Bool) -> Bool {
        guard tmuxHistoryPaging, let tmuxController, let tmuxTarget else { return false }
        let now = Date()
        guard now.timeIntervalSince(lastTmuxPageAt) >= 0.28 else { return true }
        lastTmuxPageAt = now
        if scrollUp {
            tmuxCopyModeDepth += 1
        } else {
            tmuxCopyModeDepth = max(0, tmuxCopyModeDepth - 1)
        }
        let depth = tmuxCopyModeDepth
        Task { @MainActor [weak self] in
            guard let self, !Task.isCancelled else { return }
            do {
                if !scrollUp, depth == 0 {
                    try await tmuxController.cancelCopyMode(for: tmuxTarget)
                } else {
                    try await tmuxController.pageHistory(for: tmuxTarget, scrollUp: scrollUp, lines: 18)
                }
            } catch {
                lastDiagnostic = "Remote paging unavailable"
            }
        }
        return true
    }

    func applySuggestion(_ suggestion: Suggestion) {
        let body = ComposeHistory.payload(suggestion.insertion)
        guard !body.isEmpty, transport != nil else { return }
        controller.insertComposerText(body)
        if suggestion.runAfterInsert {
            handleInput(Data("\r".utf8))
        }
        suggestions = []
    }

    func close() async {
        suggestTask?.cancel()
        pagingTask?.cancel()
        eventTask?.cancel()
        eventTask = nil
        pathMonitor.cancel()
        UIApplication.shared.isIdleTimerDisabled = false
        if let transport {
            await transport.close()
            self.transport = nil
        }
        await deletePersistedCheckpoint()
        controller.onInput = nil
    }

    func clearError() {
        errorMessage = nil
    }
}

extension RemoteTerminalSession {
    func applyKeyboardLayout() async {
        guard let keyboardLayoutRepository else { return }
        let layouts = await (try? keyboardLayoutRepository.fetchAll()) ?? []
        let selected = layouts.first(where: { $0.id == preferences.selectedKeyboardLayoutID })
            ?? KeyboardLayout.general
        controller.applyShortcutOrder(preferences.shortcutBarOrder)
        controller.shortcutBar.composeEnabled = preferences.composeBoxEnabled
        controller.shortcutBar.composeHistory = preferences.composeHistory
        controller.setKeyboardLayout(selected)
    }

    private func sendDraft(_ text: String) -> Bool {
        guard transport != nil, !text.isEmpty else { return false }
        controller.insertComposerText(text)
        return true
    }

    private func handleInput(_ data: Data) {
        cancelTmuxCopyModeIfNeeded()
        updateLineBuffer(data)
        send(data)
        scheduleSuggestions()
    }

    private func scheduleTmuxPagingHint() {
        pagingTask?.cancel()
        guard tmuxController != nil, tmuxTarget != nil else { return }
        pagingTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            await self?.refreshTmuxPagingHint()
        }
    }

    private func refreshTmuxPagingHint() async {
        guard let tmuxController, let tmuxTarget else { return }
        guard let metadata = try? await tmuxController.paneScrollMetadata(for: tmuxTarget) else {
            tmuxHistoryPaging = false
            return
        }
        tmuxHistoryPaging = !metadata.isAlternateScreen &&
            metadata.historySize > metadata.paneHeight + 2
        tmuxCopyModeDepth = 0
    }

    private func cancelTmuxCopyModeIfNeeded() {
        guard tmuxCopyModeDepth > 0, let tmuxController, let tmuxTarget else { return }
        tmuxCopyModeDepth = 0
        Task { @MainActor in
            try? await tmuxController.cancelCopyMode(for: tmuxTarget)
        }
    }

    private func updateLineBuffer(_ data: Data) {
        let text = String(data: data, encoding: .utf8) ?? ""
        if text.contains("\r") || text.contains("\n") {
            let command = lineBuffer
            lineBuffer = ""
            suggestions = []
            Task { await recordHistory(command) }
        } else if text == "\u{7f}" || text == "\u{08}" {
            if !lineBuffer.isEmpty {
                lineBuffer.removeLast()
            }
        } else if !text.isEmpty {
            lineBuffer += text
        }
    }

    private func recordHistory(_ command: String) async {
        guard AutocompleteEngine.shouldRecordHistory(command), let historyRepository else { return }
        do {
            try await historyRepository.record(hostID: launch.host.id, command: command, usedAt: Date())
            try await historyRepository.prune(hostID: launch.host.id, keeping: AutocompleteEngine.historyLimit)
        } catch {}
    }

    private func scheduleSuggestions() {
        guard preferences.autocompleteEnabled else {
            suggestions = []
            return
        }
        suggestTask?.cancel()
        let prefix = lineBuffer
        suggestTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(180))
            guard !Task.isCancelled else { return }
            await self?.refreshSuggestions(prefix: prefix)
        }
    }

    private func refreshSuggestions(prefix: String) async {
        let snapshot = controller.visibleSnapshotText()
        let tui = AutocompleteEngine.isTuiMode(
            alternateScreen: controller.isAlternateScreen,
            snapshot: snapshot,
            lineBuffer: prefix
        )
        if tui || prefix.trimmingCharacters(in: .whitespaces).isEmpty {
            suggestions = []
            return
        }
        let snippets = await (try? snippetRepository?.fetchAll()) ?? []
        let history = await (try? historyRepository?.fetchRecent(hostID: launch.host.id, limit: 20)) ?? []
        let paths = await pathCompleter?.complete(host: launch.host, linePrefix: prefix) ?? []
        suggestions = AutocompleteEngine.suggest(
            AutocompleteEngine.Query(
                linePrefix: prefix,
                tuiMode: false,
                snippets: snippets,
                history: history,
                pathHints: paths,
                vars: [
                    "host": launch.host.hostname,
                    "user": launch.host.username,
                    "port": String(launch.host.port),
                    "session": launch.sessionName ?? ""
                ]
            )
        )
    }

    private func send(_ data: Data) {
        guard let transport else { return }
        Task { @MainActor [weak self] in
            do {
                try await transport.send(data)
            } catch {
                self?.errorMessage = Self.message(for: error)
            }
        }
    }

    private func freezeForBackground() async {
        guard let transport, case .connected = transportState else { return }
        do {
            let checkpoint = try await transport.freeze()
            backgroundCheckpoint = checkpoint
            transportState = await transport.state()
            if let checkpoint {
                do {
                    try await savePersistedCheckpoint(checkpoint)
                    lastDiagnostic = "Paused safely in background"
                } catch {
                    lastDiagnostic = "Paused in memory; checkpoint not saved"
                    errorMessage = Self.message(for: error)
                }
            } else {
                lastDiagnostic = "Paused safely in background"
            }
        } catch {
            errorMessage = Self.message(for: error)
        }
    }

    private func resumeFromBackground() async {
        guard let transport, case .frozen = transportState else { return }
        do {
            try await transport.resume(from: backgroundCheckpoint)
            backgroundCheckpoint = nil
            await deletePersistedCheckpoint()
            lastDiagnostic = "Resumed terminal session"
        } catch {
            errorMessage = Self.message(for: error)
        }
    }

    private var checkpointReference: SecretReference {
        SecretReference(
            hostID: launch.host.id,
            kind: .transportCheckpoint,
            identifier: launch.sessionName ?? launch.title
        )
    }

    private func loadPersistedCheckpoint() async -> InteractiveTransportCheckpoint? {
        guard launch.sessionName != nil else { return nil }
        do {
            guard let data = try await secretStore.retrieve(checkpointReference) else {
                return nil
            }
            guard let checkpoint = try? JSONDecoder().decode(
                InteractiveTransportCheckpoint.self,
                from: data
            ) else {
                await deletePersistedCheckpoint()
                return nil
            }
            guard Date().timeIntervalSince(checkpoint.createdAt) <= checkpointMaxAge else {
                await deletePersistedCheckpoint()
                return nil
            }
            return checkpoint
        } catch {
            return nil
        }
    }

    private func savePersistedCheckpoint(_ checkpoint: InteractiveTransportCheckpoint) async throws {
        let data = try JSONEncoder().encode(checkpoint)
        try await secretStore.store(data, for: checkpointReference)
    }

    private func deletePersistedCheckpoint() async {
        try? await secretStore.delete(checkpointReference)
    }

    private func consume(_ event: InteractiveTransportEvent) {
        switch event {
        case let .stateChanged(state):
            transportState = state
            if case let .failed(message) = state {
                errorMessage = message
            }
        case let .received(data):
            controller.feed(data)
        case .synchronized:
            lastDiagnostic = "Synchronized"
        case let .diagnostic(message):
            lastDiagnostic = message
        }
    }

    private func safeGeometry(_ geometry: TerminalGeometry) -> TerminalGeometry {
        TerminalGeometry(
            columns: max(1, geometry.columns),
            rows: max(1, geometry.rows),
            pixelWidth: max(0, geometry.pixelWidth),
            pixelHeight: max(0, geometry.pixelHeight)
        )
    }

    private static func message(for error: Error) -> String {
        if let error = error as? InteractiveTransportUnavailableError {
            return String(describing: error)
        }
        if let error = error as? SSHControlPlaneError {
            return String(describing: error)
        }
        return "The terminal connection could not be established."
    }
}
