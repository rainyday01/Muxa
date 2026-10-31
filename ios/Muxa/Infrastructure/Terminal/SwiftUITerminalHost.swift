import SwiftUI

@MainActor
struct SwiftUITerminalHost: UIViewControllerRepresentable {
    typealias UIViewControllerType = TerminalHostViewController

    let controller: TerminalSessionController
    var focusOnAppear = false
    var resizeDebounceInterval: Duration = .milliseconds(100)
    var keyboardAvoidanceEnabled = true
    var terminalAccessibilityIdentifier: String?
    var onGeometryChange: (@MainActor (TerminalGeometry) -> Void)?
    var onFontStep: (@MainActor (Int) -> Void)?
    var onViewportWidth: (@MainActor (CGFloat) -> Void)?
    var onHostReady: (@MainActor (TerminalHostViewController) -> Void)?

    func makeUIViewController(context _: Context) -> TerminalHostViewController {
        makeHost()
    }

    func makeHost() -> TerminalHostViewController {
        let host = TerminalHostViewController(
            sessionController: controller,
            focusOnAppear: focusOnAppear,
            terminalAccessibilityIdentifier: terminalAccessibilityIdentifier
        )
        host.resizeDebounceInterval = resizeDebounceInterval
        host.keyboardAvoidanceEnabled = keyboardAvoidanceEnabled
        host.onGeometryChange = onGeometryChange
        host.onFontStep = onFontStep
        host.onViewportWidth = onViewportWidth
        onHostReady?(host)
        return host
    }

    func updateUIViewController(_ host: TerminalHostViewController, context _: Context) {
        host.focusOnAppear = focusOnAppear
        host.resizeDebounceInterval = resizeDebounceInterval
        host.keyboardAvoidanceEnabled = keyboardAvoidanceEnabled
        host.terminalAccessibilityIdentifier = terminalAccessibilityIdentifier
        host.onGeometryChange = onGeometryChange
        host.onFontStep = onFontStep
        host.onViewportWidth = onViewportWidth
        host.refreshLayout()
        onHostReady?(host)
    }
}
