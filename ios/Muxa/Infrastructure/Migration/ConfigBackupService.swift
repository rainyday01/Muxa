import Foundation

enum ConfigBackupError: Error, Equatable, Sendable {
    case emptyPassword
    case noHostsImported
}

actor ConfigBackupService {
    private let hostRepository: any HostRepository
    private let snippetRepository: any SnippetRepository
    private let secretStore: any SecretStoring

    init(
        hostRepository: any HostRepository,
        snippetRepository: any SnippetRepository,
        secretStore: any SecretStoring
    ) {
        self.hostRepository = hostRepository
        self.snippetRepository = snippetRepository
        self.secretStore = secretStore
    }

    func exportDocument(includePrivateKeys: Bool) async throws -> AndroidConfigDocument {
        let hosts = try await hostRepository.fetchAll()
        let snippets = try await snippetRepository.fetchAll()
        var exportedHosts = [AndroidConfigHost]()
        for host in hosts {
            var pem: String?
            if includePrivateKeys, host.hasPrivateKey {
                pem = try await secretStore.retrieve(SecretReference(hostID: host.id, kind: .privateKey))
                    .flatMap { String(data: $0, encoding: .utf8) }
            }
            exportedHosts.append(
                AndroidConfigHost(
                    id: host.id,
                    alias: host.alias,
                    hostname: host.hostname,
                    port: Int(host.port),
                    username: host.username,
                    transport: host.transport.rawValue,
                    privateKeyPem: pem
                )
            )
        }
        return AndroidConfigDocument(
            version: 1,
            app: "muxa",
            hosts: exportedHosts,
            snippets: snippets.map {
                AndroidConfigSnippet(
                    id: $0.id,
                    name: $0.name,
                    body: $0.body,
                    tags: $0.tags.joined(separator: ","),
                    runOnSelect: $0.runOnSelect
                )
            }
        )
    }

    func exportArchive(password: String, includePrivateKeys: Bool) async throws -> Data {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ConfigBackupError.emptyPassword }
        return try await AndroidConfigArchive.encrypt(
            document: exportDocument(includePrivateKeys: includePrivateKeys),
            password: trimmed
        )
    }

    func importArchive(_ data: Data, password: String) async throws -> (hosts: Int, snippets: Int) {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ConfigBackupError.emptyPassword }
        let document = try AndroidConfigArchive.decrypt(document: data, password: trimmed)
        for host in document.hosts {
            let imported = Host(
                id: host.id,
                alias: host.alias,
                hostname: host.hostname,
                port: UInt16(clamping: host.port),
                username: host.username,
                transport: .ssh,
                hasPrivateKey: host.privateKeyPem != nil,
                hasPassword: false
            )
            try await hostRepository.save(imported)
            if let pem = host.privateKeyPem, let data = pem.data(using: .utf8), !data.isEmpty {
                try await secretStore.store(data, for: SecretReference(hostID: host.id, kind: .privateKey))
            }
        }
        for snippet in document.snippets {
            try await snippetRepository.save(
                Snippet(
                    id: snippet.id,
                    name: snippet.name,
                    body: snippet.body,
                    tags: snippet.tags.split(separator: ",").map {
                        $0.trimmingCharacters(in: .whitespaces)
                    }.filter { !$0.isEmpty },
                    runOnSelect: snippet.runOnSelect
                )
            )
        }
        return (document.hosts.count, document.snippets.count)
    }
}
