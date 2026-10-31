@testable import Muxa
import XCTest

private actor MemorySecretStore: SecretStoring {
    private var values = [SecretReference: Data]()

    func store(_ value: Data, for reference: SecretReference) {
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

final class DomainPortsTests: XCTestCase {
    func testSecretReferencesIsolateCredentialKindsAndHosts() async throws {
        let store = MemorySecretStore()
        let password = SecretReference(hostID: "host-a", kind: .password)
        let key = SecretReference(hostID: "host-a", kind: .privateKey)
        let other = SecretReference(hostID: "host-b", kind: .password)
        try await store.store(Data("password".utf8), for: password)
        try await store.store(Data("key".utf8), for: key)
        try await store.store(Data("other".utf8), for: other)

        try await store.deleteAll(hostID: "host-a")

        let removedPassword = try await store.retrieve(password)
        let removedKey = try await store.retrieve(key)
        let retainedOther = try await store.retrieve(other)
        XCTAssertNil(removedPassword)
        XCTAssertNil(removedKey)
        XCTAssertEqual(retainedOther, Data("other".utf8))
    }

    func testTmuxAttachTargetAndTerminalGeometryAreValueContracts() throws {
        let target = TmuxAttachTarget(hostID: "host", socketPath: "/tmp/tmux/default", sessionName: "work")
        let geometry = TerminalGeometry(columns: 120, rows: 40, pixelWidth: 1200, pixelHeight: 800)

        XCTAssertEqual(target.sessionName, "work")
        XCTAssertEqual(try JSONDecoder().decode(TerminalGeometry.self, from: JSONEncoder().encode(geometry)), geometry)
    }
}

@MainActor
final class RemoteTerminalSessionPersistenceTests: XCTestCase {
    func testBackgroundCheckpointIsLoadedByANewSessionAfterProcessRestart() async throws {
        let secretStore = CheckpointSecretStore()
        let checkpoint = makeCheckpoint()
        let factory = FixturePersistentTransportFactory(checkpoint: checkpoint)
        let host = Host(
            id: "persisted-host",
            alias: "Persisted",
            hostname: "example.invalid",
            username: "root",
            transport: .ssh
        )
        let target = TmuxAttachTarget(
            hostID: host.id,
            socketPath: nil,
            sessionName: "work"
        )

        let firstSession = RemoteTerminalSession(
            host: host,
            target: target,
            preference: .ssh,
            initialSnapshot: nil,
            transportFactory: factory,
            secretStore: secretStore
        )
        await firstSession.start()
        await firstSession.scenePhaseChanged(.background)

        let reference = SecretReference(
            hostID: host.id,
            kind: .transportCheckpoint,
            identifier: target.sessionName
        )
        let persisted = try await secretStore.retrieve(reference)
        XCTAssertNotNil(persisted)

        let secondSession = RemoteTerminalSession(
            host: host,
            target: target,
            preference: .ssh,
            initialSnapshot: nil,
            transportFactory: factory,
            secretStore: secretStore
        )
        await secondSession.start()

        let transports = await factory.transports
        let secondTransport = try XCTUnwrap(transports.dropFirst().first)
        let request = await secondTransport.startRequest
        XCTAssertEqual(request?.initialCheckpoint, checkpoint)
        XCTAssertEqual(secondSession.lastDiagnostic, "Restored terminal session")

        await firstSession.close()
        await secondSession.close()
        let remaining = try await secretStore.retrieve(reference)
        XCTAssertNil(remaining)
    }

    private func makeCheckpoint() -> InteractiveTransportCheckpoint {
        InteractiveTransportCheckpoint(
            transport: .ssh,
            payload: Data("encrypted-ssh-state".utf8),
            createdAt: Date()
        )
    }
}

private actor CheckpointSecretStore: SecretStoring {
    private var values = [SecretReference: Data]()

    func store(_ value: Data, for reference: SecretReference) {
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

private actor FixturePersistentTransportFactory: InteractiveTransportFactory {
    private let checkpoint: InteractiveTransportCheckpoint
    private(set) var transports = [FixturePersistentTransport]()

    init(checkpoint: InteractiveTransportCheckpoint) {
        self.checkpoint = checkpoint
    }

    func makeTransport(for _: TransportPreference) -> any InteractiveTransport {
        let transport = FixturePersistentTransport(checkpoint: checkpoint)
        transports.append(transport)
        return transport
    }
}

private actor FixturePersistentTransport: InteractiveTransport {
    private let checkpoint: InteractiveTransportCheckpoint
    private let eventStream: AsyncStream<InteractiveTransportEvent>
    private var eventContinuation: AsyncStream<InteractiveTransportEvent>.Continuation?
    private(set) var startRequest: InteractiveTransportRequest?
    private var currentState: InteractiveTransportState = .idle

    init(checkpoint: InteractiveTransportCheckpoint) {
        self.checkpoint = checkpoint
        let (stream, continuation) = AsyncStream<InteractiveTransportEvent>.makeStream()
        eventStream = stream
        eventContinuation = continuation
    }

    func events() -> AsyncStream<InteractiveTransportEvent> {
        eventStream
    }

    func state() -> InteractiveTransportState {
        currentState
    }

    func start(_ request: InteractiveTransportRequest) {
        startRequest = request
        currentState = .connected(.ssh)
        eventContinuation?.yield(.stateChanged(currentState))
    }

    func send(_: Data) {}
    func resize(to _: TerminalGeometry) {}

    func freeze() -> InteractiveTransportCheckpoint? {
        currentState = .frozen
        eventContinuation?.yield(.stateChanged(currentState))
        return checkpoint
    }

    func resume(from _: InteractiveTransportCheckpoint?) {
        currentState = .connected(.ssh)
        eventContinuation?.yield(.stateChanged(currentState))
    }

    func close() {
        currentState = .closed
        eventContinuation?.finish()
    }
}
