import SwiftUI

struct HostsView: View {
    @State private var store: HostListStore
    @State private var editor: HostEditorPresentation?
    @State private var connectionPrompt: ConnectionPrompt?
    @State private var connectionMessage: String?
    @State private var deleteCandidate: Host?
    @State private var revealSessionsOnDismiss = false
    private let repository: any HostRepository
    private let secretStore: any SecretStoring
    private let knownHostStore: any KnownHostStoring
    private let sshControlPlane: any SSHControlPlane
    private let environmentProbe: any RemoteEnvironmentProbing
    private let hostDeletionService: HostDeletionService
    private let interactiveTransportFactory: any InteractiveTransportFactory
    private let preferences: AppPreferencesStore
    private let sessionStore: SessionListStore
    private let onSessionsReady: () -> Void
    @State private var installHost: Host?
    @State private var installSnapshot: RemoteEnvironmentSnapshot?
    @State private var installLaunch: RemoteTerminalLaunch?

    init(
        repository: any HostRepository,
        secretStore: any SecretStoring,
        knownHostStore: any KnownHostStoring,
        sshControlPlane: any SSHControlPlane,
        environmentProbe: any RemoteEnvironmentProbing,
        hostDeletionService: HostDeletionService,
        interactiveTransportFactory: any InteractiveTransportFactory = UnavailableInteractiveTransportFactory(),
        preferences: AppPreferencesStore = .shared,
        sessionStore: SessionListStore? = nil,
        onSessionsReady: @escaping () -> Void = {}
    ) {
        self.repository = repository
        self.secretStore = secretStore
        self.knownHostStore = knownHostStore
        self.sshControlPlane = sshControlPlane
        self.environmentProbe = environmentProbe
        self.hostDeletionService = hostDeletionService
        self.interactiveTransportFactory = interactiveTransportFactory
        self.preferences = preferences
        self.sessionStore = sessionStore ?? SessionListStore(
            hostRepository: repository,
            cache: TmuxSessionCache(
                controller: EmptyTmuxController(),
                snapshotRepository: EmptySessionSnapshotRepository(),
                cachedSessionRepository: EmptyCachedSessionRepository()
            ),
            controller: EmptyTmuxController(),
            attachmentCoordinator: TmuxSessionAttachmentCoordinator(controller: EmptyTmuxController())
        )
        self.onSessionsReady = onSessionsReady
        _store = State(initialValue: HostListStore(repository: repository))
    }

