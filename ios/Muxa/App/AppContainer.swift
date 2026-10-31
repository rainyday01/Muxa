import Foundation

final class AppContainer: @unchecked Sendable {
    let database: MuxaDatabase
    let hostRepository: any HostRepository
    let secretStore: any SecretStoring
    let knownHostStore: any KnownHostStoring
    let sshControlPlane: any SSHControlPlane
    let connectionManager: ConnectionManager
    let environmentProbe: any RemoteEnvironmentProbing
    let hostDeletionService: HostDeletionService
    let tmuxController: any TmuxControlling
    let tmuxSessionCache: TmuxSessionCache
    let tmuxAttachmentCoordinator: TmuxSessionAttachmentCoordinator
    let keyboardLayoutRepository: any KeyboardLayoutRepository
    let interactiveTransportFactory: any InteractiveTransportFactory
    let snippetRepository: any SnippetRepository
    let historyRepository: any CommandHistoryRepository
    let diagnostics: any DiagnosticLogging
    let backupService: ConfigBackupService
    let pathCompleter: any RemotePathCompleting
    let preferences: AppPreferencesStore
    let desktopPush: any DesktopPushPort
    let commandPalette: any CommandPalettePort

    private init(
        database: MuxaDatabase,
        hostRepository: any HostRepository,
        secretStore: any SecretStoring,
        knownHostStore: any KnownHostStoring,
        sshControlPlane: any SSHControlPlane,
        connectionManager: ConnectionManager,
        environmentProbe: any RemoteEnvironmentProbing,
        hostDeletionService: HostDeletionService,
        tmuxController: any TmuxControlling,
        tmuxSessionCache: TmuxSessionCache,
        tmuxAttachmentCoordinator: TmuxSessionAttachmentCoordinator,
        interactiveTransportFactory: any InteractiveTransportFactory,
        diagnostics: any DiagnosticLogging,
        preferences: AppPreferencesStore
    ) {
        self.database = database
        self.hostRepository = hostRepository
        self.secretStore = secretStore
        self.knownHostStore = knownHostStore
        self.sshControlPlane = sshControlPlane
        self.connectionManager = connectionManager
        self.environmentProbe = environmentProbe
        self.hostDeletionService = hostDeletionService
        self.tmuxController = tmuxController
        self.tmuxSessionCache = tmuxSessionCache
        self.tmuxAttachmentCoordinator = tmuxAttachmentCoordinator
        keyboardLayoutRepository = GRDBKeyboardLayoutRepository(database: database)
        self.interactiveTransportFactory = interactiveTransportFactory
        snippetRepository = GRDBSnippetRepository(database: database)
        historyRepository = GRDBCommandHistoryRepository(database: database)
        self.diagnostics = diagnostics
        backupService = ConfigBackupService(
            hostRepository: hostRepository,
            snippetRepository: snippetRepository,
            secretStore: secretStore
        )
        pathCompleter = SSHRemotePathCompleter(connectionManager: connectionManager)
        self.preferences = preferences
        desktopPush = NoOpDesktopPushPort()
        commandPalette = NoOpCommandPalettePort()
    }

