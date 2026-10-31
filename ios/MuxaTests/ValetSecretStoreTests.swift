@testable import Muxa
import XCTest

final class ValetSecretStoreTests: XCTestCase {
    func testStoresCredentialKindsAndDeletesOnlyRequestedHost() async throws {
        let store = try ValetSecretStore(identifier: "app.mtmux.tests.\(UUID().uuidString)")
        guard await store.isAvailable() else {
            throw XCTSkip("Simulator Keychain is unavailable")
        }
        let password = SecretReference(hostID: "host-a", kind: .password)
        let privateKey = SecretReference(hostID: "host-a", kind: .privateKey)
        let checkpoint = SecretReference(
            hostID: "host-a",
            kind: .transportCheckpoint,
            identifier: "work"
        )
        let otherCheckpoint = SecretReference(
            hostID: "host-a",
            kind: .transportCheckpoint,
            identifier: "other"
        )
        let otherHostKey = SecretReference(hostID: "host-b", kind: .privateKey)

        try await store.store(Data("password".utf8), for: password)
        try await store.store(Data("private-key".utf8), for: privateKey)
        try await store.store(Data("checkpoint".utf8), for: checkpoint)
        try await store.store(Data("other-checkpoint".utf8), for: otherCheckpoint)
        try await store.store(Data("other-host-key".utf8), for: otherHostKey)

        let savedPassword = try await store.retrieve(password)
        let savedPrivateKey = try await store.retrieve(privateKey)
        XCTAssertEqual(savedPassword, Data("password".utf8))
        XCTAssertEqual(savedPrivateKey, Data("private-key".utf8))
        try await store.deleteAll(hostID: "host-a")
        let deletedPassword = try await store.retrieve(password)
        let deletedPrivateKey = try await store.retrieve(privateKey)
        let deletedCheckpoint = try await store.retrieve(checkpoint)
        let deletedOtherCheckpoint = try await store.retrieve(otherCheckpoint)
        let retainedOtherHostKey = try await store.retrieve(otherHostKey)
        XCTAssertNil(deletedPassword)
        XCTAssertNil(deletedPrivateKey)
        XCTAssertNil(deletedCheckpoint)
        XCTAssertNil(deletedOtherCheckpoint)
        XCTAssertEqual(retainedOtherHostKey, Data("other-host-key".utf8))
        try await store.delete(otherHostKey)
    }

    func testDeleteAllSecretsClearsEveryHost() async throws {
        let store = try ValetSecretStore(identifier: "app.mtmux.tests.\(UUID().uuidString)")
        guard await store.isAvailable() else {
            throw XCTSkip("Simulator Keychain is unavailable")
        }
        let password = SecretReference(hostID: "host-a", kind: .password)
        let leftover = SecretReference(hostID: "host-b", kind: .privateKey)
        try await store.store(Data("password".utf8), for: password)
        try await store.store(Data("legacy".utf8), for: leftover)
        try await store.deleteAllSecrets()
        let remainingPassword = try await store.retrieve(password)
        let remainingLeftover = try await store.retrieve(leftover)
        XCTAssertNil(remainingPassword)
        XCTAssertNil(remainingLeftover)
    }

    func testRejectsEmptySecretBeforeKeychainWrite() async throws {
        let store = try ValetSecretStore(identifier: "app.mtmux.tests.\(UUID().uuidString)")
        do {
            try await store.store(Data(), for: SecretReference(hostID: "host", kind: .password))
            XCTFail("Expected empty value rejection")
        } catch {
            XCTAssertEqual(error as? SecretStoreError, .invalidValue)
        }
    }
}
