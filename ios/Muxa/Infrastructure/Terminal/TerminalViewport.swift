import SwiftUI

struct TerminalViewport: View {
    let controller: TerminalSessionController
    var focusOnAppear = false
    var keyboardAvoidanceEnabled = true
    var terminalAccessibilityIdentifier: String?
    var onGeometryChange: (@MainActor (TerminalGeometry) -> Void)?
    var onFontStep: (@MainActor (Int) -> Void)?
    var onViewportWidth: (@MainActor (CGFloat) -> Void)?
    var onHostReady: (@MainActor (TerminalHostViewController) -> Void)?

    var body: some View {
        SwiftUITerminalHost(
            controller: controller,
            focusOnAppear: focusOnAppear,
            keyboardAvoidanceEnabled: keyboardAvoidanceEnabled,
            terminalAccessibilityIdentifier: terminalAccessibilityIdentifier,
            onGeometryChange: onGeometryChange,
            onFontStep: onFontStep,
            onViewportWidth: onViewportWidth,
            onHostReady: onHostReady
        )
    }
}

enum RemoteNetworkStatus: Equatable, Sendable {
    case unknown
    case available
    case unavailable

    var title: String {
        switch self {
        case .unknown:
            "Network checking"
        case .available:
            "Online"
        case .unavailable:
            "Offline"
        }
    }

    var systemImage: String {
        switch self {
        case .unknown:
            "network"
        case .available:
            "wifi"
        case .unavailable:
            "wifi.slash"
        }
    }
}
