@testable import Muxa
import XCTest

final class TmuxOutputParserTests: XCTestCase {
    func testParsesSessionsWindowsAndPanes() {
        let parser = TmuxOutputParser()
        let target = TmuxAttachTarget(hostID: "host", socketPath: "/tmp/tmux/default", sessionName: "work")
        let sessions = parser.sessions(
            "work\t2\t1\t1700000000\t1700000010\n" +
                "idle\t1\t0\t0\t0\n",
            hostID: "host",
            hostAlias: "Lab",
            socketPath: target.socketPath
        )
        let windows = parser.windows(
            "0\tmain\t1\tbb08,80x24,0,0,0\t2\n",
            target: target
        )
        let panes = parser.panes(
            "0\t1\tEditor\tvim\t/Users/dev\t80\t24\t1234\n",
            target: target,
            windowIndex: 0
        )

        XCTAssertEqual(sessions.count, 2)
        XCTAssertEqual(sessions[0].name, "work")
        XCTAssertEqual(sessions[0].windowCount, 2)
        XCTAssertTrue(sessions[0].attached)
        XCTAssertEqual(sessions[0].hostAlias, "Lab")
        XCTAssertEqual(windows, [
            TmuxWindow(
                sessionName: "work",
                index: 0,
                name: "main",
                active: true,
                layout: "bb08,80x24,0,0,0",
                paneCount: 2,
                panes: []
            )
        ])
        XCTAssertEqual(panes, [
            TmuxPane(
                sessionName: "work",
                windowIndex: 0,
                index: 0,
                active: true,
                title: "Editor",
                currentCommand: "vim",
                currentPath: "/Users/dev",
                width: 80,
                height: 24,
                processID: 1234
            )
        ])
    }

    func testReadsSocketPathFromTheSessionRow() {
        let sessions = TmuxOutputParser().sessions(
            "work\t2\t1\t1700000000\t1700000010\t/tmp/tmux-1000/default\n",
            hostID: "host",
            socketPath: nil
        )

        XCTAssertEqual(sessions.first?.socketPath, "/tmp/tmux-1000/default")
    }

    func testSkipsMalformedRowsAndPreservesEmptyFields() {
        let parser = TmuxOutputParser()
        let target = TmuxAttachTarget(hostID: "host", socketPath: nil, sessionName: "work")

        let sessions = parser.sessions(
            "broken\n\t1\t1\t1\t1\nvalid\tbad\t*\tbad\tbad\n",
            hostID: "host"
        )
        let panes = parser.panes(
            "bad\t1\tTitle\tcmd\t/path\t80\t24\tpid\n" +
                "0\t0\t\t\t\t80\t24\t0\n",
            target: target,
            windowIndex: 0
        )

        XCTAssertEqual(sessions.map(\.name), ["valid"])
        XCTAssertTrue(sessions[0].attached)
        XCTAssertNil(sessions[0].createdAt)
        XCTAssertEqual(panes[0].title, nil)
        XCTAssertNil(panes[0].currentCommand)
        XCTAssertNil(panes[0].currentPath)
        XCTAssertNil(panes[0].processID)
    }
}
