import Foundation
@testable import Muxa
import XCTest

private enum FixtureInteractiveError: Error, Equatable, Sendable {
    case openFailed
    case readFailed
}

private actor FixtureSSHInteractiveController: SSHInteractiveControlling {
    let handle: SSHInteractiveSessionHandle
    private(set) var openedRequests = [SSHInteractiveSessionRequest]()
    private(set) var writes = [Data]()
    private(set) var resizes = [TerminalGeometry]()
    private(set) var closeCount = 0
    private(set) var readCount = 0
    var reads: [Data?]
    var openError: FixtureInteractiveError?
    var readError: FixtureInteractiveError?

    init(reads: [Data?] = []) {
        handle = SSHInteractiveSessionHandle(id: UUID(), hostID: "fixture-host")
        self.reads = reads
    }

    func openPTY(_ request: SSHInteractiveSessionRequest) throws -> SSHInteractiveSessionHandle {
        if let openError {
            throw openError
        }
        openedRequests.append(request)
        return handle
    }

    func readPTY(maxBytes _: Int, using _: SSHInteractiveSessionHandle) throws -> Data? {
        if let readError {
            throw readError
        }
        readCount += 1
        return reads.isEmpty ? Data() : reads.removeFirst()
    }

    func writePTY(_ data: Data, using _: SSHInteractiveSessionHandle) {
        writes.append(data)
    }

    func resizePTY(to geometry: TerminalGeometry, using _: SSHInteractiveSessionHandle) {
        resizes.append(geometry)
    }

    func closePTY(using _: SSHInteractiveSessionHandle) {
        closeCount += 1
    }
}

final class SSHInteractiveTransportTests: XCTestCase {
    func testStartDeliversSnapshotLiveOutputAndSynchronizedEvent() async throws {
        let controller = FixtureSSHInteractiveController(reads: [Data("live output".utf8)])
        let transport = SSHInteractiveTransport(controller: controller)
        let stream = await transport.events()
        let eventsTask = Task { () -> [InteractiveTransportEvent] in
            var events = [InteractiveTransportEvent]()
            for await event in stream {
                events.append(event)
            }
            return events
        }
        let request = makeRequest(initialSnapshot: Data("cached snapshot".utf8))

        try await transport.start(request)
        try await waitUntil { await transport.state() == .connected(.ssh) }
        try await waitUntil { await controller.readCount > 0 }
        try await Task.sleep(for: .milliseconds(20))
        await transport.close()
        let events = await eventsTask.value

        XCTAssertTrue(events.contains(.stateChanged(.connecting)))
        XCTAssertTrue(events.contains(.stateChanged(.connected(.ssh))))
        XCTAssertTrue(events.contains(.received(Data("cached snapshot".utf8))))
        XCTAssertTrue(events.contains(.received(Data("live output".utf8))))
        XCTAssertTrue(events.contains(.synchronized))
        let openedRequests = await controller.openedRequests
        XCTAssertEqual(openedRequests, [
            SSHInteractiveSessionRequest(
                host: makeHost(),
                command: "tmux attach -t demo",
                terminal: "xterm-256color",
                geometry: makeGeometry()
            )
        ])
    }

    func testSendResizeAndCloseUseTheOpenedPTY() async throws {
        let controller = FixtureSSHInteractiveController()
        let transport = SSHInteractiveTransport(controller: controller)
        _ = await transport.events()
        try await transport.start(makeRequest())
        try await waitUntil { await transport.state() == .connected(.ssh) }

        let nextGeometry = TerminalGeometry(columns: 120, rows: 40, pixelWidth: 1200, pixelHeight: 800)
        try await transport.send(Data("pwd\r".utf8))
        try await transport.resize(to: nextGeometry)
        await transport.close()

        let writes = await controller.writes
        let resizes = await controller.resizes
        let closeCount = await controller.closeCount
        let state = await transport.state()
        XCTAssertEqual(writes, [Data("pwd\r".utf8)])
        XCTAssertEqual(resizes, [nextGeometry])
        XCTAssertEqual(closeCount, 1)
        XCTAssertEqual(state, .closed)
    }

    func testOpenFailurePublishesFailedStateAndPreservesError() async {
        let controller = FixtureSSHInteractiveController()
        await controller.setOpenError(.openFailed)
        let transport = SSHInteractiveTransport(controller: controller)
        let stream = await transport.events()
        let eventsTask = Task { () -> [InteractiveTransportEvent] in
            var events = [InteractiveTransportEvent]()
            for await event in stream {
                events.append(event)
                if case .stateChanged(.failed) = event {
                    break
                }
            }
            return events
        }

        do {
            try await transport.start(makeRequest())
            XCTFail("Expected PTY open to fail")
        } catch {
            XCTAssertEqual(error as? FixtureInteractiveError, .openFailed)
        }

        let state = await transport.state()
        XCTAssertEqual(state, .failed("Interactive SSH session failed."))
        let events = await eventsTask.value
        XCTAssertTrue(events.contains(.stateChanged(.failed("Interactive SSH session failed."))))
    }

    func testEOFClosesTheTransportAndUnderlyingPTY() async throws {
        let controller = FixtureSSHInteractiveController(reads: [nil])
        let transport = SSHInteractiveTransport(controller: controller)
        _ = await transport.events()

        try await transport.start(makeRequest())
        try await waitUntil { await transport.state() == .closed }

        let closeCount = await controller.closeCount
        let state = await transport.state()
        XCTAssertEqual(closeCount, 1)
        XCTAssertEqual(state, .closed)
    }

    private func makeRequest(initialSnapshot: Data? = nil) -> InteractiveTransportRequest {
        InteractiveTransportRequest(
            host: makeHost(),
            command: "tmux attach -t demo",
            terminal: "xterm-256color",
            geometry: makeGeometry(),
            preference: .ssh,
            initialSnapshot: initialSnapshot
        )
    }

    private func makeHost() -> Host {
        Host(id: "fixture-host", alias: "Fixture", hostname: "fixture.invalid", username: "muxa")
    }

    private func makeGeometry() -> TerminalGeometry {
        TerminalGeometry(columns: 80, rows: 24, pixelWidth: 800, pixelHeight: 480)
    }

    private func waitUntil(
        timeout: Duration = .seconds(2),
        condition: @escaping @Sendable () async -> Bool
    ) async throws {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if await condition() {
                return
            }
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTFail("Timed out waiting for condition")
    }
}

private extension FixtureSSHInteractiveController {
    func setOpenError(_ error: FixtureInteractiveError?) {
        openError = error
    }
}
