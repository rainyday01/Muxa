@testable import Muxa
import XCTest

final class TmuxOutputGoldenTests: XCTestCase {
    func testSessionsGoldenFixture() throws {
        let parser = TmuxOutputParser()
        let sessions = try parser.sessions(
            fixture("tmux-sessions.golden"),
            hostID: "golden-host",
            hostAlias: "Golden",
            socketPath: "/tmp/tmux/default"
        )

        XCTAssertEqual(sessions.map(\.name), ["work", "idle"])
        XCTAssertEqual(sessions.map(\.windowCount), [2, 1])
        XCTAssertEqual(sessions.map(\.attached), [true, false])
        XCTAssertEqual(sessions.map(\.hostAlias), ["Golden", "Golden"])
    }

    func testWindowsAndPanesGoldenFixtures() throws {
        let parser = TmuxOutputParser()
        let target = TmuxAttachTarget(
            hostID: "golden-host",
            socketPath: "/tmp/tmux/default",
            sessionName: "work"
        )
        let windows = try parser.windows(fixture("tmux-windows.golden"), target: target)
        let panes = try parser.panes(
            fixture("tmux-panes.golden"),
            target: target,
            windowIndex: 0
        )

        XCTAssertEqual(windows.map(\.name), ["main", "logs"])
        XCTAssertEqual(windows.map(\.paneCount), [2, 1])
        XCTAssertEqual(panes.map(\.currentCommand), ["vim", "zsh"])
        XCTAssertEqual(panes.map(\.processID), [1234, 2345])
    }

    private func fixture(_ name: String) throws -> String {
        guard let url = Bundle(for: Self.self).url(forResource: name, withExtension: "tsv") else {
            throw FixtureError.missing(name)
        }
        return try String(contentsOf: url, encoding: .utf8)
    }
}

private enum FixtureError: Error {
    case missing(String)
}
