import Foundation

actor SSHRemotePathCompleter: RemotePathCompleting {
    private let connectionManager: ConnectionManager

    init(connectionManager: ConnectionManager) {
        self.connectionManager = connectionManager
    }

    func complete(host: Host, linePrefix: String) async -> [String] {
        guard let command = AutocompleteEngine.remoteCompgenCommand(linePrefix: linePrefix) else {
            return []
        }
        do {
            let output = try await connectionManager.execute(
                SSHCommandRequest(command: command, timeout: .seconds(4)),
                on: host
            )
            let raw = String(data: output.stdout, encoding: .utf8) ?? ""
            return AutocompleteEngine.parseCompgenOutput(raw)
        } catch {
            return []
        }
    }
}
