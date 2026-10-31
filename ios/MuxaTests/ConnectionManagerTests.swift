@testable import Muxa
import XCTest

final class ConnectionManagerTests: XCTestCase {
    func testReusesHealthyConnectionAndDisconnectsOnSuspend() async throws {
        let controlPlane = ConnectionManagerControlPlaneFixture()
        let manager = ConnectionManager(controlPlane: controlPlane)
        let host = Host(alias: "One", hostname: "one.invalid", username: "root")

        let first = try await manager.connect(to: host)
        let second = try await manager.connect(to: host)
        XCTAssertEqual(first, second)
        let countsAfterReuse = await controlPlane.counts()
        XCTAssertEqual(countsAfterReuse.connects, 1)

        await manager.suspend()
        let countsAfterSuspend = await controlPlane.counts()
        let snapshot = await manager.snapshot(hostID: host.id)
        XCTAssertEqual(countsAfterSuspend.disconnects, 1)
        XCTAssertNil(snapshot?.handle)
    }

    func testExponentialBackoffBlocksImmediateRetry() async {
        let clock = TestClock(Date(timeIntervalSince1970: 100))
        let controlPlane = ConnectionManagerControlPlaneFixture(
            connectError: SSHControlPlaneError.connectionUnavailable
        )
        let manager = ConnectionManager(
            controlPlane: controlPlane,
            policy: ConnectionBackoffPolicy(baseDelay: 5, maximumDelay: 30),
            now: clock.now
        )
        let host = Host(alias: "One", hostname: "one.invalid", username: "root")

        do {
            _ = try await manager.connect(to: host)
            XCTFail("Expected initial connection failure")
        } catch {
            XCTAssertEqual(error as? SSHControlPlaneError, .connectionUnavailable)
        }

        do {
            _ = try await manager.connect(to: host)
            XCTFail("Expected retry backoff")
        } catch let error as ConnectionManagerError {
            XCTAssertEqual(error, .retryBackoff(until: clock.now().addingTimeInterval(5)))
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        clock.advance(by: 5)
        await controlPlane.setConnectError(nil)
        _ = try? await manager.connect(to: host)
        let counts = await controlPlane.counts()
        XCTAssertEqual(counts.connects, 2)
    }

    func testHostKeyConfirmationDoesNotEnterRetryBackoff() async {
        let identity = HostKeyIdentity(
            hostname: "one.invalid",
            port: 22,
            keyType: "ssh-ed25519",
            sha256Fingerprint: "SHA256:first"
        )
        let controlPlane = ConnectionManagerControlPlaneFixture(
            connectError: SSHControlPlaneError.hostKeyConfirmationRequired(identity)
        )
        let manager = ConnectionManager(
            controlPlane: controlPlane,
            policy: ConnectionBackoffPolicy(baseDelay: 5, maximumDelay: 30)
        )
        let host = Host(alias: "One", hostname: identity.hostname, username: "root")

        do {
            _ = try await manager.connect(to: host)
            XCTFail("Expected host-key confirmation")
        } catch let error as SSHControlPlaneError {
            XCTAssertEqual(error, .hostKeyConfirmationRequired(identity))
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        do {
            _ = try await manager.connect(to: host)
            XCTFail("Expected host-key confirmation again")
        } catch let error as SSHControlPlaneError {
            XCTAssertEqual(error, .hostKeyConfirmationRequired(identity))
        } catch {
            XCTFail("Unexpected retry-backoff error: \(error)")
        }

        let counts = await controlPlane.counts()
        XCTAssertEqual(counts.connects, 2)
    }

    func testAuthenticationFailureLetsTheCorrectedUsernameConnectImmediately() async throws {
        let clock = TestClock(Date(timeIntervalSince1970: 100))
        let controlPlane = ConnectionManagerControlPlaneFixture(
            connectError: SSHControlPlaneError.authenticationFailed
        )
        let manager = ConnectionManager(
            controlPlane: controlPlane,
            policy: ConnectionBackoffPolicy(baseDelay: 5, maximumDelay: 30),
            now: clock.now
        )
        let mistyped = Host(id: "host", alias: "VPS", hostname: "vps.invalid", username: "rooy")

        do {
            _ = try await manager.connect(to: mistyped)
            XCTFail("Expected authentication failure")
        } catch let error as SSHControlPlaneError {
            XCTAssertEqual(error, .authenticationFailed)
        }

        await controlPlane.setConnectError(nil)
        let corrected = Host(id: "host", alias: "VPS", hostname: "vps.invalid", username: "root")
        _ = try await manager.connect(to: corrected)
        let counts = await controlPlane.counts()
        let snapshot = await manager.snapshot(hostID: "host")
        XCTAssertEqual(counts.connects, 2)
        XCTAssertNil(snapshot?.retryAt)
    }

    func testUsernameChangeDropsTheHealthyConnection() async throws {
        let controlPlane = ConnectionManagerControlPlaneFixture()
        let manager = ConnectionManager(controlPlane: controlPlane)
        var host = Host(id: "host", alias: "VPS", hostname: "vps.invalid", username: "rooy")

        let first = try await manager.connect(to: host)
        host.username = "root"
        let second = try await manager.connect(to: host)

        XCTAssertNotEqual(first, second)
        let counts = await controlPlane.counts()
        XCTAssertEqual(counts.connects, 2)
        XCTAssertEqual(counts.disconnects, 1)
    }
}

private actor ConnectionManagerControlPlaneFixture: SSHControlPlane {
    var connectError: Error?
    private(set) var connectCount = 0
    private(set) var disconnectCount = 0

    init(connectError: Error? = nil) {
        self.connectError = connectError
    }

    func connect(to host: Host) throws -> SSHConnectionHandle {
        connectCount += 1
        if let connectError {
            throw connectError
        }
        return SSHConnectionHandle(id: UUID(), hostID: host.id)
    }

    func execute(_: SSHCommandRequest, using _: SSHConnectionHandle) throws -> SSHCommandOutput {
        SSHCommandOutput(stdout: Data(), stderr: Data(), exitStatus: 0)
    }

    func isHealthy(_: SSHConnectionHandle) -> Bool {
        true
    }

    func disconnect(_: SSHConnectionHandle) {
        disconnectCount += 1
    }

    func counts() -> (connects: Int, disconnects: Int) {
        (connectCount, disconnectCount)
    }

    func setConnectError(_ error: Error?) {
        connectError = error
    }
}

private final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Date

    init(_ value: Date) {
        self.value = value
    }

    func now() -> Date {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func advance(by interval: TimeInterval) {
        lock.lock()
        value.addTimeInterval(interval)
        lock.unlock()
    }
}
