@testable import Muxa
import XCTest

final class SSHControlPlaneTests: XCTestCase {
    func testPasswordHostWithoutSecretFailsBeforeNetworkConnection() async {
        let controlPlane = LiveSSHControlPlane(
            secretStore: EmptySSHSecretStoreFixture(),
            knownHosts: EmptySSHKnownHostStoreFixture()
        )
        let host = Host(
            alias: "Password host",
            hostname: "example.invalid",
            username: "root",
            hasPassword: true
        )

        do {
            _ = try await controlPlane.connect(to: host)
            XCTFail("Expected missing password credential")
        } catch let error as SSHControlPlaneError {
            XCTAssertEqual(error, .missingCredential("password"))
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testPrivateKeyHostWithoutKeyFailsBeforeNetworkConnection() async {
        let controlPlane = LiveSSHControlPlane(
            secretStore: EmptySSHSecretStoreFixture(),
            knownHosts: EmptySSHKnownHostStoreFixture()
        )
        let host = Host(
            alias: "Key host",
            hostname: "example.invalid",
            username: "root",
            hasPrivateKey: true
        )

        do {
            _ = try await controlPlane.connect(to: host)
            XCTFail("Expected missing private key credential")
        } catch let error as SSHControlPlaneError {
            XCTAssertEqual(error, .missingCredential("private key"))
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }
}

private actor EmptySSHSecretStoreFixture: SecretStoring {
    func store(_: Data, for _: SecretReference) {}

    func retrieve(_: SecretReference) -> Data? {
        nil
    }

    func delete(_: SecretReference) {}

    func deleteAll(hostID _: String) {}
    func deleteAllSecrets() {}
}

private actor EmptySSHKnownHostStoreFixture: KnownHostStoring {
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