    @MainActor
    static func live() throws -> AppContainer {
        let fileManager = FileManager.default
        let baseURL = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let directory = baseURL.appendingPathComponent("Muxa", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let database = try MuxaDatabase(path: directory.appendingPathComponent("muxa.sqlite").path)
        let secretStore = try ValetSecretStore()
        let knownHostStore = GRDBKnownHostsStore(database: database)
        let sshControlPlane = LiveSSHControlPlane(secretStore: secretStore, knownHosts: knownHostStore)
        let interactiveTransportFactory = LiveInteractiveTransportFactory(
            sshController: sshControlPlane
        )
        let connectionManager = ConnectionManager(controlPlane: sshControlPlane)
        let hostRepository = GRDBHostRepository(database: database)
        let hostDeletionService = HostDeletionService(
            repository: hostRepository,
            secretStore: secretStore,
            database: database
        )
        let diagnostics = DiagnosticsLogger()
        let tmuxController = SSHCommandTmuxController(
            hostRepository: hostRepository,
            connectionManager: connectionManager,
            diagnostics: diagnostics
        )
        let tmuxSessionCache = TmuxSessionCache(
            controller: tmuxController,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let tmuxAttachmentCoordinator = TmuxSessionAttachmentCoordinator(controller: tmuxController)
        return AppContainer(
            database: database,
            hostRepository: hostRepository,
            secretStore: secretStore,
            knownHostStore: knownHostStore,
            sshControlPlane: sshControlPlane,
            connectionManager: connectionManager,
            environmentProbe: SSHRemoteEnvironmentProbe(connectionManager: connectionManager),
            hostDeletionService: hostDeletionService,
            tmuxController: tmuxController,
            tmuxSessionCache: tmuxSessionCache,
            tmuxAttachmentCoordinator: tmuxAttachmentCoordinator,
            interactiveTransportFactory: interactiveTransportFactory,
            diagnostics: diagnostics,
            preferences: .shared
        )
    }

    @MainActor
    static func testing() throws -> AppContainer {
        let database = try MuxaDatabase(inMemory: true)
        let secretStore = InMemorySecretStore()
        let knownHostStore = InMemoryKnownHostStore()
        let sshControlPlane = TestingSSHControlPlane(
            behavior: TestingSSHControlPlane.Behavior(processArguments: ProcessInfo.processInfo.arguments)
        )
        let connectionManager = ConnectionManager(controlPlane: sshControlPlane)
        let hostRepository = GRDBHostRepository(database: database)
        let hostDeletionService = HostDeletionService(
            repository: hostRepository,
            secretStore: secretStore,
            database: database
        )
        let tmuxController = TestingTmuxController()
        let tmuxSessionCache = TmuxSessionCache(
            controller: tmuxController,
            snapshotRepository: GRDBSessionSnapshotRepository(database: database),
            cachedSessionRepository: GRDBCachedSessionRepository(database: database)
        )
        let tmuxAttachmentCoordinator = TmuxSessionAttachmentCoordinator(controller: tmuxController)
        let container = AppContainer(
            database: database,
            hostRepository: hostRepository,
            secretStore: secretStore,
            knownHostStore: knownHostStore,
            sshControlPlane: sshControlPlane,
            connectionManager: connectionManager,
            environmentProbe: TestingRemoteEnvironmentProbe(),
            hostDeletionService: hostDeletionService,
            tmuxController: tmuxController,
            tmuxSessionCache: tmuxSessionCache,
            tmuxAttachmentCoordinator: tmuxAttachmentCoordinator,
            interactiveTransportFactory: UnavailableInteractiveTransportFactory(),
            diagnostics: DiagnosticsLogger(),
            preferences: AppPreferencesStore(
                defaults: UserDefaults(suiteName: "muxa.ios.ui-tests") ?? .standard
            )
        )
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-preloaded-host") {
            let host = Host(
                alias: "Template Host",
                hostname: "example.invalid",
                username: "root",
                hasPassword: true
            )
            Task {
                try? await hostRepository.save(host)
            }
        }
        return container
    }
}

private actor InMemorySecretStore: SecretStoring {
    private var values = [SecretReference: Data]()

    func store(_ value: Data, for reference: SecretReference) throws {
        guard !value.isEmpty else { throw SecretStoreError.invalidValue }
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

private actor InMemoryKnownHostStore: KnownHostStoring {
    private var values = [String: HostKeyIdentity]()

    func identities(hostname: String, port: UInt16) -> [HostKeyIdentity] {
        values.values.filter { $0.hostname == hostname && $0.port == port }
    }

    func verify(_ identity: HostKeyIdentity, seenAt _: Date) -> HostKeyVerification {
        guard let stored = values[key(identity)] else { return .unknown(identity) }
        return stored.sha256Fingerprint == identity.sha256Fingerprint
            ? .trusted(identity)
            : .mismatch(expected: stored, actual: identity)
    }

    func acceptFirst(_ identity: HostKeyIdentity, seenAt _: Date) throws {
        if let stored = values[key(identity)], stored.sha256Fingerprint != identity.sha256Fingerprint {
            throw KnownHostsError.keyChanged(expected: stored, actual: identity)
        }
        values[key(identity)] = identity
    }

    func replaceAfterConfirmation(_ identity: HostKeyIdentity, seenAt _: Date) {
        values[key(identity)] = identity
    }

    func remove(hostname: String, port: UInt16) {
        values = values.filter { $0.value.hostname != hostname || $0.value.port != port }
    }

    private func key(_ identity: HostKeyIdentity) -> String {
        "\(identity.hostname):\(identity.port):\(identity.keyType)"
    }
}

private actor TestingSSHControlPlane: SSHControlPlane {
    enum Behavior: Equatable, Sendable {
        case unknownHostKey
        case hostKeyMismatch
        case authenticationFailure

        init(processArguments: [String]) {
            if processArguments.contains("-ui-testing-host-key-mismatch") {
                self = .hostKeyMismatch
            } else if processArguments.contains("-ui-testing-auth-failure") {
                self = .authenticationFailure
            } else {
                self = .unknownHostKey
            }
        }
    }

    private let behavior: Behavior
    private var connectionAttempts = 0

    init(behavior: Behavior) {
        self.behavior = behavior
    }

    func connect(to _: Host) throws -> SSHConnectionHandle {
        connectionAttempts += 1
        if behavior == .authenticationFailure {
            throw SSHControlPlaneError.authenticationFailed
        }
        if connectionAttempts == 1, behavior == .unknownHostKey {
            throw SSHControlPlaneError.hostKeyConfirmationRequired(
                HostKeyIdentity(
                    hostname: "example.invalid",
                    port: 22,
                    keyType: "ssh-ed25519",
                    sha256Fingerprint: HostKeyFingerprint.sha256(Data("ui-test-key".utf8))
                )
            )
        }
        if connectionAttempts == 1, behavior == .hostKeyMismatch {
            let expected = HostKeyIdentity(
                hostname: "example.invalid",
                port: 22,
                keyType: "ssh-ed25519",
                sha256Fingerprint: HostKeyFingerprint.sha256(Data("expected-ui-test-key".utf8))
            )
            let actual = HostKeyIdentity(
                hostname: expected.hostname,
                port: expected.port,
                keyType: expected.keyType,
                sha256Fingerprint: HostKeyFingerprint.sha256(Data("actual-ui-test-key".utf8))
            )
            throw SSHControlPlaneError.hostKeyMismatch(expected: expected, actual: actual)
        }
        return SSHConnectionHandle(id: UUID(), hostID: "ui-test-host")
    }

    func execute(_: SSHCommandRequest, using _: SSHConnectionHandle) throws -> SSHCommandOutput {
        throw SSHControlPlaneError.connectionUnavailable
    }

    func isHealthy(_: SSHConnectionHandle) -> Bool {
        true
    }

    func disconnect(_: SSHConnectionHandle) {}
}

private actor TestingRemoteEnvironmentProbe: RemoteEnvironmentProbing {
    private var probeAttempts = 0

    func probe(host: Host) throws -> RemoteEnvironmentSnapshot {
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-probe-host-key"), probeAttempts == 0 {
            probeAttempts += 1
            throw SSHControlPlaneError.hostKeyConfirmationRequired(
                HostKeyIdentity(
                    hostname: host.hostname,
                    port: host.port,
                    keyType: "ssh-ed25519",
                    sha256Fingerprint: HostKeyFingerprint.sha256(Data("ui-test-probe-key".utf8))
                )
            )
        }
        probeAttempts += 1
        return RemoteEnvironmentSnapshot(
            operatingSystem: .linux,
            packageManager: .apt,
            tmuxVersion: "tmux 3.4",
            latencyMilliseconds: 12,
            checkedAt: Date(timeIntervalSince1970: 100)
        )
    }
}

private final class TestingTmuxController: TmuxControlling, @unchecked Sendable {
    func discoverSockets(hostID _: String) async throws -> [TmuxServerSocket] {
        [TmuxServerSocket(path: "/tmp/tmux/default", source: "TMPDIR")]
    }

    func listSessions(hostID: String, socketPath: String?) async throws -> [TmuxSession] {
        [
            TmuxSession(
                hostID: hostID,
                name: "demo",
                windowCount: 2,
                attached: true,
                createdAt: Date(timeIntervalSince1970: 100),
                lastActivityAt: Date(timeIntervalSince1970: 100),
                snapshotText: nil,
                snapshotUpdatedAt: nil,
                hostAlias: nil,
                socketPath: socketPath,
                windows: []
            )
        ]
    }

    func listWindows(for target: TmuxAttachTarget) async throws -> [TmuxWindow] {
        [
            TmuxWindow(
                sessionName: target.sessionName,
                index: 0,
                name: "main",
                active: true,
                layout: nil,
                paneCount: 1,
                panes: []
            )
        ]
    }

    func listPanes(for target: TmuxAttachTarget, windowIndex: Int?) async throws -> [TmuxPane] {
        [
            TmuxPane(
                sessionName: target.sessionName,
                windowIndex: windowIndex ?? 0,
                index: 0,
                active: true,
                title: "shell",
                currentCommand: "zsh",
                currentPath: "/tmp",
                width: 80,
                height: 24,
                processID: 1
            )
        ]
    }

    func capturePane(for _: TmuxAttachTarget, lineLimit _: Int) async throws -> String {
        "$ echo demo"
    }

    func probeResources(hostID _: String) async throws -> HostResources {
        HostResources()
    }

    func createSession(_ request: TmuxCreateRequest) async throws -> TmuxAttachTarget {
        TmuxAttachTarget(
            hostID: request.hostID,
            socketPath: request.socketPath,
            sessionName: request.sessionName
        )
    }

    func renameSession(_: TmuxAttachTarget, to _: String) async throws {}
    func terminateSession(_: TmuxAttachTarget) async throws {}

    func attachCommand(for target: TmuxAttachTarget) -> String {
        TmuxCommandBuilder().attach(target: target)
    }
}
