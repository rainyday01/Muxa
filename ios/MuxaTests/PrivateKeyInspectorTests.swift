@testable import Muxa
import XCTest

final class PrivateKeyInspectorTests: XCTestCase {
    func testDetectsPKCS8RSA() throws {
        let oid = Data([0x30, 0x0B, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01])
        let key = pem(label: "PRIVATE KEY", payload: oid)

        let inspection = try PrivateKeyInspector.inspect(key)

        XCTAssertEqual(inspection.format, .pkcs8)
        XCTAssertEqual(inspection.algorithm, .rsa)
        XCTAssertFalse(inspection.isEncrypted)
    }

    func testDetectsEncryptedPKCS8() throws {
        let key = pem(label: "ENCRYPTED PRIVATE KEY", payload: Data([0x30, 0x01, 0x00]))

        let inspection = try PrivateKeyInspector.inspect(key)

        XCTAssertEqual(inspection.format, .pkcs8)
        XCTAssertTrue(inspection.isEncrypted)
    }

    func testDetectsOpenSSHEd25519() throws {
        var payload = Data("openssh-key-v1\0".utf8)
        payload.append(sshString("none"))
        payload.append(sshString("none"))
        payload.append(sshString(Data()))
        payload.append(contentsOf: [0, 0, 0, 1])
        payload.append(sshString(sshString("ssh-ed25519")))
        let key = pem(label: "OPENSSH PRIVATE KEY", payload: payload)

        let inspection = try PrivateKeyInspector.inspect(key)

        XCTAssertEqual(inspection.format, .openSSH)
        XCTAssertEqual(inspection.algorithm, .ed25519)
        XCTAssertFalse(inspection.isEncrypted)
    }

    func testRejectsPublicKeyAndInvalidBase64() {
        XCTAssertThrowsError(try PrivateKeyInspector.inspect("ssh-ed25519 AAAA test"))
        XCTAssertThrowsError(
            try PrivateKeyInspector.inspect(
                "-----BEGIN PRIVATE KEY-----\nnot-base64\n-----END PRIVATE KEY-----"
            )
        )
    }

    private func pem(label: String, payload: Data) -> String {
        "-----BEGIN \(label)-----\n\(payload.base64EncodedString())\n-----END \(label)-----"
    }

    private func sshString(_ value: String) -> Data {
        sshString(Data(value.utf8))
    }

    private func sshString(_ value: Data) -> Data {
        var result = Data()
        let count = UInt32(value.count)
        result.append(contentsOf: [
            UInt8((count >> 24) & 0xFF),
            UInt8((count >> 16) & 0xFF),
            UInt8((count >> 8) & 0xFF),
            UInt8(count & 0xFF)
        ])
        result.append(value)
        return result
    }
}
