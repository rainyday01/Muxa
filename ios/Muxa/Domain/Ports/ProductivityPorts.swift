import Foundation

protocol DesktopPushPort: Sendable {
    func isAvailable() async -> Bool
}

protocol CommandPalettePort: Sendable {
    func isAvailable() async -> Bool
}

struct NoOpDesktopPushPort: DesktopPushPort {
    func isAvailable() async -> Bool {
        false
    }
}

struct NoOpCommandPalettePort: CommandPalettePort {
    func isAvailable() async -> Bool {
        false
    }
}

protocol RemotePathCompleting: Sendable {
    func complete(host: Host, linePrefix: String) async -> [String]
}

struct EmptyRemotePathCompleter: RemotePathCompleting {
    func complete(host _: Host, linePrefix _: String) async -> [String] {
        []
    }
}
