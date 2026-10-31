@testable import Muxa
import XCTest

final class ConnectionLifecycleTests: XCTestCase {
    func testConnectFreezeAndResumeLifecycle() async throws {
        let lifecycle = ConnectionLifecycle()
        let checkpoint = InteractiveTransportCheckpoint(
            transport: .ssh,
            payload: Data([1, 2, 3]),
            createdAt: Date(timeIntervalSince1970: 100)
        )

        let connectEffects = try await lifecycle.handle(.connectRequested)
        _ = try await lifecycle.handle(.connected(.ssh))
        let freezeEffects = try await lifecycle.handle(.sceneChanged(.background))
        let frozenEffects = try await lifecycle.handle(.freezeCompleted(checkpoint))
        let resumeEffects = try await lifecycle.handle(.sceneChanged(.active))
        _ = try await lifecycle.handle(.resumeSucceeded(.ssh))

        XCTAssertEqual(connectEffects, [.connect])
        XCTAssertEqual(freezeEffects, [.freeze])
        XCTAssertEqual(frozenEffects, [])
        XCTAssertEqual(resumeEffects, [.resume(checkpoint)])
        let snapshot = await lifecycle.snapshot()
        XCTAssertEqual(snapshot.state, .connected(.ssh))
        XCTAssertEqual(snapshot.sceneState, .active)
    }

    func testResumeFailureRequestsFirstReconnectAttempt() async throws {
        let lifecycle = ConnectionLifecycle()
        _ = try await lifecycle.handle(.connectRequested)
        _ = try await lifecycle.handle(.connected(.ssh))
        _ = try await lifecycle.handle(.sceneChanged(.background))
        _ = try await lifecycle.handle(.freezeCompleted(nil))
        _ = try await lifecycle.handle(.sceneChanged(.active))

        let effects = try await lifecycle.handle(.resumeFailed("network changed"))

        XCTAssertEqual(effects, [.reconnect(attempt: 1, reason: "network changed")])
        let state = await lifecycle.snapshot().state
        XCTAssertEqual(state, .reconnecting(attempt: 1))
    }

    func testInvalidTransitionIsRejectedWithoutMutatingState() async {
        let lifecycle = ConnectionLifecycle()

        do {
            _ = try await lifecycle.handle(.resumeSucceeded(.ssh))
            XCTFail("Expected invalid transition")
        } catch {
            XCTAssertEqual(
                error as? ConnectionLifecycleError,
                .invalidTransition(state: .idle, event: .resumeSucceeded(.ssh))
            )
        }
        let state = await lifecycle.snapshot().state
        XCTAssertEqual(state, .idle)
    }
}
