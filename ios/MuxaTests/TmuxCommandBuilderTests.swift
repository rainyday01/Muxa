@testable import Muxa
import XCTest

final class TmuxCommandBuilderTests: XCTestCase {
    func testListSessionsWalksEveryTmuxServer() {
        let command = TmuxCommandBuilder().listSessions()

        XCTAssertTrue(command.contains("_mtmux_socks"))
        XCTAssertTrue(command.contains("tmux-$_uid"))
        XCTAssertTrue(command.contains("|| true"))
        XCTAssertTrue(command.contains("#{session_name}\t#{session_windows}"))

        let specific = TmuxCommandBuilder().listSessions(socketPath: "/tmp/tmux-1000/default")
        XCTAssertTrue(specific.hasPrefix("'tmux' '-S'"))
        XCTAssertFalse(specific.contains("_mtmux_socks"))
    }

    func testQuotesSocketSessionAndCommandArguments() {
        let builder = TmuxCommandBuilder()
        let target = TmuxAttachTarget(
            hostID: "host",
            socketPath: "/tmp/tmux;touch /tmp/pwned",
            sessionName: "work; echo unsafe"
        )

        let command = builder.newSession(
            socketPath: target.socketPath,
            sessionName: target.sessionName,
            workingDirectory: "/Users/a'b",
            initialCommand: "printf '%s' unsafe"
        )

        XCTAssertEqual(
            command,
            "'tmux' '-S' '/tmp/tmux;touch /tmp/pwned' 'new-session' '-d' '-s' " +
                "'work; echo unsafe' '-c' '/Users/a'\\''b' 'printf '\\''%s'\\'' unsafe'"
        )
    }

    func testNewSessionWithoutSocketPinsTheSharedServer() {
        let command = TmuxCommandBuilder().newSession(
            socketPath: nil,
            sessionName: "work",
            workingDirectory: nil,
            initialCommand: nil
        )
        XCTAssertTrue(command.contains("_mtmux_canonical_base"))
        XCTAssertTrue(command.contains("unset TMPDIR"))
        XCTAssertTrue(command.contains("MUXA_SOCK"))
        XCTAssertEqual(
            TmuxCommandBuilder.socketPrinted(in: "ok\nMUXA_SOCK /tmp/tmux-501/default\n"),
            "/tmp/tmux-501/default"
        )
    }

    func testBuildsCaptureAndAttachCommandsWithoutInterpolatingRawTarget() {
        let builder = TmuxCommandBuilder()
        let target = TmuxAttachTarget(
            hostID: "host",
            socketPath: "/tmp/tmux/default",
            sessionName: "claude"
        )

        XCTAssertEqual(
            builder.capturePane(target: target, lineLimit: 200),
            "'tmux' '-S' '/tmp/tmux/default' 'capture-pane' '-p' '-J' '-t' 'claude'"
        )
        XCTAssertEqual(
            builder.attach(target: target),
            "'tmux' '-S' '/tmp/tmux/default' 'attach-session' '-t' 'claude'"
        )
    }

    func testBuildsSafeInlinePanePagingCommands() {
        let builder = TmuxCommandBuilder()
        let target = TmuxAttachTarget(
            hostID: "host",
            socketPath: "/tmp/tmux/default",
            sessionName: "grok; echo unsafe"
        )

        XCTAssertEqual(
            builder.paneScrollMetadata(target: target),
            "'tmux' '-S' '/tmp/tmux/default' 'display-message' '-p' '-t' " +
                "'grok; echo unsafe' '#{alternate_on} #{history_size} #{pane_height}'"
        )
        XCTAssertEqual(
            builder.cancelCopyMode(target: target),
            "'tmux' '-S' '/tmp/tmux/default' 'send-keys' '-t' 'grok; echo unsafe' '-X' 'cancel'"
        )
        XCTAssertTrue(builder.pageHistory(target: target, scrollUp: true, lines: 18).contains("'scroll-up'"))
        XCTAssertTrue(builder.pageHistory(target: target, scrollUp: false, lines: 18).contains("'scroll-down'"))
    }

    func testTemplateComposesDirectoryAgentsAndApprovalFlags() {
        let template = TmuxSessionTemplate(
            kind: .agy,
            directory: "/tmp/project's copy",
            directoryMode: .createAndEnter,
            skipDangerousPermissions: true
        )

        XCTAssertEqual(
            template.initialCommand,
            "mkdir -p '/tmp/project'\"'\"'s copy' && " +
                "cd '/tmp/project'\"'\"'s copy' && agy --dangerously-skip-permissions"
        )
    }

    func testTemplatePreservesTildeExpansionForSafeDirectoryPaths() {
        let template = TmuxSessionTemplate(
            kind: .codex,
            directory: "~/projects/mobile",
            directoryMode: .enterExisting,
            yolo: true
        )

        XCTAssertEqual(template.initialCommand, "cd ~/projects/mobile && codex --yolo")
    }

    func testTemplateDecodesLegacyPayloadWithNewDefaults() throws {
        let data = Data("{\"kind\":\"grok\",\"customCommand\":null}".utf8)
        let template = try JSONDecoder().decode(TmuxSessionTemplate.self, from: data)

        XCTAssertEqual(template.kind, .grok)
        XCTAssertNil(template.directory)
        XCTAssertEqual(template.initialCommand, "grok")
    }
}
