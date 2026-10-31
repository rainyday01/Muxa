@testable import Muxa
import XCTest

final class DiagnosticsLoggerTests: XCTestCase {
    func testRedactsMessageAndSensitiveMetadata() {
        let token = ["gh", "p_", String(repeating: "1", count: 24)].joined()
        let message = "password=hunter2 token:\(token) https://user:pass@example.invalid"
        let metadata = [
            "privateKey": "private-key-value",
            "host": "example.invalid",
            "detail": "passphrase=fixture-secret"
        ]

        let redactedMessage = DiagnosticRedactor.redact(message: message)
        let redactedMetadata = DiagnosticRedactor.redact(metadata: metadata)

        XCTAssertFalse(redactedMessage.contains("hunter2"))
        XCTAssertFalse(redactedMessage.contains("ghp_"))
        XCTAssertFalse(redactedMessage.contains(":pass@"))
        XCTAssertEqual(redactedMetadata["privateKey"], "<redacted>")
        XCTAssertEqual(redactedMetadata["host"], "example.invalid")
        XCTAssertFalse(try XCTUnwrap(redactedMetadata["detail"]).contains("fixture-secret"))
    }

    func testRedactsLogHubTokens() {
        let token = ["lh", "up", "exampletoken"].joined(separator: "_")
        let redacted = DiagnosticRedactor.redact(message: "auth \(token)")

        XCTAssertFalse(redacted.contains("exampletoken"))
        XCTAssertTrue(redacted.contains("<redacted-token>"))
    }

    func testRingCapacityExportAndClear() async throws {
        let logger = DiagnosticsLogger(subsystem: "app.mtmux.tests", capacity: 2)
        await logger.log(level: .info, category: "test", message: "one")
        await logger.log(level: .notice, category: "test", message: "two")
        await logger.log(level: .error, category: "test", message: "three", metadata: ["password": "hidden"])

        let entries = await logger.entries()
        let exported = try await logger.export()
        let decoded = try JSONDecoder.iso8601.decode([DiagnosticEntry].self, from: exported)
        XCTAssertEqual(entries.map(\.message), ["two", "three"])
        XCTAssertEqual(decoded.map(\.message), ["two", "three"])
        XCTAssertEqual(decoded.last?.metadata["password"], "<redacted>")

        await logger.clear()
        let cleared = await logger.entries()
        XCTAssertEqual(cleared, [])
    }
}

private extension JSONDecoder {
    static var iso8601: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
