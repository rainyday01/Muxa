@testable import Muxa
import XCTest

private actor HostEditorRepositoryFixture: HostRepository {
    private(set) var hosts = [Host]()

    func observeAll() -> AsyncStream<[Host]> {
        AsyncStream { continuation in
            continuation.yield(hosts)
            continuation.finish()
        }
    }

    func fetchAll() -> [Host] {
        hosts
    }

    func fetch(id: String) -> Host? {
        hosts.first { $0.id == id }
    }

    func save(_ host: Host) {
        hosts.removeAll { $0.id == host.id }
        hosts.append(host)
    }

    func updateResources(hostID: String, resources: HostResources) {
        guard !resources.isEmpty, let index = hosts.firstIndex(where: { $0.id == hostID }) else { return }
        hosts[index] = hosts[index].applying(resources: resources)
    }

    func delete(id: String) {
        hosts.removeAll { $0.id == id }
    }

    func deleteAll() {
        hosts.removeAll()
    }
}

private actor HostEditorSecretStoreFixture: SecretStoring {
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

@MainActor
final class HostEditorStoreTests: XCTestCase {
    func testSavesHostAndPasswordSeparately() async throws {
        let repository = HostEditorRepositoryFixture()
        let secrets = HostEditorSecretStoreFixture()
        let store = HostEditorStore(host: nil, repository: repository, secretStore: secrets)
        store.alias = "Production"
        store.hostname = "example.invalid"
        store.username = "root"
        store.password = "test-password"

        let saved = await store.save()
        XCTAssertTrue(saved)

        let hosts = await repository.fetchAll()
        let host = try XCTUnwrap(hosts.first)
        XCTAssertEqual(host.alias, "Production")
        XCTAssertEqual(host.port, 22)
        XCTAssertEqual(host.transport, .ssh)
        XCTAssertTrue(host.hasPassword)
        XCTAssertFalse(host.hasPrivateKey)
        let password = await secrets.retrieve(SecretReference(hostID: host.id, kind: .password))
        XCTAssertEqual(password, Data("test-password".utf8))
    }

    func testEditingWithoutNewPasswordKeepsStoredCredential() async {
        let host = Host(
            id: "host-id",
            alias: "Old",
            hostname: "example.invalid",
            username: "root",
            hasPassword: true
        )
        let repository = HostEditorRepositoryFixture()
        await repository.save(host)
        let secrets = HostEditorSecretStoreFixture()
        let reference = SecretReference(hostID: host.id, kind: .password)
        await secrets.store(Data("existing".utf8), for: reference)
        let store = HostEditorStore(host: host, repository: repository, secretStore: secrets)
        store.alias = "Updated"

        let saved = await store.save()
        let password = await secrets.retrieve(reference)
        let updatedHost = await repository.fetch(id: host.id)
        XCTAssertTrue(saved)
        XCTAssertEqual(password, Data("existing".utf8))
        XCTAssertEqual(updatedHost?.alias, "Updated")
    }

    func testSwitchingToKeyDeletesPassword() async {
        let host = Host(
            id: "host-id",
            alias: "Server",
            hostname: "example.invalid",
            username: "root",
            hasPassword: true
        )
        let repository = HostEditorRepositoryFixture()
        await repository.save(host)
        let secrets = HostEditorSecretStoreFixture()
        let passwordReference = SecretReference(hostID: host.id, kind: .password)
        await secrets.store(Data("existing".utf8), for: passwordReference)
        let store = HostEditorStore(host: host, repository: repository, secretStore: secrets)
        store.authentication = .privateKey
        store.privateKey = unencryptedOpenSSHKey(algorithm: "ssh-ed25519")

        let saved = await store.save()
        let password = await secrets.retrieve(passwordReference)
        let updatedHost = await repository.fetch(id: host.id)
        XCTAssertTrue(saved)
        XCTAssertNil(password)
        XCTAssertEqual(updatedHost?.hasPrivateKey, true)
        XCTAssertEqual(updatedHost?.hasPassword, false)
    }

    func testSanitizesPastedEndpointBeforeSaving() async {
        let repository = HostEditorRepositoryFixture()
        let secrets = HostEditorSecretStoreFixture()
        let store = HostEditorStore(host: nil, repository: repository, secretStore: secrets)
        store.alias = "Pasted"
        store.hostname = "root:secret@example.invalid:2222"

        let saved = await store.save()
        let host = await repository.fetchAll().first
        let storedPassword = await secrets.retrieve(
            SecretReference(hostID: host?.id ?? "", kind: .password)
        )
        XCTAssertTrue(saved)
        XCTAssertEqual(host?.hostname, "example.invalid")
        XCTAssertEqual(host?.username, "root")
        XCTAssertEqual(host?.port, 2222)
        XCTAssertEqual(storedPassword, Data("secret".utf8))
    }

    private func unencryptedOpenSSHKey(algorithm: String) -> String {
        var payload = Data("openssh-key-v1\0".utf8)
        payload.append(sshString("none"))
        payload.append(sshString("none"))
        payload.append(sshString(Data()))
        payload.append(contentsOf: [0, 0, 0, 1])
        payload.append(sshString(sshString(algorithm)))
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n\(payload.base64EncodedString())\n"
            + "-----END OPENSSH PRIVATE KEY-----"
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
