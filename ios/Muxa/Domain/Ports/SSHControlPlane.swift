import Foundation

struct SSHConnectionHandle: Hashable, Sendable {
    let id: UUID
    let hostID: String
}

struct SSHCommandRequest: Equatable, Sendable {
    let command: String
    let timeout: Duration

    init(command: String, timeout: Duration = .seconds(15)) {
        self.command = command
        self.timeout = timeout
    }
}

struct SSHCommandOutput: Equatable, Sendable {
    let stdout: Data
    let stderr: Data
    let exitStatus: Int32
}

enum SSHControlPlaneError: Error, Equatable, Sendable {
    case hostNotFound(String)
    case missingCredential(String)
    case connectionUnavailable
    case timedOut
    case rejected(String)
    case hostKeyConfirmationRequired(HostKeyIdentity)
    case hostKeyMismatch(expected: HostKeyIdentity, actual: HostKeyIdentity)
    case authenticationFailed
    case dnsResolutionFailed
    case connectionRefused
    case remoteFailure(status: Int32, message: String)
}

protocol SSHControlPlane: Sendable {
    func connect(to host: Host) async throws -> SSHConnectionHandle
    func execute(_ request: SSHCommandRequest, using connection: SSHConnectionHandle) async throws -> SSHCommandOutput
    func isHealthy(_ connection: SSHConnectionHandle) async -> Bool
    func disconnect(_ connection: SSHConnectionHandle) async
}
