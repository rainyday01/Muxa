import Foundation

/// Last-sampled host memory / root-disk remaining (from the most recent SSH).
/// Values are KiB as reported by /proc/meminfo and `df -Pk /`.
struct HostResources: Codable, Equatable, Sendable {
    var memAvailableKB: Int?
    var memTotalKB: Int?
    var diskAvailableKB: Int?
    var diskTotalKB: Int?

    init(
        memAvailableKB: Int? = nil,
        memTotalKB: Int? = nil,
        diskAvailableKB: Int? = nil,
        diskTotalKB: Int? = nil
    ) {
        self.memAvailableKB = memAvailableKB
        self.memTotalKB = memTotalKB
        self.diskAvailableKB = diskAvailableKB
        self.diskTotalKB = diskTotalKB
    }

    var isEmpty: Bool {
        memAvailableKB == nil && diskAvailableKB == nil
    }
}

enum HostResourceText {
    /// Parses KEY=VALUE probe output; tolerates both bare and `muxa_`-prefixed keys.
    static func parseKV(_ raw: String) -> HostResources {
        var values = [String: String]()
        for line in raw.split(whereSeparator: \.isNewline) {
            guard let separator = line.firstIndex(of: "="), separator != line.startIndex else {
                continue
            }
            let key = line[..<separator].trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: "muxa_", with: "")
            values[key.lowercased()] = line[line.index(after: separator)...]
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return HostResources(
            memAvailableKB: values["mem_avail_kb"].flatMap(Int.init),
            memTotalKB: values["mem_total_kb"].flatMap(Int.init),
            diskAvailableKB: values["disk_avail_kb"].flatMap(Int.init),
            diskTotalKB: values["disk_total_kb"].flatMap(Int.init)
        )
    }

    /// Compact remaining size: 48G / 1.2G / 512M / 80K
    static func formatKB(_ kib: Int) -> String {
        guard kib >= 0 else { return "—" }
        let gib = Double(kib) / 1_048_576.0
        let mib = Double(kib) / 1024.0
        if gib >= 10 {
            return "\(Int(gib))G"
        }
        if gib >= 1.0 {
            let rounded = String(format: "%.1f", gib)
            let trimmed = rounded.hasSuffix(".0") ? String(rounded.dropLast(2)) : rounded
            return "\(trimmed)G"
        }
        if mib >= 1 {
            return "\(Int(mib))M"
        }
        return "\(kib)K"
    }

    /// Low memory: under 256 MiB remaining, or less than 10% of total.
    static func isMemoryLow(availableKB: Int?, totalKB: Int?) -> Bool {
        guard let availableKB, availableKB >= 0 else { return false }
        if availableKB < 256 * 1024 {
            return true
        }
        if let totalKB, totalKB > 0, availableKB * 10 < totalKB {
            return true
        }
        return false
    }

    /// Low disk: under 2 GiB remaining on `/`, or less than 10% of total.
    static func isDiskLow(availableKB: Int?, totalKB: Int?) -> Bool {
        guard let availableKB, availableKB >= 0 else { return false }
        if availableKB < 2 * 1_048_576 {
            return true
        }
        if let totalKB, totalKB > 0, availableKB * 10 < totalKB {
            return true
        }
        return false
    }
}

extension Host {
    /// Last-seen memory/disk sample carried on the host record.
    var lastResources: HostResources {
        HostResources(
            memAvailableKB: lastMemAvailableKB,
            memTotalKB: lastMemTotalKB,
            diskAvailableKB: lastDiskAvailableKB,
            diskTotalKB: lastDiskTotalKB
        )
    }

    /// Patches only the sampled mem/disk fields; absent samples keep old values.
    func applying(resources: HostResources?) -> Host {
        guard let resources, !resources.isEmpty else { return self }
        var updated = self
        if let value = resources.memAvailableKB {
            updated.lastMemAvailableKB = value
        }
        if let value = resources.memTotalKB {
            updated.lastMemTotalKB = value
        }
        if let value = resources.diskAvailableKB {
            updated.lastDiskAvailableKB = value
        }
        if let value = resources.diskTotalKB {
            updated.lastDiskTotalKB = value
        }
        updated.lastStatsAt = Date()
        return updated
    }
}
