@testable import Muxa
import XCTest

final class ConfigBackupServiceTests: XCTestCase {
    func testExportOmitsPrivateKeysUnlessRequestedAndImportRestoresHosts() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let hosts = GRDBHostRepository(database: database)
        let snippets = GRDBSnippetRepository(database: database)
        let secrets = MemoryBackupSecretStore()
        let host = Host(
            id: "host-1",
            alias: "Lab",
            hostname: "lab.invalid",
            username: "dev",
            hasPrivateKey: true
        )
        try await hosts.save(host)
        try await secrets.store(Data("SECRET-KEY".utf8), for: SecretReference(hostID: host.id, kind: .privateKey))
        try await snippets.save(Snippet(id: "snip-1", name: "List", body: "ls -la {{host}}", tags: ["shell"]))
        let service = ConfigBackupService(hostRepository: hosts, snippetRepository: snippets, secretStore: secrets)

        let withoutKeys = try await service.exportDocument(includePrivateKeys: false)
        XCTAssertEqual(withoutKeys.app, "muxa")
        XCTAssertNil(withoutKeys.hosts.first?.privateKeyPem)

        let withKeys = try await service.exportDocument(includePrivateKeys: true)
        XCTAssertEqual(withKeys.hosts.first?.privateKeyPem, "SECRET-KEY")

        let archive = try await service.exportArchive(password: "test-pass", includePrivateKeys: false)
        try await hosts.delete(id: host.id)
        let imported = try await service.importArchive(archive, password: "test-pass")
        XCTAssertEqual(imported.hosts, 1)
        XCTAssertEqual(imported.snippets, 1)
        let importedHost = try await hosts.fetch(id: host.id)
        XCTAssertEqual(importedHost?.alias, "Lab")
    }
}

private actor MemoryBackupSecretStore: SecretStoring {
    private var values = [SecretReference: Data]()

    func store(_ value: Data, for reference: SecretReference) {
        values[reference] = value
    }

    func retrieve(_ reference: SecretReference) -> Data? {
        values[reference]
    }

    func delete(_ reference: SecretReference) {
        values.removeValue(forKey: reference)
    }

    func deleteAll(hostID: String) {
        values = values.filter { $0.key.hostID != hostID }
    }

    func deleteAllSecrets() {
        values.removeAll()
    }
}
