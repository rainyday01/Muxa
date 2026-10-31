@testable import Muxa
import XCTest

final class KnownHostsStoreTests: XCTestCase {
    func testUnknownAcceptMatchAndMismatchPolicy() async throws {
        let store = try GRDBKnownHostsStore(database: MuxaDatabase(inMemory: true))
        let first = HostKeyIdentity(
            hostname: "host.invalid",
            port: 22,
            keyType: "ssh-ed25519",
            sha256Fingerprint: HostKeyFingerprint.sha256(Data("first-key".utf8))
        )
        let changed = HostKeyIdentity(
            hostname: first.hostname,
            port: first.port,
            keyType: first.keyType,
            sha256Fingerprint: HostKeyFingerprint.sha256(Data("changed-key".utf8))
        )

        let unknown = try await store.verify(first, seenAt: Date(timeIntervalSince1970: 1))
        XCTAssertEqual(unknown, .unknown(first))
        try await store.acceptFirst(first, seenAt: Date(timeIntervalSince1970: 1))
        let trusted = try await store.verify(first, seenAt: Date(timeIntervalSince1970: 2))
        let mismatch = try await store.verify(changed, seenAt: Date(timeIntervalSince1970: 3))
        XCTAssertEqual(trusted, .trusted(first))
        XCTAssertEqual(mismatch, .mismatch(expected: first, actual: changed))

        do {
            try await store.acceptFirst(changed, seenAt: Date(timeIntervalSince1970: 3))
            XCTFail("Expected silent replacement to be blocked")
        } catch {
            XCTAssertEqual(error as? KnownHostsError, .keyChanged(expected: first, actual: changed))
        }
        let stillMismatch = try await store.verify(changed, seenAt: Date(timeIntervalSince1970: 4))
        XCTAssertEqual(stillMismatch, .mismatch(expected: first, actual: changed))
    }

    func testExplicitReplacementAndRemoval() async throws {
        let store = try GRDBKnownHostsStore(database: MuxaDatabase(inMemory: true))
        let identity = HostKeyIdentity(
            hostname: "host.invalid",
            port: 2222,
            keyType: "ecdsa-sha2-nistp256",
            sha256Fingerprint: HostKeyFingerprint.sha256(Data("key".utf8))
        )

        try await store.replaceAfterConfirmation(identity, seenAt: Date())
        let trusted = try await store.verify(identity, seenAt: Date())
        XCTAssertEqual(trusted, .trusted(identity))
        try await store.remove(hostname: identity.hostname, port: identity.port)
        let unknown = try await store.verify(identity, seenAt: Date())
        XCTAssertEqual(unknown, .unknown(identity))
    }
}
