import Foundation

actor SSHInteractiveTransport: InteractiveTransport {
    private let controller: any SSHInteractiveControlling
    private let eventStream: AsyncStream<InteractiveTransportEvent>
    private var eventContinuation: AsyncStream<InteractiveTransportEvent>.Continuation?
    private var receiveTask: Task<Void, Never>?
    private var ptyHandle: SSHInteractiveSessionHandle?
    private var request: InteractiveTransportRequest?
    private var transportState: InteractiveTransportState = .idle
    private var didEmitSynchronized = false

    init(controller: any SSHInteractiveControlling) {
        self.controller = controller
        let (eventStream, eventContinuation) = AsyncStream<InteractiveTransportEvent>.makeStream()
        self.eventStream = eventStream
        self.eventContinuation = eventContinuation
    }

    func events() async -> AsyncStream<InteractiveTransportEvent> {
        eventStream
    }

    func state() async -> InteractiveTransportState {
        transportState
    }

    func start(_ request: InteractiveTransportRequest) async throws {
        guard transportState == .idle else {
            throw SSHInteractiveTransportError.invalidState(transportState)
        }
        self.request = request
        didEmitSynchronized = false
        setState(.connecting)
        if let initialSnapshot = request.initialSnapshot, !initialSnapshot.isEmpty {
            eventContinuation?.yield(.received(initialSnapshot))
        }

        do {
            try await openPTY(for: request)
        } catch {
            setState(.failed(Self.message(for: error)))
            throw error
        }
    }

    func send(_ data: Data) async throws {
        guard let ptyHandle, isActive else {
            throw SSHInteractiveTransportError.invalidState(transportState)
        }
        do {
            try await controller.writePTY(data, using: ptyHandle)
        } catch {
            await fail(error, handle: ptyHandle)
            throw error
        }
    }

    func resize(to geometry: TerminalGeometry) async throws {
        guard let ptyHandle, isActive else {
            throw SSHInteractiveTransportError.invalidState(transportState)
        }
        guard geometry.columns > 0, geometry.rows > 0, geometry.pixelWidth >= 0, geometry.pixelHeight >= 0 else {
            throw SSHInteractiveTransportError.invalidGeometry
        }
        do {
            try await controller.resizePTY(to: geometry, using: ptyHandle)
        } catch {
            await fail(error, handle: ptyHandle)
            throw error
        }
    }

    func freeze() async throws -> InteractiveTransportCheckpoint? {
        guard isActive, let ptyHandle else {
            throw SSHInteractiveTransportError.invalidState(transportState)
        }
        stopReceiving()
        self.ptyHandle = nil
        await controller.closePTY(using: ptyHandle)
        setState(.frozen)
        return nil
    }

    func resume(from checkpoint: InteractiveTransportCheckpoint?) async throws {
        guard transportState == .frozen, let request else {
            throw SSHInteractiveTransportError.invalidState(transportState)
        }
        if let checkpoint, checkpoint.transport != .ssh {
            throw SSHInteractiveTransportError.invalidCheckpoint
        }
        setState(.reconnecting)
        do {
            try await openPTY(for: request)
        } catch {
            setState(.failed(Self.message(for: error)))
            throw error
        }
    }

    func close() async {
        stopReceiving()
        let handle = ptyHandle
        ptyHandle = nil
        if let handle {
            await controller.closePTY(using: handle)
        }
        guard transportState != .closed else { return }
        setState(.closed)
        eventContinuation?.finish()
        eventContinuation = nil
    }

    private var isActive: Bool {
        if case .connected = transportState {
            return true
        }
        return false
    }

    private func openPTY(for request: InteractiveTransportRequest) async throws {
        let handle = try await controller.openPTY(
            SSHInteractiveSessionRequest(
                host: request.host,
                command: request.command,
                terminal: request.terminal,
                geometry: request.geometry
            )
        )
        ptyHandle = handle
        didEmitSynchronized = false
        setState(.connected(.ssh))
        receiveTask = Task { [weak self] in
            await self?.receiveLoop(handle: handle)
        }
    }

    private func receiveLoop(handle: SSHInteractiveSessionHandle) async {
        while !Task.isCancelled {
            do {
                guard let data = try await controller.readPTY(maxBytes: 32 * 1024, using: handle) else {
                    await finishAfterEOF(handle: handle)
                    return
                }
                if data.isEmpty {
                    try await Task.sleep(for: .milliseconds(20))
                } else {
                    eventContinuation?.yield(.received(data))
                    if !didEmitSynchronized {
                        didEmitSynchronized = true
                        eventContinuation?.yield(.synchronized)
                    }
                }
            } catch is CancellationError {
                return
            } catch {
                await fail(error, handle: handle)
                return
            }
        }
    }

    private func finishAfterEOF(handle: SSHInteractiveSessionHandle) async {
        guard ptyHandle == handle else { return }
        ptyHandle = nil
        setState(.closed)
        await controller.closePTY(using: handle)
        eventContinuation?.finish()
        eventContinuation = nil
    }

    private func fail(_ error: Error, handle: SSHInteractiveSessionHandle) async {
        guard ptyHandle == handle else { return }
        ptyHandle = nil
        stopReceiving()
        setState(.failed(Self.message(for: error)))
        await controller.closePTY(using: handle)
    }

    private func stopReceiving() {
        receiveTask?.cancel()
        receiveTask = nil
    }

    private func setState(_ state: InteractiveTransportState) {
        transportState = state
        eventContinuation?.yield(.stateChanged(state))
    }

    private static func message(for error: Error) -> String {
        if let error = error as? SSHControlPlaneError {
            return String(describing: error)
        }
        if let error = error as? LibSSH2DriverError {
            return String(describing: error)
        }
        return "Interactive SSH session failed."
    }
}

enum SSHInteractiveTransportError: Error, Equatable, Sendable {
    case invalidState(InteractiveTransportState)
    case invalidGeometry
    case invalidCheckpoint
}

struct LiveInteractiveTransportFactory: InteractiveTransportFactory {
    let sshController: any SSHInteractiveControlling

    func makeTransport(for _: TransportPreference) async -> any InteractiveTransport {
        SSHInteractiveTransport(controller: sshController)
    }
}
