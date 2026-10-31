@testable import Muxa
import XCTest

final class SnapshotTextTests: XCTestCase {
    func testCardKeepsTheVisibleTailAndDropsBoxChrome() {
        let raw = """
        ╭────────────────────────╮
        │                        │
        old prompt that should drop
        user asked about muxa
        grok is answering now
        """

        let card = SnapshotText.forCard(raw)

        XCTAssertEqual(card, "user asked about muxa\ngrok is answering now")
        XCTAssertTrue(SnapshotText.isUseful(card))
    }

    func testCardKeepsOnlyTheLastTwoMeaningfulLines() {
        let lines = (1 ... 40).map { "line \($0) of terminal output" }
        let card = SnapshotText.forCard(lines.joined(separator: "\n"))

        XCTAssertEqual(card, "line 39 of terminal output\nline 40 of terminal output")
    }

    func testStoreTrimsToTailBudget() {
        let lines = (1 ... 120).map { "line \($0) with enough text to keep" }
        let stored = SnapshotText.forStore(lines.joined(separator: "\n"))

        XCTAssertNotNil(stored)
        XCTAssertTrue(stored?.contains("line 120") == true)
        XCTAssertFalse(stored?.contains("line 1 ") == true)
    }
}
