import Foundation

enum PrivateKeyFormat: String, Sendable {
    case openSSH = "OpenSSH"
    case pkcs8 = "PKCS#8"
    case pkcs1 = "PKCS#1"
    case sec1 = "SEC1"
}

enum PrivateKeyAlgorithm: String, Sendable {
    case rsa = "RSA"
    case ed25519 = "Ed25519"
    case ecdsa = "ECDSA"
    case unknown = "Unknown"
}

struct PrivateKeyInspection: Equatable, Sendable {
    let format: PrivateKeyFormat
    let algorithm: PrivateKeyAlgorithm
    let isEncrypted: Bool

    var summary: String {
        let encryption = isEncrypted ? "Encrypted" : "Unencrypted"
        return "\(algorithm.rawValue) · \(format.rawValue) · \(encryption)"
    }
}

enum PrivateKeyInspectionError: Error, Equatable, Sendable {
    case empty
    case tooLarge
    case unsupportedContainer
    case mismatchedFooter
    case invalidBase64
    case invalidPayload
}

enum PrivateKeyInspector {
    static let maximumUTF8Bytes = 1_048_576

    static func inspect(_ value: String) throws -> PrivateKeyInspection {
        let key = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { throw PrivateKeyInspectionError.empty }
        guard key.utf8.count <= maximumUTF8Bytes else { throw PrivateKeyInspectionError.tooLarge }
        let container = try container(for: key)
        let payload = try decodePayload(key, container: container)
        guard payload.first == 0x30 || container.format == .openSSH else {
            throw PrivateKeyInspectionError.invalidPayload
        }
        switch container.format {
        case .openSSH:
            return try inspectOpenSSH(payload)
        case .pkcs8:
            return PrivateKeyInspection(
                format: .pkcs8,
                algorithm: container.encrypted ? .unknown : algorithmFromDER(payload),
                isEncrypted: container.encrypted
            )
        case .pkcs1:
            return PrivateKeyInspection(format: .pkcs1, algorithm: .rsa, isEncrypted: container.encrypted)
        case .sec1:
            return PrivateKeyInspection(format: .sec1, algorithm: .ecdsa, isEncrypted: container.encrypted)
        }
    }

    private static func container(for key: String) throws -> KeyContainer {
        let firstLine = key.components(separatedBy: .newlines).first ?? ""
        guard let container = KeyContainer.supported.first(where: { $0.header == firstLine }) else {
            throw PrivateKeyInspectionError.unsupportedContainer
        }
        guard key.components(separatedBy: .newlines).contains(container.footer) else {
            throw PrivateKeyInspectionError.mismatchedFooter
        }
        let encrypted = container.encrypted || key.contains("Proc-Type: 4,ENCRYPTED")
        return KeyContainer(
            header: container.header,
            footer: container.footer,
            format: container.format,
            encrypted: encrypted
        )
    }

    private static func decodePayload(_ key: String, container: KeyContainer) throws -> Data {
        let lines = key.components(separatedBy: .newlines)
        guard let headerIndex = lines.firstIndex(of: container.header),
              let footerIndex = lines.firstIndex(of: container.footer),
              headerIndex < footerIndex
        else {
            throw PrivateKeyInspectionError.mismatchedFooter
        }
        let encoded = lines[(headerIndex + 1) ..< footerIndex]
            .filter { !$0.contains(":") }
            .joined()
        guard let payload = Data(base64Encoded: encoded), !payload.isEmpty else {
            throw PrivateKeyInspectionError.invalidBase64
        }
        return payload
    }

    private static func inspectOpenSSH(_ payload: Data) throws -> PrivateKeyInspection {
        var reader = SSHBinaryReader(data: payload)
        guard reader.readBytes(count: 15) == Data("openssh-key-v1\0".utf8),
              let cipher = reader.readString(),
              reader.readString() != nil,
              reader.readString() != nil,
              let keyCount = reader.readUInt32(),
              keyCount > 0,
              let publicKey = reader.readString()
        else {
            throw PrivateKeyInspectionError.invalidPayload
        }
        var publicReader = SSHBinaryReader(data: publicKey)
        guard let keyTypeData = publicReader.readString(),
              let keyType = String(data: keyTypeData, encoding: .utf8),
              let cipherName = String(data: cipher, encoding: .utf8)
        else {
            throw PrivateKeyInspectionError.invalidPayload
        }
        return PrivateKeyInspection(
            format: .openSSH,
            algorithm: algorithmFromSSHKeyType(keyType),
            isEncrypted: cipherName != "none"
        )
    }

    private static func algorithmFromSSHKeyType(_ value: String) -> PrivateKeyAlgorithm {
        if value == "ssh-ed25519" {
            return .ed25519
        }
        if value == "ssh-rsa" || value.contains("rsa-sha2") {
            return .rsa
        }
        if value.hasPrefix("ecdsa-sha2-") {
            return .ecdsa
        }
        return .unknown
    }

    private static func algorithmFromDER(_ data: Data) -> PrivateKeyAlgorithm {
        let rsaOID = Data([0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01])
        let ecOID = Data([0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01])
        let ed25519OID = Data([0x06, 0x03, 0x2B, 0x65, 0x70])
        if data.range(of: rsaOID) != nil {
            return .rsa
        }
        if data.range(of: ecOID) != nil {
            return .ecdsa
        }
        if data.range(of: ed25519OID) != nil {
            return .ed25519
        }
        return .unknown
    }

    private struct KeyContainer {
        let header: String
        let footer: String
        let format: PrivateKeyFormat
        let encrypted: Bool

        static let supported = [
            KeyContainer(
                header: "-----BEGIN OPENSSH PRIVATE KEY-----",
                footer: "-----END OPENSSH PRIVATE KEY-----",
                format: .openSSH,
                encrypted: false
            ),
            KeyContainer(
                header: "-----BEGIN PRIVATE KEY-----",
                footer: "-----END PRIVATE KEY-----",
                format: .pkcs8,
                encrypted: false
            ),
            KeyContainer(
                header: "-----BEGIN ENCRYPTED PRIVATE KEY-----",
                footer: "-----END ENCRYPTED PRIVATE KEY-----",
                format: .pkcs8,
                encrypted: true
            ),
            KeyContainer(
                header: "-----BEGIN RSA PRIVATE KEY-----",
                footer: "-----END RSA PRIVATE KEY-----",
                format: .pkcs1,
                encrypted: false
            ),
            KeyContainer(
                header: "-----BEGIN EC PRIVATE KEY-----",
                footer: "-----END EC PRIVATE KEY-----",
                format: .sec1,
                encrypted: false
            )
        ]
    }
}

private struct SSHBinaryReader {
    let data: Data
    private var offset = 0

    init(data: Data) {
        self.data = data
    }

    mutating func readUInt32() -> UInt32? {
        guard let bytes = readBytes(count: 4) else { return nil }
        return bytes.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
    }

    mutating func readString() -> Data? {
        guard let length = readUInt32(), let count = Int(exactly: length) else { return nil }
        return readBytes(count: count)
    }

    mutating func readBytes(count: Int) -> Data? {
        guard count >= 0, offset <= data.count, count <= data.count - offset else { return nil }
        defer { offset += count }
        return data.subdata(in: offset ..< (offset + count))
    }
}
