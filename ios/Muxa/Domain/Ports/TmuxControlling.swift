import Foundation

struct TmuxServerSocket: Codable, Equatable, Hashable, Sendable {
    let path: String
    let source: String
}

struct TmuxCreateRequest: Equatable, Sendable {
    let hostID: String
    let socketPath: String?
    let sessionName: String
    let initialCommand: String?
    let workingDirectory: String?
}

struct TmuxAttachTarget: Equatable, Sendable {
    let hostID: String
    let socketPath: String?
    let sessionName: String
}

struct TmuxPaneScrollMetadata: Equatable, Sendable {
    let isAlternateScreen: Bool
    let historySize: Int
    let paneHeight: Int
}

protocol TmuxControlling: Sendable {
    func discoverSockets(hostID: String) async throws -> [TmuxServerSocket]
    func listSessions(hostID: String, socketPath: String?) async throws -> [TmuxSession]
    func listWindows(for target: TmuxAttachTarget) async throws -> [TmuxWindow]
    func listPanes(for target: TmuxAttachTarget, windowIndex: Int?) async throws -> [TmuxPane]
    func capturePane(for target: TmuxAttachTarget, lineLimit: Int) async throws -> String
    /// Cheap mem/disk sample over SSH exec (after probe / attach).
    func probeResources(hostID: String) async throws -> HostResources
    func createSession(_ request: TmuxCreateRequest) async throws -> TmuxAttachTarget
    func renameSession(_ target: TmuxAttachTarget, to name: String) async throws
    func terminateSession(_ target: TmuxAttachTarget) async throws
    func attachCommand(for target: TmuxAttachTarget) -> String
    /// Reads the tmux pane state used to distinguish inline TUIs from a real
    /// alternate-screen application when handling a finger page gesture.
    func paneScrollMetadata(for target: TmuxAttachTarget) async throws -> TmuxPaneScrollMetadata
    /// Pages an inline pane through tmux copy-mode instead of sending PgUp to
    /// the attached CLI, which would be interpreted by some TUIs.
    func pageHistory(for target: TmuxAttachTarget, scrollUp: Bool, lines: Int) async throws
    func cancelCopyMode(for target: TmuxAttachTarget) async throws
}

extension TmuxControlling {
    func paneScrollMetadata(for _: TmuxAttachTarget) async throws -> TmuxPaneScrollMetadata {
        throw TmuxControlError.unsupported
    }

    func pageHistory(for _: TmuxAttachTarget, scrollUp _: Bool, lines _: Int) async throws {
        throw TmuxControlError.unsupported
    }

    func cancelCopyMode(for _: TmuxAttachTarget) async throws {
        throw TmuxControlError.unsupported
    }
}
