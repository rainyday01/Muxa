import Foundation

actor TmuxSessionAttachmentCoordinator {
    private let controller: any TmuxControlling

    init(controller: any TmuxControlling) {
        self.controller = controller
    }

    func attachExisting(
        hostID: String,
        sessionName: String,
        knownSocketPath: String? = nil
    ) async throws -> TmuxAttachTarget? {
        let sockets = try await controller.discoverSockets(hostID: hostID)
        let prioritizedSockets = Self.prioritize(
            sockets.filter { Self.looksLikeTmuxSocket($0.path) },
            knownPath: knownSocketPath
        )
        var successfulListings = false
        var lastError: Error?

        if prioritizedSockets.isEmpty {
            do {
                successfulListings = true
                let sessions = try await controller.listSessions(hostID: hostID, socketPath: nil)
                if sessions.contains(where: { $0.name == sessionName }) {
                    return TmuxAttachTarget(hostID: hostID, socketPath: nil, sessionName: sessionName)
                }
            } catch {
                lastError = error
            }
        } else {
            for socket in prioritizedSockets {
                do {
                    successfulListings = true
                    let sessions = try await controller.listSessions(
                        hostID: hostID,
                        socketPath: socket.path
                    )
                    if sessions.contains(where: { $0.name == sessionName }) {
                        return TmuxAttachTarget(
                            hostID: hostID,
                            socketPath: socket.path,
                            sessionName: sessionName
                        )
                    }
                } catch {
                    lastError = error
                }
            }
        }

        if !successfulListings, let lastError {
            throw lastError
        }
        return nil
    }

    func attachOrCreate(
        hostID: String,
        sessionName: String,
        template: TmuxSessionTemplate,
        knownSocketPath: String? = nil
    ) async throws -> TmuxAttachTarget {
        if let existing = try await attachExisting(
            hostID: hostID,
            sessionName: sessionName,
            knownSocketPath: knownSocketPath
        ) {
            return existing
        }

        // nil asks the remote command to pin TMUX_TMPDIR or /tmp.
        // Do not reuse a per-SSH /var/folders socket for a new session.
        return try await controller.createSession(
            TmuxCreateRequest(
                hostID: hostID,
                socketPath: nil,
                sessionName: sessionName,
                initialCommand: template.initialCommand,
                workingDirectory: nil
            )
        )
    }

    /// ssh-agent and other unix sockets share /tmp. Only tmux server sockets
    /// are safe to pass to `tmux -S`.
    private static func looksLikeTmuxSocket(_ path: String) -> Bool {
        path.contains("/tmux-") || path.contains("/tmux/")
    }

    private static func prioritize(
        _ sockets: [TmuxServerSocket],
        knownPath: String?
    ) -> [TmuxServerSocket] {
        var result = [TmuxServerSocket]()
        var seen = Set<String>()
        if let knownPath {
            if let known = sockets.first(where: { $0.path == knownPath }) {
                result.append(known)
                seen.insert(known.path)
            }
        }
        for socket in sockets where seen.insert(socket.path).inserted {
            result.append(socket)
        }
        return result
    }
}
