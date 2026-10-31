import SwiftUI

struct RootView: View {
    enum Tab: Hashable {
        case sessions
        case hosts
        case settings
    }

    @State private var selection: Tab = .sessions
    @State private var workspaceSection: WorkspaceSection = .sessions
    @State private var splitTerminal: SplitTerminal?
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private let hostRepository: any HostRepository
    private let secretStore: any SecretStoring
    private let knownHostStore: any KnownHostStoring
    private let sshControlPlane: any SSHControlPlane
    private let environmentProbe: any RemoteEnvironmentProbing
    private let hostDeletionService: HostDeletionService
    private let tmuxSessionCache: TmuxSessionCache
    private let tmuxController: any TmuxControlling
    private let tmuxAttachmentCoordinator: TmuxSessionAttachmentCoordinator
    private let keyboardLayoutRepository: any KeyboardLayoutRepository
    private let interactiveTransportFactory: any InteractiveTransportFactory
    private let snippetRepository: any SnippetRepository
    private let historyRepository: any CommandHistoryRepository
    private let diagnostics: any DiagnosticLogging
    private let backupService: ConfigBackupService
    private let pathCompleter: any RemotePathCompleting
    private let preferences: AppPreferencesStore
    private let appLock: AppLockController
    @State private var sessionStore: SessionListStore

    init(
        hostRepository: any HostRepository = EmptyHostRepository(),
        secretStore: any SecretStoring = EmptySecretStore(),
        knownHostStore: any KnownHostStoring = EmptyKnownHostStore(),
        sshControlPlane: any SSHControlPlane = EmptySSHControlPlane(),
        environmentProbe: any RemoteEnvironmentProbing = EmptyRemoteEnvironmentProbe(),
        hostDeletionService: HostDeletionService = HostDeletionService(
            repository: EmptyHostRepository(),
            secretStore: EmptySecretStore()
        ),
        tmuxSessionCache: TmuxSessionCache = TmuxSessionCache(
            controller: EmptyTmuxController(),
            snapshotRepository: EmptySessionSnapshotRepository(),
            cachedSessionRepository: EmptyCachedSessionRepository()
        ),
        tmuxController: any TmuxControlling = EmptyTmuxController(),
        tmuxAttachmentCoordinator: TmuxSessionAttachmentCoordinator = TmuxSessionAttachmentCoordinator(
            controller: EmptyTmuxController()
        ),
        keyboardLayoutRepository: any KeyboardLayoutRepository = EmptyKeyboardLayoutRepository(),
        interactiveTransportFactory: any InteractiveTransportFactory = UnavailableInteractiveTransportFactory(),
        snippetRepository: any SnippetRepository = EmptySnippetRepository(),
        historyRepository: any CommandHistoryRepository = EmptyCommandHistoryRepository(),
        diagnostics: any DiagnosticLogging = EmptyDiagnosticsLogger(),
        backupService: ConfigBackupService = ConfigBackupService(
            hostRepository: EmptyHostRepository(),
            snippetRepository: EmptySnippetRepository(),
            secretStore: EmptySecretStore()
        ),
        pathCompleter: any RemotePathCompleting = EmptyRemotePathCompleter(),
        preferences: AppPreferencesStore = .shared,
        appLock: AppLockController? = nil
    ) {
        self.hostRepository = hostRepository
        self.secretStore = secretStore
        self.knownHostStore = knownHostStore
        self.sshControlPlane = sshControlPlane
        self.environmentProbe = environmentProbe
        self.hostDeletionService = hostDeletionService
        self.tmuxSessionCache = tmuxSessionCache
        self.tmuxController = tmuxController
        self.tmuxAttachmentCoordinator = tmuxAttachmentCoordinator
        self.keyboardLayoutRepository = keyboardLayoutRepository
        self.interactiveTransportFactory = interactiveTransportFactory
        self.snippetRepository = snippetRepository
        self.historyRepository = historyRepository
        self.diagnostics = diagnostics
        self.backupService = backupService
        self.pathCompleter = pathCompleter
        self.preferences = preferences
        self.appLock = appLock ?? AppLockController(preferences: preferences)
        _sessionStore = State(
            initialValue: SessionListStore(
                hostRepository: hostRepository,
                cache: tmuxSessionCache,
                controller: tmuxController,
                attachmentCoordinator: tmuxAttachmentCoordinator,
                preferences: preferences,
                diagnostics: diagnostics
            )
        )
    }

    var body: some View {
        Group {
            if ProcessInfo.processInfo.arguments.contains("-terminal-probe") {
                TerminalProbeView()
            } else if horizontalSizeClass == .regular {
                regularWorkspace
            } else {
                compactTabs
            }
        }
        .onAppear(perform: syncDynamicType)
        .onChange(of: dynamicTypeSize) { _, _ in
            syncDynamicType()
        }
        .background {
            GeometryReader { proxy in
                Color.clear
                    .onAppear { preferences.noteWindowWidth(proxy.size.width) }
                    .onChange(of: proxy.size.width) { _, width in
                        preferences.noteWindowWidth(width)
                    }
            }
        }
    }

