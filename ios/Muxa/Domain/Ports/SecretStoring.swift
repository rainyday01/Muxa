import Foundation

enum SecretKind: String, Codable, CaseIterable, Sendable {
    case password
    case privateKey
    case privateKeyPassphrase
    case transportCheckpoint
}

struct SecretReference: Codable, Equatable, Hashable, Sendable {
    let hostID: String
    let kind: SecretKind
    let identifier: String?

    init(hostID: String, kind: SecretKind, identifier: String? = nil) {
        self.hostID = hostID
        self.kind = kind
        self.identifier = identifier
    }
}

enum SecretStoreError: Error, Equatable, Sendable {
    case unavailable
    case invalidValue
    case accessDenied
}

protocol SecretStoring: Sendable {
    func store(_ value: Data, for reference: SecretReference) async throws
    func retrieve(_ reference: SecretReference) async throws -> Data?
    func delete(_ reference: SecretReference) async throws
    func deleteAll(hostID: String) async throws
    /// Removes every Muxa Keychain item stored for this app.
    func deleteAllSecrets() async throws
}
