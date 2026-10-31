@testable import Muxa
import XCTest

final class TmuxSocketDiscoveryTests: XCTestCase {
    func testParsesAndDeduplicatesSocketPaths() {
        let discovery = TmuxSocketDiscovery()
        let sockets = discovery.parse(
            "/tmp/tmux-501/default\tTMPDIR\n" +
                "/run/user/501/tmux/default\tXDG_RUNTIME_DIR\n" +
                "/tmp/tmux-501/default\tduplicate\n"
        )

        XCTAssertEqual(
            sockets,
            [
                TmuxServerSocket(path: "/tmp/tmux-501/default", source: "TMPDIR"),
                TmuxServerSocket(path: "/run/user/501/tmux/default", source: "XDG_RUNTIME_DIR")
            ]
        )
    }

    func testDiscoveryCommandChecksAllRuntimeDirectories() {
        let command = TmuxSocketDiscovery(maximumDepth: 4).command

        XCTAssertTrue(command.contains("${TMUX_TMPDIR:-}"))
        XCTAssertTrue(command.contains("${TMPDIR:-}"))
        XCTAssertTrue(command.contains("${XDG_RUNTIME_DIR:-}"))
        XCTAssertTrue(command.contains("-maxdepth 4"))
        XCTAssertTrue(command.contains("awk"))
        XCTAssertTrue(command.contains("tmux-$_uid"))
        XCTAssertTrue(command.contains("-path \"*/tmux-$_uid/*\""))
        XCTAssertTrue(command.contains("/var/folders"))
        XCTAssertTrue(command.contains("_mtmux_canonical_base"))
        XCTAssertTrue(command.contains("/tmp"))
    }
}