    var body: some View {
        List {
            if let errorMessage = store.errorMessage {
                Section {
                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("hosts.error")
                }
            }
            ForEach(orderedHosts) { host in
                HostRow(
                    host: host,
                    onEdit: { editor = HostEditorPresentation(host: host) },
                    onTest: { testConnection(host) },
                    onProbe: { probeHost(host) },
                    onDelete: { deleteCandidate = host },
                    onInstall: { installHost = host }
                )
                .accessibilityIdentifier("hosts.row.\(host.id)")
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        deleteCandidate = host
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                    .accessibilityIdentifier("hosts.delete.\(host.id)")
                }
            }
            .onMove { offsets, destination in
                var ids = orderedHosts.map(\.id)
                ids.move(fromOffsets: offsets, toOffset: destination)
                preferences.hostOrder = ids
            }
            .muxaListRow()
        }
        .listStyle(.insetGrouped)
        .overlay {
            if store.isLoading {
                ProgressView()
            } else if store.hosts.isEmpty, store.errorMessage == nil {
                ContentUnavailableView(
                    "No Hosts",
                    systemImage: "server.rack",
                    description: Text("Add a host to discover its tmux sessions.")
                )
                .accessibilityIdentifier("hosts.empty")
            }
        }
        .refreshable {
            await store.reload()
        }
        .task {
            await store.start()
        }
        .navigationTitle("Hosts")
        .muxaScreen()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                EditButton()
                    .accessibilityIdentifier("hosts.edit")
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    editor = HostEditorPresentation(host: nil)
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add Host")
                .accessibilityIdentifier("hosts.add")
            }
        }
        .sheet(item: $installHost) { host in
            NavigationStack {
                InstallWizardView(
                    host: host,
                    snapshot: installSnapshot,
                    onOpenInstallTerminal: { command in
                        installLaunch = RemoteTerminalLaunch(
                            host: host,
                            title: "Install · \(host.alias)",
                            command: "bash -l",
                            sessionName: nil,
                            initialSnapshot: nil,
                            prefill: command
                        )
                    },
                    onReprobe: { probeHost(host) }
                )
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { installHost = nil }
                    }
                }
            }
        }
        .fullScreenCover(item: $installLaunch) { launch in
            NavigationStack {
                RemoteTerminalView(
                    launch: launch,
                    transportFactory: interactiveTransportFactory,
                    secretStore: secretStore,
                    preferences: preferences
                )
            }
        }
        .sheet(item: $editor) { presentation in
            NavigationStack {
                HostEditorView(
                    host: presentation.host,
                    repository: repository,
                    secretStore: secretStore
                ) { host in
                    editor = nil
                    guard !isUITesting else { return }
                    testConnection(host)
                }
            }
        }
        .alert(item: $connectionPrompt) { prompt in
            switch prompt {
            case let .firstConnection(host, identity, action):
                Alert(
                    title: Text("Verify host key"),
                    message: Text(hostKeyMessage(host: host, identity: identity)),
                    primaryButton: .default(Text("Trust and connect")) {
                        Task { await acceptFirstAndRetry(host: host, identity: identity, action: action) }
                    },
                    secondaryButton: .cancel()
                )
            case let .mismatch(host, expected, actual, action):
                Alert(
                    title: Text("Host key changed"),
                    message: Text(
                        "Expected \(expected.sha256Fingerprint), received \(actual.sha256Fingerprint)."
                    ),
                    primaryButton: .destructive(Text("Replace and connect")) {
                        Task { await replaceAndRetry(host: host, identity: actual, action: action) }
                    },
                    secondaryButton: .cancel()
                )
            }
        }
        .alert(
            "Connection",
            isPresented: Binding(
                get: { connectionMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        finishConnectionAlert()
                    }
                }
            )
        ) {
            Button("OK") { finishConnectionAlert() }
        } message: {
            Text(connectionMessage ?? "")
        }
        .confirmationDialog(
            "Delete \(deleteCandidate?.alias ?? "host")?",
            isPresented: Binding(
                get: { deleteCandidate != nil },
                set: {
                    if !$0 {
                        deleteCandidate = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Host", role: .destructive) {
                guard let host = deleteCandidate else { return }
                deleteCandidate = nil
                Task {
                    do {
                        try await hostDeletionService.delete(hostID: host.id)
                        await sessionStore.forgetHost(host.id)
                        revealSessionsOnDismiss = false
                        connectionMessage = "Deleted \(host.alias)."
                        await store.reload()
                    } catch {
                        connectionMessage = "Host could not be deleted safely."
                    }
                }
            }
            Button("Cancel", role: .cancel) {
                deleteCandidate = nil
            }
        }
    }
}

private extension HostsView {
    var orderedHosts: [Host] {
        let ids = ListOrder.merge(saved: preferences.hostOrder, all: store.hosts.map(\.id))
        return ids.compactMap { id in store.hosts.first(where: { $0.id == id }) }
    }

    var isUITesting: Bool {
        ProcessInfo.processInfo.arguments.contains("-ui-testing")
    }

    func finishConnectionAlert() {
        let reveal = revealSessionsOnDismiss
        connectionMessage = nil
        revealSessionsOnDismiss = false
        if reveal {
            onSessionsReady()
        }
    }

    func testConnection(_ host: Host) {
        Task {
            do {
                let handle = try await sshControlPlane.connect(to: host)
                let healthy = await sshControlPlane.isHealthy(handle)
                await sshControlPlane.disconnect(handle)
                if healthy {
                    await loadSessions(for: host, intro: "Connected to \(host.alias).")
                } else {
                    revealSessionsOnDismiss = false
                    connectionMessage = "The connection closed early."
                }
            } catch let error as SSHControlPlaneError {
                switch error {
                case let .hostKeyConfirmationRequired(identity):
                    connectionPrompt = .firstConnection(host: host, identity: identity, action: .test)
                case let .hostKeyMismatch(expected, actual):
                    connectionPrompt = .mismatch(
                        host: host,
                        expected: expected,
                        actual: actual,
                        action: .test
                    )
                default:
                    connectionMessage = error.userMessage
                }
            } catch {
                connectionMessage = "Connection failed."
            }
        }
    }

