@testable import Muxa
import XCTest

final class TmuxSessionAttachmentTests: XCTestCase {
    func testKnownSocketIsListedFirstAndExistingSessionIsReused() async throws {
        let controller = AttachmentControllerFixture()
        let coordinator = TmuxSessionAttachmentCoordinator(controller: controller)

        let target = try await coordinator.attachOrCreate(
            hostID: "host",
            sessionName: "work",
            template: TmuxSessionTemplate(kind: .empty),
            knownSocketPath: "/run/user/501/tmux/default"
        )

        XCTAssertEqual(target.socketPath, "/run/user/501/tmux/default")
        XCTAssertEqual(target.sessionName, "work")
        let createdRequest = await controller.createdRequest()
        let listedPaths = await controller.listedSocketPaths()
        XCTAssertNil(createdRequest)
        XCTAssertEqual(listedPaths, ["/run/user/501/tmux/default"])
    }

    func testCreatesOnlyAfterAllKnownSocketsReportMissingSession() async throws {
        let controller = AttachmentControllerFixture(existingSession: nil)
        let coordinator = TmuxSessionAttachmentCoordinator(controller: controller)

        let target = try await coordinator.attachOrCreate(
            hostID: "host",
            sessionName: "new",
            template: TmuxSessionTemplate(kind: .codex)
        )

        XCTAssertNil(target.socketPath)
        let listedPaths = await controller.listedSocketPaths()
        let createdRequest = await controller.createdRequest()
        XCTAssertEqual(listedPaths, ["/tmp/tmux/default", "/run/user/501/tmux/default"])
        XCTAssertEqual(createdRequest?.initialCommand, "codex")
    }

    func testCreateIgnoresSocketsThatAreNotTmuxServers() async throws {
        let controller = AttachmentControllerFixture(
            existingSession: nil,
            sockets: [TmuxServerSocket(path: "/tmp/ssh-abc/agent.1", source: "unknown")]
        )
        let coordinator = TmuxSessionAttachmentCoordinator(controller: controller)

        let target = try await coordinator.attachOrCreate(
            hostID: "host",
            sessionName: "new",
            template: TmuxSessionTemplate(kind: .empty)
        )

        XCTAssertNil(target.socketPath)
        let created = await controller.createdRequest()
        XCTAssertEqual(created?.sessionName, "new")
    }
}

private actor AttachmentControllerFixture: TmuxControlling {
    private let existingSession: String?
    private let sockets: [TmuxServerSocket]
    private var paths = [String]()
    private var request: TmuxCreateRequest?

    init(existingSession: String? = "work", sockets: [TmuxServerSocket]? = nil) {
        self.existingSession = existingSession
        self.sockets = sockets ?? [
            TmuxServerSocket(path: "/tmp/tmux/default", source: "TMPDIR"),
            TmuxServerSocket(path: "/run/user/501/tmux/default", source: "XDG_RUNTIME_DIR")
        ]
    }

    func discoverSockets(hostID _: String) -> [TmuxServerSocket] {
        sockets
    }

    func listSessions(hostID _: String, socketPath: String?) -> [TmuxSession] {
        if let socketPath {
            paths.append(socketPath)
        }
        guard let existingSession else { return [] }
        return [
            TmuxSession(
                hostID: "host",
                name: existingSession,
                windowCount: 1,
                attached: false,
                createdAt: nil,
                lastActivityAt: nil,
                snapshotText: nil,
                snapshotUpdatedAt: nil,
                hostAlias: nil,
                socketPath: socketPath,
                windows: []
            )
        ]
    }

    func listWindows(for _: TmuxAttachTarget) -> [TmuxWindow] {
        []
    }

    func listPanes(for _: TmuxAttachTarget, windowIndex _: Int?) -> [TmuxPane] {
        []
    }

    func capturePane(for _: TmuxAttachTarget, lineLimit _: Int) throws -> String {
        ""
    }

    func probeResources(hostID _: String) throws -> HostResources {
        HostResources()
    }

    func createSession(_ request: TmuxCreateRequest) -> TmuxAttachTarget {
        self.request = request
        return TmuxAttachTarget(
            hostID: request.hostID,
            socketPath: request.socketPath,
            sessionName: request.sessionName
        )
    }

    func renameSession(_: TmuxAttachTarget, to _: String) {}
    func terminateSession(_: TmuxAttachTarget) {}

    nonisolated func attachCommand(for target: TmuxAttachTarget) -> String {
        TmuxCommandBuilder().attach(target: target)
    }

    func listedSocketPaths() -> [String] {
        paths
    }

    func createdRequest() -> TmuxCreateRequest? {
        request
    }
}
