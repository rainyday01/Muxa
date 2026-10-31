import Foundation

enum RemoteOperatingSystem: String, Codable, Sendable {
    case linux
    case macOS
    case freeBSD
    case unknown
}

enum RemotePackageManager: String, Codable, Sendable {
    case apt
    case dnf
    case yum
    case pacman
    case apk
    case brew
    case unknown
}

struct RemoteEnvironmentSnapshot: Codable, Equatable, Sendable {
    let operatingSystem: RemoteOperatingSystem
    let packageManager: RemotePackageManager
    let tmuxVersion: String?
    let latencyMilliseconds: Int
    let checkedAt: Date
    /// Last-seen memory/disk remaining sampled during the probe, when reported.
    var resources: HostResources?

    init(
        operatingSystem: RemoteOperatingSystem,
        packageManager: RemotePackageManager,
        tmuxVersion: String?,
        latencyMilliseconds: Int,
        checkedAt: Date,
        resources: HostResources? = nil
    ) {
        self.operatingSystem = operatingSystem
        self.packageManager = packageManager
        self.tmuxVersion = tmuxVersion
        self.latencyMilliseconds = latencyMilliseconds
        self.checkedAt = checkedAt
        self.resources = resources
    }

    var tmuxInstalled: Bool {
        tmuxVersion != nil
    }

    var summary: String {
        let tmux = tmuxInstalled ? "tmux ready" : "tmux missing"
        return "\(tmux), \(latencyMilliseconds) ms"
    }
}
