import Foundation
import Observation

enum HostAuthenticationMethod: String, CaseIterable, Identifiable, Sendable {
    case password
    case privateKey
    case passwordAndPrivateKey

    var id: Self {
        self
    }

    var title: String {
        switch self {
        case .password: "Password"
        case .privateKey: "Private Key"
        case .passwordAndPrivateKey: "Password & Key"
        }
    }

    var usesPassword: Bool {
        self != .privateKey
    }

    var usesPrivateKey: Bool {
        self != .password
    }
}

@MainActor
@Observable
final class HostEditorStore {
    private struct NormalizedFields {
        let alias: String
        let hostname: String
        let port: UInt16
        let username: String
    }

    private let initialHost: Host?
    private let repository: any HostRepository
    private let secretStore: any SecretStoring

    var alias: String
    var hostname: String
    var port: String
    var username: String
    var authentication: HostAuthenticationMethod
    var password = ""
    var privateKey = ""
    var passphrase = ""

    private(set) var isSaving = false
    private(set) var errorMessage: String?
    private(set) var savedHost: Host?
    private(set) var privateKeyInspection: PrivateKeyInspection?

    var isEditing: Bool {
        initialHost != nil
    }

    init(host: Host?, repository: any HostRepository, secretStore: any SecretStoring) {
        initialHost = host
        self.repository = repository
        self.secretStore = secretStore
        alias = host?.alias ?? ""
        hostname = host?.hostname ?? ""
        port = host.map { String($0.port) } ?? ""
        username = host?.username ?? ""
        switch (host?.hasPassword, host?.hasPrivateKey) {
        case (true, true): authentication = .passwordAndPrivateKey
        case (_, true): authentication = .privateKey
        default: authentication = .password
        }
    }

    func save() async -> Bool {
        guard !isSaving else { return false }
        applyParsedHostInput()
        guard let fields = normalizedFields(), validateAuthentication() else { return false }
        return await persist(fields)
    }

