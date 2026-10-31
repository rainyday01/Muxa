@testable import Muxa
import XCTest

final class AndroidConfigArchiveTests: XCTestCase {
    private let password = "Muxa fixture pass 2026"

    func testDecryptsAndroidV1GoldenFixture() throws {
        let archive = try fixture(named: "android-config-v1-envelope")
        let expected = try fixture(named: "android-config-v1-plaintext").trimmingTrailingNewline()

        let plaintext = try AndroidConfigArchive.decrypt(archive: archive, password: password)
        let document = try AndroidConfigArchive.decrypt(document: archive, password: password)

        XCTAssertEqual(plaintext, expected)
        XCTAssertEqual(document.version, 1)
        XCTAssertEqual(document.app, "muxa")
        XCTAssertEqual(document.hosts.first?.transport, "AUTO")
        XCTAssertEqual(document.snippets.first?.body, "ls -la")
        XCTAssertNil(document.hosts.first?.privateKeyPem)
    }

    func testEncryptsByteIdenticalAndroidV1GoldenVector() throws {
        let plaintext = try fixture(named: "android-config-v1-plaintext").trimmingTrailingNewline()
        let expectedArchive = try fixture(named: "android-config-v1-envelope")
        let expected = try JSONDecoder().decode(EnvelopeFixture.self, from: expectedArchive)

        let archive = try AndroidConfigArchive.encrypt(
            plaintext: plaintext,
            password: password,
            salt: Data(0x00 ... 0x0F),
            initializationVector: Data(0x10 ... 0x1B)
        )
        let actual = try JSONDecoder().decode(EnvelopeFixture.self, from: archive)

        XCTAssertEqual(actual, expected)
    }

    func testRandomArchiveRoundTripAndWrongPasswordRejection() throws {
        let document = AndroidConfigDocument(
            version: 1,
            app: "muxa",
            hosts: [
                AndroidConfigHost(
                    id: "host-key",
                    alias: "Key host",
                    hostname: "key.invalid",
                    port: 2222,
                    username: "dev",
                    transport: "SSH",
                    privateKeyPem: "private-key-fixture"
                )
            ],
            snippets: []
        )
        let archive = try AndroidConfigArchive.encrypt(document: document, password: password)

        XCTAssertEqual(try AndroidConfigArchive.decrypt(document: archive, password: password), document)
        XCTAssertThrowsError(try AndroidConfigArchive.decrypt(archive: archive, password: "wrong-password")) {
            XCTAssertEqual($0 as? AndroidConfigArchiveError, .authenticationFailed)
        }
    }

    private func fixture(named name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try Data(contentsOf: url)
    }
}

private struct EnvelopeFixture: Codable, Equatable {
    let version: Int
    let salt: Data
    let initializationVector: Data
    let ciphertext: Data

    private enum CodingKeys: String, CodingKey {
        case version = "v"
        case salt
        case initializationVector = "iv"
        case ciphertext = "ct"
    }
}

private extension Data {
    func trimmingTrailingNewline() -> Data {
        var result = self
        while result.last == 0x0A || result.last == 0x0D {
            result.removeLast()
        }
        return result
    }
}
