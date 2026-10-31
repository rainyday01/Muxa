import Foundation

enum SSHSessionState: Equatable, Sendable {
    case idle
    case connecting
    case ready
    case ptyOpen
    case closed
}

struct SSHConnectionRequest: Equatable, Sendable {
    let hostname: String
    let port: UInt16
    let username: String
}

struct SSHExecResult: Equatable, Sendable {
    let stdout: Data
    let stderr: Data
    let exitStatus: Int32
}

struct SSHPTYRequest: Equatable, Sendable {
    let terminal: String
    let columns: Int32
    let rows: Int32
    let pixelWidth: Int32
    let pixelHeight: Int32
    let command: String?
}

enum SSHAdapterError: Error, Equatable {
    case invalidState(expected: String, actual: SSHSessionState)
    case invalidTerminalSize
    case invalidPTYReadSize
}

protocol LibSSH2SessionDriving: Sendable {
    func connect(_ request: SSHConnectionRequest) throws
    func execute(_ command: String) throws -> SSHExecResult
    func openPTY(_ request: SSHPTYRequest) throws
    func resizePTY(columns: Int32, rows: Int32, pixelWidth: Int32, pixelHeight: Int32) throws
    func readPTY(maxBytes: Int) throws -> Data?
    func writePTY(_ data: Data) throws
    func close()
}

actor LibSSH2SessionAdapter {
    private let driver: any LibSSH2SessionDriving
    private(set) var state = SSHSessionState.idle

    init(driver: any LibSSH2SessionDriving) {
        self.driver = driver
    }

    func connect(_ request: SSHConnectionRequest) throws {
        guard state == .idle else {
            throw SSHAdapterError.invalidState(expected: "idle", actual: state)
        }
        state = .connecting
        do {
            try driver.connect(request)
            state = .ready
        } catch {
            state = .idle
            throw error
        }
    }

    func execute(_ command: String) throws -> SSHExecResult {
        guard state == .ready || state == .ptyOpen else {
            throw SSHAdapterError.invalidState(expected: "ready or ptyOpen", actual: state)
        }
        return try driver.execute(command)
    }

    func openPTY(_ request: SSHPTYRequest) throws {
        guard state == .ready else {
            throw SSHAdapterError.invalidState(expected: "ready", actual: state)
        }
        try validateSize(
            columns: request.columns,
            rows: request.rows,
            pixelWidth: request.pixelWidth,
            pixelHeight: request.pixelHeight
        )
        try driver.openPTY(request)
        state = .ptyOpen
    }

    func resizePTY(columns: Int32, rows: Int32, pixelWidth: Int32, pixelHeight: Int32) throws {
        guard state == .ptyOpen else {
            throw SSHAdapterError.invalidState(expected: "ptyOpen", actual: state)
        }
        try validateSize(columns: columns, rows: rows, pixelWidth: pixelWidth, pixelHeight: pixelHeight)
        try driver.resizePTY(columns: columns, rows: rows, pixelWidth: pixelWidth, pixelHeight: pixelHeight)
    }

    func readPTY(maxBytes: Int) throws -> Data? {
        guard state == .ptyOpen else {
            throw SSHAdapterError.invalidState(expected: "ptyOpen", actual: state)
        }
        guard maxBytes > 0 else { throw SSHAdapterError.invalidPTYReadSize }
        return try driver.readPTY(maxBytes: maxBytes)
    }

    func writePTY(_ data: Data) throws {
        guard state == .ptyOpen else {
            throw SSHAdapterError.invalidState(expected: "ptyOpen", actual: state)
        }
        try driver.writePTY(data)
    }

    func close() {
        guard state != .closed else { return }
        driver.close()
        state = .closed
    }

    private func validateSize(columns: Int32, rows: Int32, pixelWidth: Int32, pixelHeight: Int32) throws {
        guard columns > 0, rows > 0, pixelWidth >= 0, pixelHeight >= 0 else {
            throw SSHAdapterError.invalidTerminalSize
        }
    }
}
