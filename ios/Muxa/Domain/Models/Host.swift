import Foundation

enum TransportPreference: String, Codable, CaseIterable, Sendable {
    case ssh = "SSH"

    init(from decoder: Decoder) throws {
        // Older exports may contain another transport value. Since SSH is the
        // only supported transport, preserve those hosts as SSH records.
        _ = try decoder.singleValueContainer().decode(String.self)
        self = .ssh
    }
}

struct Host: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var alias: String
    var hostname: String
    var port: UInt16
    var username: String
    var transport: TransportPreference
    var hasPrivateKey: Bool
    var hasPassword: Bool
    var tmuxInstalled: Bool?
    var lastLatencyMilliseconds: Int?
    var lastCheckedAt: Date?
    var operatingSystem: RemoteOperatingSystem?
    var packageManager: RemotePackageManager?
    /// Free memory (KiB) at the last successful SSH.
    var lastMemAvailableKB: Int?
    var lastMemTotalKB: Int?
    /// Free space on `/` (KiB) at the last successful SSH.
    var lastDiskAvailableKB: Int?
    var lastDiskTotalKB: Int?
    var lastStatsAt: Date?

    init(
        id: String = UUID().uuidString,
        alias: String,
        hostname: String,
        port: UInt16 = 22,
        username: String,
        transport: TransportPreference = .ssh,
        hasPrivateKey: Bool = false,
        hasPassword: Bool = false,
        tmuxInstalled: Bool? = nil,
        lastLatencyMilliseconds: Int? = nil,
        lastCheckedAt: Date? = nil,
        operatingSystem: RemoteOperatingSystem? = nil,
        packageManager: RemotePackageManager? = nil,
        lastMemAvailableKB: Int? = nil,
        lastMemTotalKB: Int? = nil,
        lastDiskAvailableKB: Int? = nil,
        lastDiskTotalKB: Int? = nil,
        lastStatsAt: Date? = nil
    ) {
        self.id = id
        self.alias = alias
        self.hostname = hostname
        self.port = port
        self.username = username
        self.transport = transport
        self.hasPrivateKey = hasPrivateKey
        self.hasPassword = hasPassword
        self.tmuxInstalled = tmuxInstalled
        self.lastLatencyMilliseconds = lastLatencyMilliseconds
        self.lastCheckedAt = lastCheckedAt
        self.operatingSystem = operatingSystem
        self.packageManager = packageManager
        self.lastMemAvailableKB = lastMemAvailableKB
        self.lastMemTotalKB = lastMemTotalKB
        self.lastDiskAvailableKB = lastDiskAvailableKB
        self.lastDiskTotalKB = lastDiskTotalKB
        self.lastStatsAt = lastStatsAt
    }
}
