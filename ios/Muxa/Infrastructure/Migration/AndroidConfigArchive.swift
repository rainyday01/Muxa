import CommonCrypto
import CryptoKit
import Foundation
import Security

struct AndroidConfigDocument: Codable, Equatable, Sendable {
    let version: Int
    let app: String
    let hosts: [AndroidConfigHost]
    let snippets: [AndroidConfigSnippet]
}

struct AndroidConfigHost: Codable, Equatable, Sendable {
    let id: String
    let alias: String
    let hostname: String
    let port: Int
    let username: String
    let transport: String
    let privateKeyPem: String?
}

struct AndroidConfigSnippet: Codable, Equatable, Sendable {
    let id: String
    let name: String
    let body: String
    let tags: String
    let runOnSelect: Bool
}

enum AndroidConfigArchiveError: Error, Equatable {
    case invalidEnvelope
    case unsupportedVersion(Int)
    case invalidSalt
    case invalidIV
    case invalidCiphertext
    case keyDerivation(Int32)
    case authenticationFailed
    case randomGeneration(Int32)
}

enum AndroidConfigArchive {
    private static let iterations = 120_000
    private static let keyLength = 32
    private static let saltLength = 16
    private static let ivLength = 12
    private static let tagLength = 16

    static func encrypt(document: AndroidConfigDocument, password: String) throws -> Data {
        let plaintext = try JSONEncoder().encode(document)
        return try encrypt(
            plaintext: plaintext,
            password: password,
            salt: randomData(count: saltLength),
            initializationVector: randomData(count: ivLength)
        )
    }

    static func decrypt(document archive: Data, password: String) throws -> AndroidConfigDocument {
        let plaintext = try decrypt(archive: archive, password: password)
        return try JSONDecoder().decode(AndroidConfigDocument.self, from: plaintext)
    }

    static func encrypt(
        plaintext: Data,
        password: String,
        salt: Data,
        initializationVector: Data
    ) throws -> Data {
        guard salt.count == saltLength else { throw AndroidConfigArchiveError.invalidSalt }
        guard initializationVector.count == ivLength else { throw AndroidConfigArchiveError.invalidIV }
        let key = try deriveKey(password: password, salt: salt)
        let nonce = try AES.GCM.Nonce(data: initializationVector)
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        let envelope = AndroidConfigEnvelope(
            version: 1,
            salt: salt,
            initializationVector: initializationVector,
            ciphertext: sealed.ciphertext + sealed.tag
        )
        return try JSONEncoder().encode(envelope)
    }

    static func decrypt(archive: Data, password: String) throws -> Data {
        let envelope: AndroidConfigEnvelope
        do {
            envelope = try JSONDecoder().decode(AndroidConfigEnvelope.self, from: archive)
        } catch {
            throw AndroidConfigArchiveError.invalidEnvelope
        }
        guard envelope.version == 1 else {
            throw AndroidConfigArchiveError.unsupportedVersion(envelope.version)
        }
        guard envelope.salt.count == saltLength else { throw AndroidConfigArchiveError.invalidSalt }
        guard envelope.initializationVector.count == ivLength else {
            throw AndroidConfigArchiveError.invalidIV
        }
        guard envelope.ciphertext.count >= tagLength else {
            throw AndroidConfigArchiveError.invalidCiphertext
        }

        let key = try deriveKey(password: password, salt: envelope.salt)
        let nonce = try AES.GCM.Nonce(data: envelope.initializationVector)
        let ciphertext = envelope.ciphertext.dropLast(tagLength)
        let tag = envelope.ciphertext.suffix(tagLength)
        let sealed = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
        do {
            return try AES.GCM.open(sealed, using: key)
        } catch {
            throw AndroidConfigArchiveError.authenticationFailed
        }
    }

    private static func deriveKey(password: String, salt: Data) throws -> SymmetricKey {
        let passwordBytes = Data(password.utf8)
        var derived = Data(repeating: 0, count: keyLength)
        let result = passwordBytes.withUnsafeBytes { passwordBuffer in
            salt.withUnsafeBytes { saltBuffer in
                derived.withUnsafeMutableBytes { keyBuffer in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBuffer.bindMemory(to: Int8.self).baseAddress,
                        passwordBytes.count,
                        saltBuffer.bindMemory(to: UInt8.self).baseAddress,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        keyBuffer.bindMemory(to: UInt8.self).baseAddress,
                        keyLength
                    )
                }
            }
        }
        guard result == kCCSuccess else { throw AndroidConfigArchiveError.keyDerivation(result) }
        return SymmetricKey(data: derived)
    }

    private static func randomData(count: Int) throws -> Data {
        var data = Data(repeating: 0, count: count)
        let status = data.withUnsafeMutableBytes { buffer in
            guard let address = buffer.baseAddress else { return errSecParam }
            return SecRandomCopyBytes(kSecRandomDefault, count, address)
        }
        guard status == errSecSuccess else {
            throw AndroidConfigArchiveError.randomGeneration(status)
        }
        return data
    }
}

private struct AndroidConfigEnvelope: Codable {
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
