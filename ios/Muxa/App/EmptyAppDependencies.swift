import Foundation

actor EmptyKeyboardLayoutRepository: KeyboardLayoutRepository {
    func fetchAll() -> [KeyboardLayout] {
        []
    }

    func save(_: KeyboardLayout) {}
    func delete(id _: String) {}
}

actor EmptyHostRepository: HostRepository {
    func observeAll() -> AsyncStream<[Host]> {
        AsyncStream { continuation in
            continuation.yield([])
            continuation.finish()
        }
    }

    func fetchAll() -> [Host] {
        []
    }

    func fetch(id _: String) -> Host? {
        nil
    }

    func save(_: Host) {}
    func updateResources(hostID _: String, resources _: HostResources) {}
    func delete(id _: String) {}
    func deleteAll() {}
}

actor EmptySnippetRepository: SnippetRepository {
    func observeAll() -> AsyncStream<[Snippet]> {
        AsyncStream { continuation in
            continuation.yield([])
            continuation.finish()
        }
    }

    func fetchAll() -> [Snippet] {
        []
    }

    func save(_: Snippet) {}
    func delete(id _: String) {}
}

actor EmptyCommandHistoryRepository: CommandHistoryRepository {
    func record(hostID _: String, command _: String, usedAt _: Date) {}
    func fetchRecent(hostID _: String, limit _: Int) -> [CommandHistoryEntry] {
        []
    }

    func prune(hostID _: String, keeping _: Int) {}
}

actor EmptySecretStore: SecretStoring {
    func store(_: Data, for _: SecretReference) {}
    func retrieve(_: SecretReference) -> Data? {
        nil
    }

    func delete(_: SecretReference) {}
    func deleteAll(hostID _: String) {}
    func deleteAllSecrets() {}
}

actor EmptyKnownHostStore: KnownHostStoring {
    func identities(hostname _: String, port _: UInt16) -> [HostKeyIdentity] {
        []
    }

    func verify(_ identity: HostKeyIdentity, seenAt _: Date) -> HostKeyVerification {
        .unknown(identity)
    }

    func acceptFirst(_: HostKeyIdentity, seenAt _: Date) {}
    func replaceAfterConfirmation(_: HostKeyIdentity, seenAt _: Date) {}
    func remove(hostname _: String, port _: UInt16) {}
}

actor EmptySSHControlPlane: SSHControlPlane {
    func connect(to _: Host) throws -> SSHConnectionHandle {
        throw SSHControlPlaneError.connectionUnavailable
    }

    func execute(_: SSHCommandRequest, using _: SSHConnectionHandle) throws -> SSHCommandOutput {
        throw SSHControlPlaneError.connectionUnavailable
    }

    func isHealthy(_: SSHConnectionHandle) -> Bool {
        false
    }

    func disconnect(_: SSHConnectionHandle) {}
}

actor EmptyRemoteEnvironmentProbe: RemoteEnvironmentProbing {
    func probe(host _: Host) throws -> RemoteEnvironmentSnapshot {
        throw SSHControlPlaneError.connectionUnavailable
    }
}

actor EmptyDiagnosticsLogger: DiagnosticLogging {
    func log(level _: DiagnosticLevel, category _: String, message _: String, metadata _: [String: String]) {}
    func entries() -> [DiagnosticEntry] {
        []
    }

    func export() throws -> Data {
        Data("[]".utf8)
    }

    func clear() {}
}
