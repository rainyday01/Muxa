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
}