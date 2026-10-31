import Foundation

struct SSHInteractiveSessionRequest: Equatable, Sendable {
    let host: Host
    let command: String
    let terminal: String
    let geometry: TerminalGeometry
}

struct SSHInteractiveSessionHandle: Hashable, Sendable {
    let id: UUID
    let hostID: String
}

enum SSHInteractiveControlError: Error, Equatable, Sendable {
    case sessionUnavailable
    case invalidTerminalSize
}

protocol SSHInteractiveControlling: Sendable {
    func openPTY(_ request: SSHInteractiveSessionRequest) async throws -> SSHInteractiveSessionHandle
    /// Returns `nil` at EOF and an empty `Data` value when the channel would block.
    func readPTY(maxBytes: Int, using handle: SSHInteractiveSessionHandle) async throws -> Data?
    func writePTY(_ data: Data, using handle: SSHInteractiveSessionHandle) async throws
    func resizePTY(to geometry: TerminalGeometry, using handle: SSHInteractiveSessionHandle) async throws
    func closePTY(using handle: SSHInteractiveSessionHandle) async
}
