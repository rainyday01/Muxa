import Foundation

enum ConnectionManagerError: Error, Equatable, Sendable {
    case retryBackoff(until: Date)
    case noConnection(hostID: String)
}

struct ConnectionBackoffPolicy: Equatable, Sendable {
    let baseDelay: TimeInterval
    let maximumDelay: TimeInterval

    init(baseDelay: TimeInterval = 1, maximumDelay: TimeInterval = 30) {
        self.baseDelay = max(0, baseDelay)
        self.maximumDelay = max(self.baseDelay, maximumDelay)
    }

    func delay(afterFailureCount count: Int) -> TimeInterval {
        guard count > 0 else { return 0 }
        let multiplier = pow(2, Double(min(count - 1, 30)))
        return min(maximumDelay, baseDelay * multiplier)
    }
}

struct ConnectionManagerSnapshot: Equatable, Sendable {
    let hostID: String
    let handle: SSHConnectionHandle?
    let failureCount: Int
    let retryAt: Date?
}

actor ConnectionManager {
    private struct Entry: Sendable {
        var host: Host
        var handle: SSHConnectionHandle?
        var failureCount = 0
        var retryAt: Date?
    }

    private let controlPlane: any SSHControlPlane
    private let policy: ConnectionBackoffPolicy
    private let now: @Sendable () -> Date
    private var entries = [String: Entry]()

    init(
        controlPlane: any SSHControlPlane,
        policy: ConnectionBackoffPolicy = ConnectionBackoffPolicy(),
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.controlPlane = controlPlane
        self.policy = policy
        self.now = now
    }

    func connect(to host: Host) async throws -> SSHConnectionHandle {
        var entry = entries[host.id] ?? Entry(host: host)
        // A corrected username or host must not keep the socket, or the backoff,
        // from the previous login. The Hosts screen tests a fresh connection, so
        // it can succeed while Sessions refresh stays stuck on the old entry.
        if Self.loginChanged(from: entry.host, to: host) {
            if let handle = entry.handle {
                await controlPlane.disconnect(handle)
                entry.handle = nil
            }
            entry.failureCount = 0
            entry.retryAt = nil
        }
        entry.host = host
        let currentDate = now()

        if let handle = entry.handle, await controlPlane.isHealthy(handle) {
            entries[host.id] = entry
            return handle
        }
        if let handle = entry.handle {
            await controlPlane.disconnect(handle)
            entry.handle = nil
        }
        if let retryAt = entry.retryAt, retryAt > currentDate {
            entries[host.id] = entry
            throw ConnectionManagerError.retryBackoff(until: retryAt)
        }

        do {
            let handle = try await controlPlane.connect(to: host)
            entry.handle = handle
            entry.failureCount = 0
            entry.retryAt = nil
            entries[host.id] = entry
            return handle
        } catch {
            if Self.retriesImmediately(error) {
                // Host-key prompts and bad credentials wait for the user to edit
                // the host. A network failure is the case that backs off.
                entry.failureCount = 0
                entry.retryAt = nil
            } else {
                entry.failureCount += 1
                entry.retryAt = currentDate.addingTimeInterval(policy.delay(afterFailureCount: entry.failureCount))
            }
            entries[host.id] = entry
            throw error
        }
    }

    func execute(_ request: SSHCommandRequest, on host: Host) async throws -> SSHCommandOutput {
        let handle = try await connect(to: host)
        do {
            return try await controlPlane.execute(request, using: handle)
        } catch {
            await controlPlane.disconnect(handle)
            entries[host.id]?.handle = nil
            throw error
        }
    }

    func healthCheck(hostID: String) async -> Bool {
        guard let handle = entries[hostID]?.handle else { return false }
        let healthy = await controlPlane.isHealthy(handle)
        if !healthy {
            await controlPlane.disconnect(handle)
            entries[hostID]?.handle = nil
        }
        return healthy
    }

    func disconnect(hostID: String) async {
        guard let entry = entries[hostID] else { return }
        if let handle = entry.handle {
            await controlPlane.disconnect(handle)
        }
        entries.removeValue(forKey: hostID)
    }

    func suspend() async {
        for entry in entries.values {
            if let handle = entry.handle {
                await controlPlane.disconnect(handle)
            }
        }
        for hostID in entries.keys {
            entries[hostID]?.handle = nil
        }
    }

    func resume(_ hosts: [Host]) async -> [String: Result<SSHConnectionHandle, Error>] {
        var results = [String: Result<SSHConnectionHandle, Error>]()
        for host in hosts {
            do {
                results[host.id] = try await .success(connect(to: host))
            } catch {
                results[host.id] = .failure(error)
            }
        }
        return results
    }

    func snapshot(hostID: String) -> ConnectionManagerSnapshot? {
        guard let entry = entries[hostID] else { return nil }
        return ConnectionManagerSnapshot(
            hostID: hostID,
            handle: entry.handle,
            failureCount: entry.failureCount,
            retryAt: entry.retryAt
        )
    }

    private static func loginChanged(from old: Host, to new: Host) -> Bool {
        old.hostname != new.hostname
            || old.port != new.port
            || old.username != new.username
            || old.hasPassword != new.hasPassword
            || old.hasPrivateKey != new.hasPrivateKey
    }

    private static func retriesImmediately(_ error: Error) -> Bool {
        guard let error = error as? SSHControlPlaneError else { return false }
        switch error {
        case .hostKeyConfirmationRequired, .hostKeyMismatch, .authenticationFailed, .missingCredential:
            return true
        default:
            return false
        }
    }
}
