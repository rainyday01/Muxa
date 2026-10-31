import Foundation

actor LiveSSHControlPlane: SSHControlPlane, SSHInteractiveControlling {
    private struct Connection: Sendable {
        let adapter: LibSSH2SessionAdapter
        let driver: LibSSH2NativeDriver
        let host: Host
    }

    private let secretStore: any SecretStoring
    private let knownHosts: any KnownHostStoring
    private var connections = [UUID: Connection]()
    private var interactiveConnections = [UUID: Connection]()

    init(secretStore: any SecretStoring, knownHosts: any KnownHostStoring) {
        self.secretStore = secretStore
        self.knownHosts = knownHosts
    }

    func connect(to host: Host) async throws -> SSHConnectionHandle {
        let connection = try await makeConnection(for: host)
        let id = UUID()
        connections[id] = connection
        return SSHConnectionHandle(id: id, hostID: host.id)
    }

    func execute(_ request: SSHCommandRequest, using connection: SSHConnectionHandle) async throws -> SSHCommandOutput {
        guard let stored = connections[connection.id] else {
            throw SSHControlPlaneError.connectionUnavailable
        }
        do {
            let result = try await withThrowingTaskGroup(of: SSHExecResult.self) { group in
                group.addTask {
                    try await stored.adapter.execute(request.command)
                }
                group.addTask {
                    try await Task.sleep(for: request.timeout)
                    throw SSHControlPlaneError.timedOut
                }
                guard let first = try await group.next() else {
                    throw SSHControlPlaneError.connectionUnavailable
                }
                group.cancelAll()
                return first
            }
            return SSHCommandOutput(
                stdout: result.stdout,
                stderr: result.stderr,
                exitStatus: result.exitStatus
            )
        } catch let error as SSHControlPlaneError {
            throw error
        } catch {
            throw Self.map(error)
        }
    }

    func isHealthy(_ connection: SSHConnectionHandle) async -> Bool {
        guard let stored = connections[connection.id] else { return false }
        let state = await stored.adapter.state
        return state == .ready || state == .ptyOpen
    }

    func disconnect(_ connection: SSHConnectionHandle) async {
        guard let stored = connections.removeValue(forKey: connection.id) else { return }
        await stored.adapter.close()
    }

    func openPTY(_ request: SSHInteractiveSessionRequest) async throws -> SSHInteractiveSessionHandle {
        let pty = try Self.makePTYRequest(
            terminal: request.terminal,
            geometry: request.geometry,
            command: request.command
        )
        let connection = try await makeConnection(for: request.host)
        do {
            try await connection.adapter.openPTY(pty)
        } catch {
            await connection.adapter.close()
            throw Self.map(error)
        }

        let id = UUID()
        interactiveConnections[id] = connection
        return SSHInteractiveSessionHandle(id: id, hostID: request.host.id)
    }

    func readPTY(maxBytes: Int, using handle: SSHInteractiveSessionHandle) async throws -> Data? {
        guard let connection = interactiveConnections[handle.id] else {
            throw SSHInteractiveControlError.sessionUnavailable
        }
        do {
            return try await connection.adapter.readPTY(maxBytes: maxBytes)
        } catch {
            throw Self.map(error)
        }
    }

    func writePTY(_ data: Data, using handle: SSHInteractiveSessionHandle) async throws {
        guard let connection = interactiveConnections[handle.id] else {
            throw SSHInteractiveControlError.sessionUnavailable
        }
        do {
            try await connection.adapter.writePTY(data)
        } catch {
            throw Self.map(error)
        }
    }

    func resizePTY(to geometry: TerminalGeometry, using handle: SSHInteractiveSessionHandle) async throws {
        guard let connection = interactiveConnections[handle.id] else {
            throw SSHInteractiveControlError.sessionUnavailable
        }
        do {
            let columns = try Self.int32Value(geometry.columns, allowingZero: false)
            let rows = try Self.int32Value(geometry.rows, allowingZero: false)
            let pixelWidth = try Self.int32Value(geometry.pixelWidth, allowingZero: true)
            let pixelHeight = try Self.int32Value(geometry.pixelHeight, allowingZero: true)
            try await connection.adapter.resizePTY(
                columns: columns,
                rows: rows,
                pixelWidth: pixelWidth,
                pixelHeight: pixelHeight
            )
        } catch {
            throw Self.map(error)
        }
    }

    func closePTY(using handle: SSHInteractiveSessionHandle) async {
        guard let connection = interactiveConnections.removeValue(forKey: handle.id) else { return }
        await connection.adapter.close()
    }

    private func authentication(for host: Host) async throws -> SSHAuthentication {
        if host.hasPrivateKey {
            guard let privateKeyData = try await secretStore.retrieve(
                SecretReference(hostID: host.id, kind: .privateKey)
            ), let privateKey = String(data: privateKeyData, encoding: .utf8) else {
                throw SSHControlPlaneError.missingCredential("private key")
            }
            let passphrase = try await secretStore.retrieve(
                SecretReference(hostID: host.id, kind: .privateKeyPassphrase)
            ).flatMap { String(data: $0, encoding: .utf8) }
            return .publicKey(publicKey: nil, privateKey: privateKey, passphrase: passphrase)
        }
        guard let passwordData = try await secretStore.retrieve(
            SecretReference(hostID: host.id, kind: .password)
        ), let password = String(data: passwordData, encoding: .utf8) else {
            throw SSHControlPlaneError.missingCredential("password")
        }
        return .password(password)
    }

    private func makeConnection(for host: Host) async throws -> Connection {
        let authentication = try await authentication(for: host)
        let trustedIdentities = try await knownHosts.identities(hostname: host.hostname, port: host.port)
        let trustedFingerprints = Set(trustedIdentities.map(\.sha256Fingerprint))
        let driver = LibSSH2NativeDriver(authentication: authentication) { fingerprint, _ in
            trustedFingerprints.contains(fingerprint)
        }
        let adapter = LibSSH2SessionAdapter(driver: driver)
        do {
            try await adapter.connect(
                SSHConnectionRequest(hostname: host.hostname, port: host.port, username: host.username)
            )
        } catch {
            if case let LibSSH2DriverError.hostKeyRejected(observed) = error {
                let actual = HostKeyIdentity(
                    hostname: host.hostname,
                    port: host.port,
                    keyType: observed.keyType,
                    sha256Fingerprint: observed.fingerprint
                )
                if let expected = trustedIdentities.first(where: { $0.keyType == actual.keyType }) {
                    throw SSHControlPlaneError.hostKeyMismatch(expected: expected, actual: actual)
                }
                throw SSHControlPlaneError.hostKeyConfirmationRequired(actual)
            }
            throw Self.map(error)
        }
        return Connection(adapter: adapter, driver: driver, host: host)
    }

    private static func makePTYRequest(
        terminal: String,
        geometry: TerminalGeometry,
        command: String
    ) throws -> SSHPTYRequest {
        try SSHPTYRequest(
            terminal: terminal,
            columns: int32Value(geometry.columns, allowingZero: false),
            rows: int32Value(geometry.rows, allowingZero: false),
            pixelWidth: int32Value(geometry.pixelWidth, allowingZero: true),
            pixelHeight: int32Value(geometry.pixelHeight, allowingZero: true),
            command: command
        )
    }

    private static func int32Value(_ value: Int, allowingZero: Bool) throws -> Int32 {
        let isValid = allowingZero ? value >= 0 : value > 0
        guard isValid, let result = Int32(exactly: value) else {
            throw SSHInteractiveControlError.invalidTerminalSize
        }
        return result
    }

    private static func map(_ error: Error) -> SSHControlPlaneError {
        switch error {
        case LibSSH2DriverError.addressResolution:
            return .dnsResolutionFailed
        case let LibSSH2DriverError.connectionFailed(code):
            if code == ECONNREFUSED {
                return .connectionRefused
            }
            return .connectionUnavailable
        case LibSSH2DriverError.operationTimedOut:
            return .timedOut
        case LibSSH2DriverError.hostKeyUnavailable:
            return .rejected("The server did not present a host key.")
        case LibSSH2DriverError.operation(name: "password-authentication", _),
             LibSSH2DriverError.operation(name: "public-key-authentication", _):
            return .authenticationFailed
        case let LibSSH2DriverError.operation(name, code):
            return .rejected("SSH operation \(name) failed (\(code)).")
        case SSHAdapterError.invalidTerminalSize, SSHAdapterError.invalidPTYReadSize:
            return .remoteFailure(status: 0, message: "Invalid terminal geometry.")
        case SSHInteractiveControlError.sessionUnavailable:
            return .connectionUnavailable
        default:
            return .connectionUnavailable
        }
    }
}
