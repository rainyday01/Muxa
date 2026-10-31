import Foundation

protocol RemoteEnvironmentProbing: Sendable {
    func probe(host: Host) async throws -> RemoteEnvironmentSnapshot
}

actor SSHRemoteEnvironmentProbe: RemoteEnvironmentProbing {
    private let connectionManager: ConnectionManager
    private let now: @Sendable () -> Date

    init(
        connectionManager: ConnectionManager,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.connectionManager = connectionManager
        self.now = now
    }

    func probe(host: Host) async throws -> RemoteEnvironmentSnapshot {
        let startedAt = now()
        let output = try await connectionManager.execute(
            SSHCommandRequest(command: Self.command, timeout: .seconds(10)),
            on: host
        )
        let finishedAt = now()
        guard output.exitStatus == 0 else {
            let message = String(data: output.stderr, encoding: .utf8) ?? "remote probe failed"
            throw SSHControlPlaneError.remoteFailure(status: output.exitStatus, message: message)
        }
        var snapshot = try Self.parse(output.stdout, checkedAt: finishedAt)
        snapshot = RemoteEnvironmentSnapshot(
            operatingSystem: snapshot.operatingSystem,
            packageManager: snapshot.packageManager,
            tmuxVersion: snapshot.tmuxVersion,
            latencyMilliseconds: max(0, Int(finishedAt.timeIntervalSince(startedAt) * 1000)),
            checkedAt: finishedAt,
            resources: snapshot.resources
        )
        return snapshot
    }

    static let command = """
    printf 'muxa_os=%s\\n' "$(uname -s 2>/dev/null || printf unknown)"
    printf 'muxa_tmux=%s\\n' "$(tmux -V 2>/dev/null || true)"
    if command -v apt-get >/dev/null 2>&1; then printf 'muxa_pm=apt\\n'; \
    elif command -v dnf >/dev/null 2>&1; then printf 'muxa_pm=dnf\\n'; \
    elif command -v yum >/dev/null 2>&1; then printf 'muxa_pm=yum\\n'; \
    elif command -v pacman >/dev/null 2>&1; then printf 'muxa_pm=pacman\\n'; \
    elif command -v apk >/dev/null 2>&1; then printf 'muxa_pm=apk\\n'; \
    elif command -v brew >/dev/null 2>&1; then printf 'muxa_pm=brew\\n'; \
    else printf 'muxa_pm=unknown\\n'; fi
    \(hostResourceCommand)
    """

    /// MemAvailable + `df -Pk /` — portable on Linux VPS (busybox df ok).
    static let hostResourceCommand = """
    if [ -r /proc/meminfo ]; then
      MEM_AVAIL="$(awk '/MemAvailable:/ {print $2}' /proc/meminfo 2>/dev/null)"
      if [ -z "$MEM_AVAIL" ]; then
        MEM_AVAIL="$(awk '/MemFree:/ {f=$2} /Buffers:/ {b=$2} /^Cached:/ {c=$2} END {print f+b+c}' /proc/meminfo)"
      fi
      printf 'muxa_mem_avail_kb=%s\\n' "$MEM_AVAIL"
      printf 'muxa_mem_total_kb=%s\\n' "$(awk '/MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null)"
    fi
    df -Pk / 2>/dev/null | awk 'NR==2 {printf "muxa_disk_avail_kb=%s\\nmuxa_disk_total_kb=%s\\n", $4, $2}'
    """

    static func parse(_ data: Data, checkedAt: Date) throws -> RemoteEnvironmentSnapshot {
        guard let output = String(data: data, encoding: .utf8) else {
            throw SSHControlPlaneError.rejected("Remote probe returned invalid UTF-8.")
        }
        var values = [String: String]()
        for line in output.split(whereSeparator: \.isNewline) {
            let pieces = line.split(separator: "=", maxSplits: 1).map(String.init)
            guard pieces.count == 2 else { continue }
            values[pieces[0]] = pieces[1].trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return RemoteEnvironmentSnapshot(
            operatingSystem: operatingSystem(values["muxa_os"]),
            packageManager: packageManager(values["muxa_pm"]),
            tmuxVersion: nonEmpty(values["muxa_tmux"]),
            latencyMilliseconds: 0,
            checkedAt: checkedAt,
            resources: HostResourceText.parseKV(output)
        )
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private static func operatingSystem(_ value: String?) -> RemoteOperatingSystem {
        switch value?.lowercased() {
        case "linux": .linux
        case "darwin": .macOS
        case "freebsd": .freeBSD
        default: .unknown
        }
    }

    private static func packageManager(_ value: String?) -> RemotePackageManager {
        switch value {
        case "apt": .apt
        case "dnf": .dnf
        case "yum": .yum
        case "pacman": .pacman
        case "apk": .apk
        case "brew": .brew
        default: .unknown
        }
    }
}
