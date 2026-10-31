import Foundation

enum AppSceneState: Equatable, Sendable {
    case active
    case inactive
    case background
}

enum ConnectionLifecycleState: Equatable, Sendable {
    case idle
    case connecting
    case connected(TransportPreference)
    case freezing(TransportPreference)
    case frozen(InteractiveTransportCheckpoint?)
    case resuming
    case reconnecting(attempt: Int)
    case closing
    case closed
    case failed(String)
}

enum ConnectionLifecycleEvent: Equatable, Sendable {
    case connectRequested
    case connected(TransportPreference)
    case connectionFailed(String)
    case sceneChanged(AppSceneState)
    case freezeCompleted(InteractiveTransportCheckpoint?)
    case freezeFailed(String)
    case resumeSucceeded(TransportPreference)
    case resumeFailed(String)
    case reconnectStarted(attempt: Int)
    case closeRequested
    case closeCompleted
}

enum ConnectionLifecycleEffect: Equatable, Sendable {
    case connect
    case freeze
    case resume(InteractiveTransportCheckpoint?)
    case reconnect(attempt: Int, reason: String)
    case close
}

enum ConnectionLifecycleError: Error, Equatable, Sendable {
    case invalidTransition(state: ConnectionLifecycleState, event: ConnectionLifecycleEvent)
}

struct ConnectionLifecycleSnapshot: Equatable, Sendable {
    let state: ConnectionLifecycleState
    let sceneState: AppSceneState
}

actor ConnectionLifecycle {
    private var lifecycleState: ConnectionLifecycleState = .idle
    private var sceneState: AppSceneState = .active

    func snapshot() -> ConnectionLifecycleSnapshot {
        ConnectionLifecycleSnapshot(state: lifecycleState, sceneState: sceneState)
    }

    func handle(_ event: ConnectionLifecycleEvent) throws -> [ConnectionLifecycleEffect] {
        if case let .sceneChanged(scene) = event {
            return try handleSceneChange(scene)
        }
        if case .closeRequested = event {
            lifecycleState = .closing
            return [.close]
        }
        if case .closeCompleted = event {
            guard case .closing = lifecycleState else {
                throw ConnectionLifecycleError.invalidTransition(state: lifecycleState, event: event)
            }
            lifecycleState = .closed
            return []
        }
        return try handleConnectionEvent(event)
    }

    private func handleConnectionEvent(_ event: ConnectionLifecycleEvent) throws -> [ConnectionLifecycleEffect] {
        switch (lifecycleState, event) {
        case (.idle, .connectRequested), (.closed, .connectRequested), (.failed, .connectRequested):
            lifecycleState = .connecting
            return [.connect]
        case let (.connecting, .connected(transport)), let (.reconnecting, .connected(transport)):
            lifecycleState = .connected(transport)
            return []
        case let (.connecting, .connectionFailed(reason)):
            lifecycleState = .failed(reason)
            return []
        case let (.freezing, .freezeCompleted(checkpoint)):
            lifecycleState = .frozen(checkpoint)
            return sceneState == .active ? beginResume(checkpoint: checkpoint) : []
        case let (.freezing, .freezeFailed(reason)):
            return beginReconnect(reason: reason)
        case let (.resuming, .resumeSucceeded(transport)):
            lifecycleState = .connected(transport)
            return []
        case let (.resuming, .resumeFailed(reason)):
            return beginReconnect(reason: reason)
        case let (.reconnecting, .reconnectStarted(attempt)):
            lifecycleState = .reconnecting(attempt: max(1, attempt))
            return []
        default:
            throw ConnectionLifecycleError.invalidTransition(state: lifecycleState, event: event)
        }
    }

    private func handleSceneChange(_ scene: AppSceneState) throws -> [ConnectionLifecycleEffect] {
        sceneState = scene
        switch (lifecycleState, scene) {
        case let (.connected(transport), .background):
            lifecycleState = .freezing(transport)
            return [.freeze]
        case let (.frozen(checkpoint), .active):
            return beginResume(checkpoint: checkpoint)
        case (_, .inactive), (_, .active), (_, .background):
            return []
        }
    }

    private func beginResume(checkpoint: InteractiveTransportCheckpoint?) -> [ConnectionLifecycleEffect] {
        lifecycleState = .resuming
        return [.resume(checkpoint)]
    }

    private func beginReconnect(reason: String) -> [ConnectionLifecycleEffect] {
        lifecycleState = .reconnecting(attempt: 1)
        return [.reconnect(attempt: 1, reason: reason)]
    }
}
