import Foundation

enum HostDeletionError: Error, Equatable, Sendable {
    case secretCleanupFailed
    case databaseDeletionFailed
}

actor HostDeletionService {
    private let repository: any HostRepository
    private let secretStore: any SecretStoring
    private let database: MuxaDatabase?

    init(
        repository: any HostRepository,
        secretStore: any SecretStoring,
        database: MuxaDatabase? = nil
    ) {
        self.repository = repository
        self.secretStore = secretStore
        self.database = database
    }

    func delete(hostID: String) async throws {
        do {
            try await secretStore.deleteAll(hostID: hostID)
        } catch {
            throw HostDeletionError.secretCleanupFailed
        }
        do {
            try await repository.delete(id: hostID)
        } catch {
            throw HostDeletionError.databaseDeletionFailed
        }
    }

    /// Removes every Muxa record and secret stored on this device.
    func deleteAllData() async throws {
        do {
            try await secretStore.deleteAllSecrets()
        } catch {
            throw HostDeletionError.secretCleanupFailed
        }

        if let database {
            do {
                try await repository.deleteAll()
                try database.deleteAllNonHostData()
            } catch {
                throw HostDeletionError.databaseDeletionFailed
            }
            return
        }

        // Keep the dependency-light fallback used by previews and unit tests.
        let hosts = try await repository.fetchAll()
        for host in hosts {
            try await delete(hostID: host.id)
        }
    }
}
