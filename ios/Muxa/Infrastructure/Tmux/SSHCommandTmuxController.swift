import Foundation

enum TmuxControlError: Error, Equatable, Sendable {
    case hostNotFound
    case invalidOutput
    case unsupported
    case remoteFailure(status: Int32, message: String)

    var summary: String {
        switch self {
        case .hostNotFound:
            return "The host is no longer saved."
        case .invalidOutput:
            return "The remote tmux output could not be read."
        case .unsupported:
            return "This tmux action is not supported."
        case let .remoteFailure(status, message):
            let detail = message.trimmingCharacters(in: .whitespacesAndNewlines)
            if detail.isEmpty {
                return "Remote tmux command failed (\(status))."
            }
            let clipped = detail.count > 180 ? String(detail.prefix(180)) : detail
            return "Remote tmux command failed (\(status)): \(clipped)"
        }
    }
}

actor SSHCommandTmuxController: TmuxControlling {
    private let hostRepository: any HostRepository
    private let connectionManager: ConnectionManager
    private let commandBuilder: TmuxCommandBuilder
    private let socketDiscovery: TmuxSocketDiscovery
    private let outputParser: TmuxOutputParser
    private let diagnostics: (any DiagnosticLogging)?

    init(
        hostRepository: any HostRepository,
        connectionManager: ConnectionManager,
        commandBuilder: TmuxCommandBuilder = TmuxCommandBuilder(),
        socketDiscovery: TmuxSocketDiscovery = TmuxSocketDiscovery(),
        outputParser: TmuxOutputParser = TmuxOutputParser(),
        diagnostics: (any DiagnosticLogging)? = nil
    ) {
        self.hostRepository = hostRepository
        self.connectionManager = connectionManager
        self.commandBuilder = commandBuilder
        self.socketDiscovery = socketDiscovery
        self.outputParser = outputParser
        self.diagnostics = diagnostics
    }

    func discoverSockets(hostID: String) async throws -> [TmuxServerSocket] {
        let output = try await run(hostID: hostID, command: socketDiscovery.command)
        let sockets = socketDiscovery.parse(output)
        let paths = sockets.map(\.path).joined(separator: ", ")
        await note("Discovered \(sockets.count) tmux sockets\(paths.isEmpty ? "" : ": \(paths)")")
        return sockets
    }

    func listSessions(hostID: String, socketPath: String?) async throws -> [TmuxSession] {
        let output = try await run(
            hostID: hostID,
            command: commandBuilder.listSessions(socketPath: socketPath)
        )
        let hostAlias = try await hostRepository.fetch(id: hostID)?.alias
        let sessions = outputParser.sessions(
            output,
            hostID: hostID,
            hostAlias: hostAlias,
            socketPath: socketPath
        )
        let names = sessions.map(\.name).joined(separator: ", ")
        await note(
            "Listed \(sessions.count) sessions for \(hostAlias ?? hostID)" +
                (names.isEmpty ? "" : ": \(names)")
        )
        return sessions
    }

    func listWindows(for target: TmuxAttachTarget) async throws -> [TmuxWindow] {
        let output = try await run(
            hostID: target.hostID,
            command: commandBuilder.listWindows(target: target)
        )
        return outputParser.windows(output, target: target)
    }

    func listPanes(for target: TmuxAttachTarget, windowIndex: Int?) async throws -> [TmuxPane] {
        let output = try await run(
            hostID: target.hostID,
            command: commandBuilder.listPanes(target: target, windowIndex: windowIndex)
        )
        return outputParser.panes(
            output,
            target: target,
            windowIndex: windowIndex ?? 0
        )
    }

    func capturePane(for target: TmuxAttachTarget, lineLimit: Int) async throws -> String {
        try await run(
            hostID: target.hostID,
            command: commandBuilder.capturePane(target: target, lineLimit: lineLimit)
        )
    }

    /// Cheap mem/disk sample over SSH exec (after probe / attach).
    func probeResources(hostID: String) async throws -> HostResources {
        let output = try await run(
            hostID: hostID,
            command: SSHRemoteEnvironmentProbe.hostResourceCommand
        )
        return HostResourceText.parseKV(output)
    }

    func createSession(_ request: TmuxCreateRequest) async throws -> TmuxAttachTarget {
        let output = try await run(
            hostID: request.hostID,
            command: commandBuilder.newSession(
                socketPath: request.socketPath,
                sessionName: request.sessionName,
                workingDirectory: request.workingDirectory,
                initialCommand: request.initialCommand
            )
        )
        let socket = TmuxCommandBuilder.socketPrinted(in: output) ?? request.socketPath
        await note(
            "Created session \(request.sessionName) on \(socket ?? "default socket")"
        )
        return TmuxAttachTarget(
            hostID: request.hostID,
            socketPath: socket,
            sessionName: request.sessionName
        )
    }

    func renameSession(_ target: TmuxAttachTarget, to name: String) async throws {
        _ = try await run(
            hostID: target.hostID,
            command: commandBuilder.renameSession(target: target, name: name)
        )
    }

    func terminateSession(_ target: TmuxAttachTarget) async throws {
        _ = try await run(
            hostID: target.hostID,
            command: commandBuilder.killSession(target: target)
        )
    }

    nonisolated func attachCommand(for target: TmuxAttachTarget) -> String {
        commandBuilder.attach(target: target)
    }

    func paneScrollMetadata(for target: TmuxAttachTarget) async throws -> TmuxPaneScrollMetadata {
        let output = try await run(
            hostID: target.hostID,
            command: commandBuilder.paneScrollMetadata(target: target)
        )
        let values = output.split(whereSeparator: { $0.isWhitespace }).compactMap { Int($0) }
        guard values.count >= 3 else { throw TmuxControlError.invalidOutput }
        return TmuxPaneScrollMetadata(
            isAlternateScreen: values[0] == 1,
            historySize: max(0, values[1]),
            paneHeight: max(1, values[2])
        )
    }

    func pageHistory(for target: TmuxAttachTarget, scrollUp: Bool, lines: Int) async throws {
        _ = try await run(
            hostID: target.hostID,
            command: commandBuilder.pageHistory(target: target, scrollUp: scrollUp, lines: lines)
        )
    }

    func cancelCopyMode(for target: TmuxAttachTarget) async throws {
        _ = try await run(
            hostID: target.hostID,
            command: commandBuilder.cancelCopyMode(target: target)
        )
    }

    private func run(hostID: String, command: String) async throws -> String {
        guard let host = try await hostRepository.fetch(id: hostID) else {
            throw TmuxControlError.hostNotFound
        }
        let output = try await connectionManager.execute(
            SSHCommandRequest(command: command),
            on: host
        )
        let stderr = String(data: output.stderr, encoding: .utf8) ?? ""
        guard output.exitStatus == 0 else {
            let message = stderr.isEmpty ? "tmux command failed" : stderr
            await note("tmux command failed (\(output.exitStatus)): \(message)", level: .error)
            throw TmuxControlError.remoteFailure(status: output.exitStatus, message: message)
        }
        guard let text = String(data: output.stdout, encoding: .utf8) else {
            throw TmuxControlError.invalidOutput
        }
        let trimmedError = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !trimmedError.isEmpty {
            await note("Remote command returned no sessions: \(trimmedError)", level: .notice)
        }
        return text
    }

    private func note(_ message: String, level: DiagnosticLevel = .info) async {
        let clipped = message.count > 800 ? String(message.prefix(800)) : message
        await diagnostics?.log(level: level, category: "tmux", message: clipped, metadata: [:])
    }
}
