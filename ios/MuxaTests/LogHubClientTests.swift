@testable import Muxa
import XCTest

final class LogHubClientTests: XCTestCase {
    func testArchiveRedactsSecretsAndNamesThePackageFiles() {
        let archive = LogHubClient.archive(
            LogHubClient.Payload(
                logText: "password=hunter2\nready",
                note: "could not list sessions",
                appVersion: "0.1.1 (4)",
                deviceID: "muxa-ios-abc",
                osVersion: "18.5",
                model: "iPhone"
            )
        )
        let text = String(decoding: archive, as: UTF8.self)

        XCTAssertTrue(text.contains("app.log"))
        XCTAssertTrue(text.contains("device.json"))
        XCTAssertTrue(text.contains("note.txt"))
        XCTAssertTrue(text.contains("password=<redacted>"))
        XCTAssertTrue(text.contains("could not list sessions"))
        XCTAssertTrue(text.contains("muxa-ios-abc"))
        XCTAssertTrue(text.contains("\"os\""))
        XCTAssertTrue(text.contains("ios"))
        XCTAssertFalse(text.contains("hunter2"))
    }

    func testCooldownCountsDownAndIgnoresClockJumps() {
        XCTAssertEqual(LogHubClient.cooldownSeconds(lastUpload: 1000, now: 1000), 60)
        XCTAssertEqual(LogHubClient.cooldownSeconds(lastUpload: 1000, now: 1059.5), 1)
        XCTAssertEqual(LogHubClient.cooldownSeconds(lastUpload: 1000, now: 1060), 0)
        XCTAssertEqual(LogHubClient.cooldownSeconds(lastUpload: 0, now: 1000), 0)
        XCTAssertEqual(LogHubClient.cooldownSeconds(lastUpload: 2000, now: 1000), 0)
    }

    func testOversizedPackageKeepsNewestLog() {
        let archive = LogHubClient.archiveWithinLimit(
            LogHubClient.Payload(
                logText: String(repeating: "x", count: 100_000) + "NEWEST",
                note: "",
                appVersion: "1",
                deviceID: "d",
                osVersion: "18.5",
                model: "iPhone"
            ),
            maximumBytes: 20_000
        )

        XCTAssertLessThanOrEqual(archive.count, 20_000)
        XCTAssertTrue(String(decoding: archive, as: UTF8.self).contains("NEWEST"))
    }
}