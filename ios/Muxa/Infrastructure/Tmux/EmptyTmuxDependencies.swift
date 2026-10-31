import Foundation

actor EmptyTmuxController: TmuxControlling {
    func discoverSockets(hostID _: String) -> [TmuxServerSocket] {
        []
    }

    func listSessions(hostID _: String, socketPath _: String?) -> [TmuxSession] {
        []
    }

    func listWindows(for _: TmuxAttachTarget) -> [TmuxWindow] {
        []
    }

    func listPanes(for _: TmuxAttachTarget, windowIndex _: Int?) -> [TmuxPane] {
        []
    }

    func capturePane(for _: TmuxAttachTarget, lineLimit _: Int) throws -> String {
        throw TmuxControlError.remoteFailure(status: 1, message: "tmux unavailable")
    }

    func probeResources(hostID _: String) throws -> HostResources {
        HostResources()
    }

    func createSession(_: TmuxCreateRequest) throws -> TmuxAttachTarget {
        throw TmuxControlError.remoteFailure(status: 1, message: "tmux unavailable")
    }

    func renameSession(_: TmuxAttachTarget, to _: String) throws {
        throw TmuxControlError.remoteFailure(status: 1, message: "tmux unavailable")
    }

    func terminateSession(_: TmuxAttachTarget) throws {
        throw TmuxControlError.remoteFailure(status: 1, message: "tmux unavailable")
    }

    nonisolated func attachCommand(for target: TmuxAttachTarget) -> String {
        TmuxCommandBuilder().attach(target: target)
    }
}

actor EmptySessionSnapshotRepository: SessionSnapshotRepository {
    func fetch(hostID _: String, sessionName _: String) -> SessionSnapshot? {
        nil
    }

    func save(_: SessionSnapshot) {}

    func deleteAll(hostID _: String) {}
}

actor EmptyCachedSessionRepository: CachedSessionRepository {
    func fetch(hostID _: String) -> [TmuxSession] {
        []
    }

    func replace(hostID _: String, sessions _: [TmuxSession], listedAt _: Date) {}

    func deleteAll(hostID _: String) {}
}
