import Foundation

struct HostKeyIdentity: Codable, Equatable, Sendable {
    let hostname: String
    let port: UInt16
    let keyType: String
    let sha256Fingerprint: String
}

enum HostKeyVerification: Equatable, Sendable {
    case unknown(HostKeyIdentity)
    case trusted(HostKeyIdentity)
    case mismatch(expected: HostKeyIdentity, actual: HostKeyIdentity)
}

enum KnownHostsError: Error, Equatable, Sendable {
    case keyChanged(expected: HostKeyIdentity, actual: HostKeyIdentity)
}

protocol KnownHostStoring: Sendable {
    func identities(hostname: String, port: UInt16) async throws -> [HostKeyIdentity]
    func verify(_ identity: HostKeyIdentity, seenAt: Date) async throws -> HostKeyVerification
    func acceptFirst(_ identity: HostKeyIdentity, seenAt: Date) async throws
    func replaceAfterConfirmation(_ identity: HostKeyIdentity, seenAt: Date) async throws
    func remove(hostname: String, port: UInt16) async throws
}
