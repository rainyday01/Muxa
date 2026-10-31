import Foundation

struct TerminalGeometry: Codable, Equatable, Sendable {
    let columns: Int
    let rows: Int
    let pixelWidth: Int
    let pixelHeight: Int
}

struct InteractiveTransportRequest: Equatable, Sendable {
    let host: Host
    let command: String
    let terminal: String
    let geometry: TerminalGeometry
    let preference: TransportPreference
    let initialSnapshot: Data?
    let initialCheckpoint: InteractiveTransportCheckpoint?

    init(
        host: Host,
        command: String,
        terminal: String,
        geometry: TerminalGeometry,
        preference: TransportPreference,
        initialSnapshot: Data? = nil,
        initialCheckpoint: InteractiveTransportCheckpoint? = nil
    ) {
        self.host = host
        self.command = command
        self.terminal = terminal
        self.geometry = geometry
        self.preference = preference
        self.initialSnapshot = initialSnapshot
        self.initialCheckpoint = initialCheckpoint
    }
}

enum InteractiveTransportState: Equatable, Sendable {
    case idle
    case connecting
    case connected(TransportPreference)
    case frozen
    case reconnecting
    case closed
    case failed(String)
}

enum InteractiveTransportEvent: Equatable, Sendable {
    case stateChanged(InteractiveTransportState)
    case received(Data)
    case synchronized
    case diagnostic(String)
}

struct InteractiveTransportCheckpoint: Codable, Equatable, Sendable {
    let transport: TransportPreference
    let payload: Data
    let createdAt: Date

    init(transport: TransportPreference, payload: Data, createdAt: Date) {
        self.transport = transport
        self.payload = payload
        self.createdAt = createdAt
    }

    private enum CodingKeys: String, CodingKey {
        case transport
        case payload
        case createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let transport = try container.decode(String.self, forKey: .transport)
        guard transport == TransportPreference.ssh.rawValue else {
            throw DecodingError.dataCorruptedError(
                forKey: .transport,
                in: container,
                debugDescription: "Unsupported interactive transport checkpoint."
            )
        }
        self.transport = .ssh
        payload = try container.decode(Data.self, forKey: .payload)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
    }
}

protocol InteractiveTransport: Sendable {
    func events() async -> AsyncStream<InteractiveTransportEvent>
    func state() async -> InteractiveTransportState
    func start(_ request: InteractiveTransportRequest) async throws
    func send(_ data: Data) async throws
    func resize(to geometry: TerminalGeometry) async throws
    func freeze() async throws -> InteractiveTransportCheckpoint?
    func resume(from checkpoint: InteractiveTransportCheckpoint?) async throws
    func close() async
}

protocol InteractiveTransportFactory: Sendable {
    func makeTransport(for preference: TransportPreference) async -> any InteractiveTransport
}

enum InteractiveTransportUnavailableError: Error, Equatable, Sendable {
    case unavailable
}

struct UnavailableInteractiveTransportFactory: InteractiveTransportFactory {
    func makeTransport(for _: TransportPreference) async -> any InteractiveTransport {
        UnavailableInteractiveTransport()
    }
}

private actor UnavailableInteractiveTransport: InteractiveTransport {
    func events() async -> AsyncStream<InteractiveTransportEvent> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }

    func state() async -> InteractiveTransportState {
        .failed("Interactive transport is unavailable.")
    }

    func start(_: InteractiveTransportRequest) async throws {
        throw InteractiveTransportUnavailableError.unavailable
    }

    func send(_: Data) async throws {
        throw InteractiveTransportUnavailableError.unavailable
    }

    func resize(to _: TerminalGeometry) async throws {
        throw InteractiveTransportUnavailableError.unavailable
    }

    func freeze() async throws -> InteractiveTransportCheckpoint? {
        throw InteractiveTransportUnavailableError.unavailable
    }

    func resume(from _: InteractiveTransportCheckpoint?) async throws {
        throw InteractiveTransportUnavailableError.unavailable
    }

    func close() async {}
}