    func probeHost(_ host: Host) {
        Task {
            do {
                let snapshot = try await environmentProbe.probe(host: host)
                var updated = host
                updated.tmuxInstalled = snapshot.tmuxInstalled
                updated.lastLatencyMilliseconds = snapshot.latencyMilliseconds
                updated.lastCheckedAt = snapshot.checkedAt
                updated.operatingSystem = snapshot.operatingSystem
                updated.packageManager = snapshot.packageManager
                updated = updated.applying(resources: snapshot.resources)
                try await repository.save(updated)
                installSnapshot = snapshot
                if !snapshot.tmuxInstalled {
                    installHost = updated
                    revealSessionsOnDismiss = false
                    connectionMessage = "Probe complete: \(snapshot.summary). Install packages to continue."
                } else {
                    await loadSessions(for: updated, intro: "Probe complete: \(snapshot.summary).")
                }
            } catch let error as SSHControlPlaneError {
                switch error {
                case let .hostKeyConfirmationRequired(identity):
                    connectionPrompt = .firstConnection(host: host, identity: identity, action: .probe)
                case let .hostKeyMismatch(expected, actual):
                    connectionPrompt = .mismatch(host: host, expected: expected, actual: actual, action: .probe)
                default:
                    connectionMessage = error.userMessage
                }
            } catch {
                connectionMessage = "Environment probe failed."
            }
        }
    }

    func acceptFirstAndRetry(
        host: Host,
        identity: HostKeyIdentity,
        action: HostConnectionAction
    ) async {
        do {
            try await knownHostStore.acceptFirst(identity, seenAt: Date())
            retry(host: host, action: action)
        } catch {
            connectionMessage = "The host key could not be saved."
        }
    }

    func replaceAndRetry(
        host: Host,
        identity: HostKeyIdentity,
        action: HostConnectionAction
    ) async {
        do {
            try await knownHostStore.replaceAfterConfirmation(identity, seenAt: Date())
            retry(host: host, action: action)
        } catch {
            connectionMessage = "The host key could not be replaced."
        }
    }

    func loadSessions(for host: Host, intro: String) async {
        await sessionStore.refresh(hostIDs: [host.id])
        if let errorMessage = sessionStore.errorMessage {
            revealSessionsOnDismiss = false
            connectionMessage = "\(intro) \(errorMessage)"
            return
        }
        let countText = sessionStore.statusMessage ?? "Found no tmux sessions."
        revealSessionsOnDismiss = true
        connectionMessage = "\(intro) \(countText)"
    }

    func retry(host: Host, action: HostConnectionAction) {
        switch action {
        case .test:
            testConnection(host)
        case .probe:
            probeHost(host)
        }
    }

    func hostKeyMessage(host: Host, identity: HostKeyIdentity) -> String {
        "\(host.hostname):\(host.port) presented \(identity.keyType) with fingerprint \(identity.sha256Fingerprint)."
    }
}

private enum ConnectionPrompt: Identifiable {
    case firstConnection(host: Host, identity: HostKeyIdentity, action: HostConnectionAction)
    case mismatch(
        host: Host,
        expected: HostKeyIdentity,
        actual: HostKeyIdentity,
        action: HostConnectionAction
    )

    var id: String {
        switch self {
        case let .firstConnection(host, identity, action):
            "first:\(host.id):\(identity.sha256Fingerprint):\(action.rawValue)"
        case let .mismatch(host, _, actual, action):
            "mismatch:\(host.id):\(actual.sha256Fingerprint):\(action.rawValue)"
        }
    }
}

private enum HostConnectionAction: String {
    case test
    case probe
}

private struct HostEditorPresentation: Identifiable {
    let id = UUID()
    let host: Host?
}

private extension SSHControlPlaneError {
    var userMessage: String {
        switch self {
        case let .hostNotFound(host): "Host \(host) was not found."
        case let .missingCredential(kind): "Missing \(kind)."
        case .connectionUnavailable: "The SSH connection is unavailable."
        case .timedOut: "The SSH connection timed out."
        case let .rejected(message): message
        case .hostKeyConfirmationRequired: "The server host key needs confirmation."
        case .hostKeyMismatch: "The server host key changed."
        case .authenticationFailed: "Authentication failed."
        case .dnsResolutionFailed: "The hostname could not be resolved."
        case .connectionRefused: "The server refused the connection."
        case let .remoteFailure(status, message): "Remote command failed (\(status)): \(message)"
        }
    }
}