    private func syncDynamicType() {
        preferences.noteDynamicTypeMultiplier(
            TerminalFontMetrics.dynamicTypeMultiplier(dynamicTypeSize)
        )
    }

    private var compactTabs: some View {
        TabView(selection: $selection) {
            NavigationStack {
                sessionsView(onOpenTerminal: nil)
            }
            .tabItem { Label("Sessions", systemImage: "rectangle.stack") }
            .tag(Tab.sessions)

            NavigationStack {
                hostsView
            }
            .tabItem { Label("Hosts", systemImage: "server.rack") }
            .tag(Tab.hosts)

            NavigationStack {
                settingsView
            }
            .tabItem { Label("Settings", systemImage: "gearshape") }
            .tag(Tab.settings)
        }
        .muxaAppearance(preferences)
    }

    /// Inner iPhone Duo and iPad: session list beside the live terminal.
    private var regularWorkspace: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                Picker("Section", selection: $workspaceSection) {
                    Text("Sessions").tag(WorkspaceSection.sessions)
                    Text("Hosts").tag(WorkspaceSection.hosts)
                    Text("Settings").tag(WorkspaceSection.settings)
                }
                .pickerStyle(.segmented)
                .padding(12)
                .accessibilityIdentifier("workspace.section")
                NavigationStack {
                    switch workspaceSection {
                    case .sessions:
                        sessionsView { host, item in
                            splitTerminal = SplitTerminal(host: host, item: item)
                        }
                    case .hosts:
                        hostsView
                    case .settings:
                        settingsView
                    }
                }
            }
            .navigationSplitViewColumnWidth(min: 280, ideal: 340, max: 420)
        } detail: {
            NavigationStack {
                if let splitTerminal {
                    RemoteTerminalView(
                        host: splitTerminal.host,
                        item: splitTerminal.item,
                        transportFactory: interactiveTransportFactory,
                        secretStore: secretStore,
                        preferences: preferences,
                        snippetRepository: snippetRepository,
                        historyRepository: historyRepository,
                        pathCompleter: pathCompleter,
                        keyboardLayoutRepository: keyboardLayoutRepository,
                        tmuxController: tmuxController,
                        onLeaveSnapshot: { text in
                            let hostID = splitTerminal.host.id
                            let name = splitTerminal.item.session.name
                            Task {
                                try? await tmuxSessionCache.saveLeaveSnapshot(
                                    hostID: hostID,
                                    sessionName: name,
                                    text: text
                                )
                            }
                        }
                    )
                    .id(splitTerminal.id)
                } else {
                    ContentUnavailableView(
                        "Select a session",
                        systemImage: "rectangle.stack",
                        description: Text("The list stays on the side. The terminal opens here.")
                    )
                    .accessibilityIdentifier("workspace.terminal.empty")
                }
            }
        }
        .navigationSplitViewStyle(.balanced)
        .muxaAppearance(preferences)
    }

    private func sessionsView(
        onOpenTerminal: ((Host, TmuxSessionCacheItem) -> Void)?
    ) -> SessionsView {
        SessionsView(
            store: sessionStore,
            controller: tmuxController,
            interactiveTransportFactory: interactiveTransportFactory,
            secretStore: secretStore,
            preferences: preferences,
            snippetRepository: snippetRepository,
            historyRepository: historyRepository,
            pathCompleter: pathCompleter,
            keyboardLayoutRepository: keyboardLayoutRepository,
            onAddHost: {
                selection = .hosts
                workspaceSection = .hosts
            },
            onOpenTerminal: onOpenTerminal
        )
    }

    private var hostsView: HostsView {
        HostsView(
            repository: hostRepository,
            secretStore: secretStore,
            knownHostStore: knownHostStore,
            sshControlPlane: sshControlPlane,
            environmentProbe: environmentProbe,
            hostDeletionService: hostDeletionService,
            interactiveTransportFactory: interactiveTransportFactory,
            preferences: preferences,
            sessionStore: sessionStore,
            onSessionsReady: {
                selection = .sessions
                workspaceSection = .sessions
            }
        )
    }

    private var settingsView: SettingsView {
        SettingsView(
            preferences: preferences,
            keyboardLayoutRepository: keyboardLayoutRepository,
            snippetRepository: snippetRepository,
            diagnostics: diagnostics,
            backupService: backupService,
            appLock: appLock,
            hostDeletionService: hostDeletionService
        )
    }
}

private enum WorkspaceSection: Hashable {
    case sessions
    case hosts
    case settings
}

private struct SplitTerminal: Identifiable {
    let host: Host
    let item: TmuxSessionCacheItem

    var id: String {
        item.id
    }
}

#Preview {
    RootView()
}
