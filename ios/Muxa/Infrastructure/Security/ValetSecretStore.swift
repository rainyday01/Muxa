import Foundation
import Valet

actor ValetSecretStore: SecretStoring {
    private let valet: Valet

    init(identifier: String = "app.mtmux.ios.secrets") throws {
        guard let identifier = Identifier(nonEmpty: identifier) else {
            throw SecretStoreError.invalidValue
        }
        valet = Valet.valet(with: identifier, accessibility: .whenUnlockedThisDeviceOnly)
    }

    func isAvailable() -> Bool {
        valet.canAccessKeychain()
    }

    func store(_ value: Data, for reference: SecretReference) throws {
        guard !value.isEmpty else { throw SecretStoreError.invalidValue }
        do {
            try valet.setObject(value, forKey: Self.key(reference))
        } catch {
            throw Self.map(error)
        }
    }

    func retrieve(_ reference: SecretReference) throws -> Data? {
        do {
            return try valet.object(forKey: Self.key(reference))
        } catch KeychainError.itemNotFound {
            return nil
        } catch {
            throw Self.map(error)
        }
    }

    func delete(_ reference: SecretReference) throws {
        do {
            try valet.removeObject(forKey: Self.key(reference))
        } catch KeychainError.itemNotFound {
            return
        } catch {
            throw Self.map(error)
        }
    }

    func deleteAll(hostID: String) throws {
        let prefix = Self.hostPrefix(hostID)
        do {
            for key in try valet.allKeys() where key.hasPrefix(prefix) {
                try valet.removeObject(forKey: key)
            }
        } catch {
            throw Self.map(error)
        }
    }

    func deleteAllSecrets() throws {
        do {
            try valet.removeAllObjects()
        } catch {
            throw Self.map(error)
        }
    }

    private static func key(_ reference: SecretReference) -> String {
        let identifier = reference.identifier.map { ":\(encode($0))" } ?? ""
        return hostPrefix(reference.hostID) + reference.kind.rawValue + identifier
    }

    private static func hostPrefix(_ hostID: String) -> String {
        "v1:\(encode(hostID)):"
    }

    private static func encode(_ value: String) -> String {
        Data(value.utf8).base64EncodedString()
    }

    private static func map(_ error: Error) -> SecretStoreError {
        switch error as? KeychainError {
        case .emptyKey, .emptyValue:
            .invalidValue
        case .missingEntitlement, .userCancelled:
            .accessDenied
        case .couldNotAccessKeychain, .itemNotFound, nil:
            .unavailable
        }
    }
}
