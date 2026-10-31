import Foundation
import OSLog

actor DiagnosticsLogger: DiagnosticLogging {
    private let subsystem: String
    private let capacity: Int
    private var storedEntries = [DiagnosticEntry]()

    init(subsystem: String = "app.mtmux.ios", capacity: Int = 500) {
        self.subsystem = subsystem
        self.capacity = max(1, capacity)
    }

    func log(
        level: DiagnosticLevel,
        category: String,
        message: String,
        metadata: [String: String] = [:]
    ) {
        let sanitizedMessage = DiagnosticRedactor.redact(message: message)
        let sanitizedMetadata = DiagnosticRedactor.redact(metadata: metadata)
        let entry = DiagnosticEntry(
            id: UUID(),
            timestamp: Date(),
            level: level,
            category: category,
            message: sanitizedMessage,
            metadata: sanitizedMetadata
        )
        storedEntries.append(entry)
        if storedEntries.count > capacity {
            storedEntries.removeFirst(storedEntries.count - capacity)
        }
        let logger = Logger(subsystem: subsystem, category: category)
        logger.log(level: level.osLogType, "\(sanitizedMessage, privacy: .public)")
    }

    func entries() -> [DiagnosticEntry] {
        storedEntries
    }

    func export() throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(storedEntries)
    }

    func clear() {
        storedEntries.removeAll(keepingCapacity: true)
    }
}

private extension DiagnosticLevel {
    var osLogType: OSLogType {
        switch self {
        case .debug: .debug
        case .info: .info
        case .notice: .default
        case .error: .error
        }
    }
}
