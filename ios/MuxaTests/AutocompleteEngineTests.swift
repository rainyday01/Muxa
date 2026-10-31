@testable import Muxa
import XCTest

final class AutocompleteEngineTests: XCTestCase {
    func testSuggestsSnippetsHistoryBuiltinsAndPathDeltas() {
        let snippet = Snippet(id: "s1", name: "Status", body: "git status {{host}}", runOnSelect: true)
        let history = CommandHistoryEntry(id: 1, hostID: "h", command: "git push", usedAt: Date())
        let suggestions = AutocompleteEngine.suggest(
            AutocompleteEngine.Query(
                linePrefix: "git",
                tuiMode: false,
                snippets: [snippet],
                history: [history],
                pathHints: ["/tmp/git-repo"],
                vars: ["host": "lab"]
            )
        )

        XCTAssertTrue(suggestions.contains(where: { $0.source == .snippet && $0.insertion == "git status lab" }))
        XCTAssertTrue(suggestions.contains(where: { $0.source == .history && $0.insertion == "git push" }))
        XCTAssertTrue(suggestions.contains(where: { $0.source == .builtIn && $0.insertion.hasPrefix("git") }))
    }

    func testPausesInAlternateScreenAndSkipsSecrets() {
        XCTAssertTrue(
            AutocompleteEngine.isTuiMode(alternateScreen: true, snapshot: "", lineBuffer: "ls")
        )
        XCTAssertTrue(
            AutocompleteEngine.isTuiMode(
                alternateScreen: false,
                snapshot: "htop\nCPU\nMEM",
                lineBuffer: ""
            )
        )
        XCTAssertFalse(AutocompleteEngine.shouldRecordHistory("export PASSWORD=secret"))
        XCTAssertTrue(AutocompleteEngine.shouldRecordHistory("ls -la"))
    }

    func testBuildsQuotedCompgenAndPathInsertionDelta() {
        let command = AutocompleteEngine.remoteCompgenCommand(linePrefix: "cd /tmp/fo")
        XCTAssertTrue(command?.contains("compgen -d") == true)
        XCTAssertTrue(command?.contains("/tmp/fo") == true)
        XCTAssertEqual(AutocompleteEngine.pathCompletionInsertion(token: "/tmp/fo", completed: "/tmp/foo"), "o")
        XCTAssertEqual(
            AutocompleteEngine.parseCompgenOutput("/tmp/foo\n/tmp/foo\n\n/tmp/bar\n"),
            ["/tmp/foo", "/tmp/bar"]
        )
    }
}
