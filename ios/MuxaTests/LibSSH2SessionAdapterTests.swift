import Foundation
@testable import Muxa
import XCTest

private final class RecordingSSHDriver: LibSSH2SessionDriving, @unchecked Sendable {
    enum Call: Equatable {
        case connect(SSHConnectionRequest)
        case execute(String)
        case openPTY(SSHPTYRequest)
        case resize(Int32, Int32, Int32, Int32)
        case read(Int)
        case write(Data)
        case close
    }

    var calls = [Call]()
    var reads = [Data?]()

    func connect(_ request: SSHConnectionRequest) throws {
        calls.append(.connect(request))
    }

    func execute(_ command: String) throws -> SSHExecResult {
        calls.append(.execute(command))
        return SSHExecResult(stdout: Data("ok\n".utf8), stderr: Data(), exitStatus: 0)
    }

    func openPTY(_ request: SSHPTYRequest) throws {
        calls.append(.openPTY(request))
    }

    func resizePTY(columns: Int32, rows: Int32, pixelWidth: Int32, pixelHeight: Int32) throws {
        calls.append(.resize(columns, rows, pixelWidth, pixelHeight))
    }

    func readPTY(maxBytes: Int) throws -> Data? {
        calls.append(.read(maxBytes))
        return reads.isEmpty ? Data() : reads.removeFirst()
    }

    func writePTY(_ data: Data) throws {
        calls.append(.write(data))
    }

    func close() {
        calls.append(.close)
    }
}

final class LibSSH2SessionAdapterTests: XCTestCase {
    func testConnectionExecPTYResizeAndIdempotentCloseSequence() async throws {
        let driver = RecordingSSHDriver()
        let adapter = LibSSH2SessionAdapter(driver: driver)
        let connection = SSHConnectionRequest(hostname: "fixture.invalid", port: 22, username: "muxa")
        let pty = SSHPTYRequest(
            terminal: "xterm-256color",
            columns: 80,
            rows: 24,
            pixelWidth: 800,
            pixelHeight: 480,
            command: "tmux attach"
        )

        try await adapter.connect(connection)
        let result = try await adapter.execute("printf ok")
        try await adapter.openPTY(pty)
        try await adapter.resizePTY(columns: 120, rows: 40, pixelWidth: 1200, pixelHeight: 800)
        await adapter.close()
        await adapter.close()

        let finalState = await adapter.state
        XCTAssertEqual(result.stdout, Data("ok\n".utf8))
        XCTAssertEqual(finalState, .closed)
        XCTAssertEqual(
            driver.calls,
            [
                .connect(connection),
                .execute("printf ok"),
                .openPTY(pty),
                .resize(120, 40, 1200, 800),
                .close
            ]
        )
    }

    func testRejectsExecBeforeConnectAndInvalidPTYSize() async throws {
        let driver = RecordingSSHDriver()
        let adapter = LibSSH2SessionAdapter(driver: driver)

        do {
            _ = try await adapter.execute("true")
            XCTFail("Expected state validation to fail")
        } catch {
            XCTAssertEqual(
                error as? SSHAdapterError,
                .invalidState(expected: "ready or ptyOpen", actual: .idle)
            )
        }

        try await adapter.connect(SSHConnectionRequest(hostname: "fixture.invalid", port: 22, username: "muxa"))
        do {
            try await adapter.openPTY(
                SSHPTYRequest(
                    terminal: "xterm-256color",
                    columns: 0,
                    rows: 24,
                    pixelWidth: 0,
                    pixelHeight: 0,
                    command: nil
                )
            )
            XCTFail("Expected terminal size validation to fail")
        } catch {
            XCTAssertEqual(error as? SSHAdapterError, .invalidTerminalSize)
        }
    }

    func testExecRemainsAvailableWhileInteractivePTYIsOpen() async throws {
        let driver = RecordingSSHDriver()
        let adapter = LibSSH2SessionAdapter(driver: driver)
        try await adapter.connect(
            SSHConnectionRequest(hostname: "fixture.invalid", port: 22, username: "muxa")
        )
        try await adapter.openPTY(
            SSHPTYRequest(
                terminal: "xterm-256color",
                columns: 80,
                rows: 24,
                pixelWidth: 800,
                pixelHeight: 480,
                command: "tmux attach"
            )
        )

        let result = try await adapter.execute("tmux list-sessions")

        XCTAssertEqual(result.exitStatus, 0)
        XCTAssertEqual(driver.calls.filter {
            if case .execute = $0 {
                true
            } else {
                false
            }
        }.count, 1)
        let state = await adapter.state
        XCTAssertEqual(state, .ptyOpen)
    }

    func testInteractivePTYReadsWritesAndPropagatesEOF() async throws {
        let driver = RecordingSSHDriver()
        driver.reads = [Data("remote output".utf8), nil]
        let adapter = LibSSH2SessionAdapter(driver: driver)
        try await adapter.connect(
            SSHConnectionRequest(hostname: "fixture.invalid", port: 22, username: "muxa")
        )
        try await adapter.openPTY(
            SSHPTYRequest(
                terminal: "xterm-256color",
                columns: 80,
                rows: 24,
                pixelWidth: 800,
                pixelHeight: 480,
                command: "tmux attach"
            )
        )

        let firstRead = try await adapter.readPTY(maxBytes: 1024)
        try await adapter.writePTY(Data("input\r".utf8))
        let eof = try await adapter.readPTY(maxBytes: 1024)

        XCTAssertEqual(firstRead, Data("remote output".utf8))
        XCTAssertNil(eof)
        XCTAssertEqual(
            driver.calls.suffix(3),
            [.read(1024), .write(Data("input\r".utf8)), .read(1024)]
        )
    }
}