    func inspectPrivateKey() {
        guard !privateKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            privateKeyInspection = nil
            return
        }
        privateKeyInspection = try? PrivateKeyInspector.inspect(privateKey)
    }

    func importPrivateKey(from url: URL) {
        let hasSecurityScope = url.startAccessingSecurityScopedResource()
        defer {
            if hasSecurityScope {
                url.stopAccessingSecurityScopedResource()
            }
        }
        do {
            let data = try Data(contentsOf: url, options: .mappedIfSafe)
            guard data.count <= PrivateKeyInspector.maximumUTF8Bytes,
                  let value = String(data: data, encoding: .utf8)
            else {
                errorMessage = "The selected private key is not a supported text file."
                return
            }
            _ = try PrivateKeyInspector.inspect(value)
            privateKey = value
            inspectPrivateKey()
            errorMessage = nil
        } catch {
            errorMessage = "The selected file is not a supported private key."
        }
    }

    private func applyParsedHostInput() {
        let parsedInput = HostFieldSanitizer.parse(hostname)
        let usernameIsEmpty = username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if usernameIsEmpty, let parsedUsername = parsedInput.username {
            username = parsedUsername
        }
        if let parsedEndpointPort = parsedInput.port {
            port = String(parsedEndpointPort)
        }
        if password.isEmpty, let parsedPassword = parsedInput.password {
            password = parsedPassword
        }
        hostname = parsedInput.host
    }

    private func normalizedFields() -> NormalizedFields? {
        let trimmedPort = port.trimmingCharacters(in: .whitespaces)
        let portToken = trimmedPort.isEmpty ? "22" : trimmedPort
        guard let parsedPort = UInt16(portToken), parsedPort > 0 else {
            errorMessage = "Enter a port between 1 and 65535."
            return nil
        }
        let trimmedAlias = alias.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedHostname = hostname.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedAlias.isEmpty, !trimmedHostname.isEmpty, !trimmedUsername.isEmpty else {
            errorMessage = "Alias, host, and username are required."
            return nil
        }
        guard trimmedAlias.count <= 80 else {
            errorMessage = "Alias must be 80 characters or fewer."
            return nil
        }
        guard HostFieldSanitizer.isPlausibleHost(trimmedHostname) else {
            errorMessage = "Enter a valid hostname or IP address."
            return nil
        }
        guard HostFieldSanitizer.isPlausibleUsername(trimmedUsername) else {
            errorMessage = "Enter a valid username without spaces or @."
            return nil
        }
        return NormalizedFields(
            alias: trimmedAlias,
            hostname: trimmedHostname,
            port: parsedPort,
            username: trimmedUsername
        )
    }

    private func validateAuthentication() -> Bool {
        if authentication.usesPassword, password.isEmpty, initialHost?.hasPassword != true {
            errorMessage = "Enter a password."
            return false
        }
        if authentication.usesPrivateKey, privateKey.isEmpty, initialHost?.hasPrivateKey != true {
            errorMessage = "Enter a private key."
            return false
        }
        if authentication.usesPrivateKey, !privateKey.isEmpty {
            do {
                let inspection = try PrivateKeyInspector.inspect(privateKey)
                privateKeyInspection = inspection
                if inspection.isEncrypted, passphrase.isEmpty {
                    errorMessage = "Enter the passphrase for this encrypted private key."
                    return false
                }
            } catch {
                errorMessage = "Enter a supported OpenSSH or PEM private key."
                return false
            }
        }
        return true
    }

    private func persist(_ fields: NormalizedFields) async -> Bool {
        isSaving = true
        defer { isSaving = false }
        let hostID = initialHost?.id ?? UUID().uuidString
        do {
            try await updateSecrets(hostID: hostID)
            let host = Host(
                id: hostID,
                alias: fields.alias,
                hostname: fields.hostname,
                port: fields.port,
                username: fields.username,
                transport: .ssh,
                hasPrivateKey: authentication.usesPrivateKey,
                hasPassword: authentication.usesPassword,
                tmuxInstalled: initialHost?.tmuxInstalled,
                lastLatencyMilliseconds: initialHost?.lastLatencyMilliseconds,
                lastCheckedAt: initialHost?.lastCheckedAt,
                operatingSystem: initialHost?.operatingSystem,
                packageManager: initialHost?.packageManager,
                lastMemAvailableKB: initialHost?.lastMemAvailableKB,
                lastMemTotalKB: initialHost?.lastMemTotalKB,
                lastDiskAvailableKB: initialHost?.lastDiskAvailableKB,
                lastDiskTotalKB: initialHost?.lastDiskTotalKB,
                lastStatsAt: initialHost?.lastStatsAt
            )
            try await repository.save(host)
            savedHost = host
            errorMessage = nil
            return true
        } catch {
            errorMessage = "Host could not be saved. \(error.localizedDescription)"
            return false
        }
    }

    private func updateSecrets(hostID: String) async throws {
        let passwordReference = SecretReference(hostID: hostID, kind: .password)
        if authentication.usesPassword {
            if !password.isEmpty {
                try await secretStore.store(Data(password.utf8), for: passwordReference)
            }
        } else {
            try await secretStore.delete(passwordReference)
        }

        let keyReference = SecretReference(hostID: hostID, kind: .privateKey)
        let passphraseReference = SecretReference(hostID: hostID, kind: .privateKeyPassphrase)
        if authentication.usesPrivateKey {
            if !privateKey.isEmpty {
                try await secretStore.store(Data(privateKey.utf8), for: keyReference)
            }
            if !passphrase.isEmpty {
                try await secretStore.store(Data(passphrase.utf8), for: passphraseReference)
            }
        } else {
            try await secretStore.delete(keyReference)
            try await secretStore.delete(passphraseReference)
        }
    }
}
