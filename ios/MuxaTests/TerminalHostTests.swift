@testable import Muxa
import SwiftTerm
import UIKit
import XCTest

@MainActor
final class TerminalHostTests: XCTestCase {
    func testHostInstallsControllerTerminalOnceAndKeepsItStable() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        let host = TerminalHostViewController(sessionController: controller)

        host.loadViewIfNeeded()
        host.refreshLayout()
        let terminalView = host.terminalView

        host.refreshLayout()

        XCTAssertTrue(host.terminalView === terminalView)
        XCTAssertTrue(terminalView.superview === host.view)
        XCTAssertEqual(host.view.accessibilityIdentifier, "terminal.host")
        XCTAssertEqual(terminalView.accessibilityIdentifier, "terminal.viewport")
        XCTAssertEqual(host.view.subviews.filter { $0 === terminalView }.count, 1)
    }

    func testHostReportsTerminalGeometryAfterLayout() {
        let controller = TerminalSessionController(frame: .zero)
        let host = TerminalHostViewController(sessionController: controller)
        host.resizeDebounceInterval = .zero
        var reportedGeometry: TerminalGeometry?
        host.onGeometryChange = { reportedGeometry = $0 }

        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        host.refreshLayout()

        XCTAssertEqual(host.geometry, controller.geometry)
        XCTAssertEqual(reportedGeometry, host.geometry)
        XCTAssertEqual(host.geometry.columns, controller.columns)
        XCTAssertEqual(host.geometry.rows, controller.rows)
        XCTAssertGreaterThan(host.geometry.pixelWidth, 0)
        XCTAssertGreaterThan(host.geometry.pixelHeight, 0)
    }

    func testMeasuresCellPixelsAndViewportPixelsAcrossRotation() {
        let controller = TerminalSessionController(frame: .zero)
        let host = TerminalHostViewController(sessionController: controller)
        host.resizeDebounceInterval = .zero

        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        host.refreshLayout()
        let portrait = controller.viewportMetrics

        host.view.frame = CGRect(x: 0, y: 0, width: 700, height: 390)
        host.refreshLayout()
        let landscape = controller.viewportMetrics

        XCTAssertGreaterThan(portrait.cellPixelWidth, 0)
        XCTAssertGreaterThan(portrait.cellPixelHeight, 0)
        XCTAssertEqual(
            portrait.geometry.pixelWidth,
            portrait.cellPixelWidth * portrait.geometry.columns
        )
        XCTAssertEqual(
            portrait.geometry.pixelHeight,
            portrait.cellPixelHeight * portrait.geometry.rows
        )
        XCTAssertNotEqual(portrait.viewportPixelWidth, landscape.viewportPixelWidth)
        XCTAssertNotEqual(portrait.viewportPixelHeight, landscape.viewportPixelHeight)
    }

    func testDebouncesWindowChangeNotificationsUntilLayoutSettles() async throws {
        let controller = TerminalSessionController(frame: .zero)
        let host = TerminalHostViewController(sessionController: controller)
        host.resizeDebounceInterval = .milliseconds(40)
        var reported = [TerminalGeometry]()
        host.onGeometryChange = { reported.append($0) }

        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        host.refreshLayout()
        host.view.frame = CGRect(x: 0, y: 0, width: 700, height: 390)
        host.refreshLayout()

        XCTAssertTrue(reported.isEmpty)
        try await Task.sleep(for: .milliseconds(80))

        XCTAssertEqual(reported.count, 1)
        XCTAssertEqual(reported.first, host.geometry)
    }

    func testAlternateScreenFingerScrollReachesTheHostAsAWheel() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let host = TerminalHostViewController(sessionController: controller)
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        host.refreshLayout()
        controller.feed("\u{1b}[?1049h\u{1b}[?1002h\u{1b}[?1006h")
        controller.clearCapturedInput()

        let terminal = controller.terminalView as? MuxaTerminalView
        terminal?.onVerticalFingerScroll?(true, 2)

        let text = String(decoding: controller.sentBytes, as: UTF8.self)
        XCTAssertTrue(text.contains("<64;"), "expected wheel events, got \(text)")
        XCTAssertFalse(text.contains("<0;"))
    }

    func testClaudeCodexAndGrokScrollByWheelWhileAgyUsesPaneHistory() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let host = TerminalHostViewController(sessionController: controller)
        host.loadViewIfNeeded()
        host.refreshLayout()
        controller.feed("\u{1b}[?1049h\u{1b}[?1002h\u{1b}[?1006h")

        var historyPages = 0
        host.onAltFingerScroll = { _ in
            historyPages += 1
            return true
        }
        controller.clearCapturedInput()
        (controller.terminalView as? MuxaTerminalView)?.onVerticalFingerScroll?(true, 3)
        XCTAssertEqual(historyPages, 1)
        XCTAssertTrue(controller.sentBytes.isEmpty, "inline history must not also send a wheel or page key")

        host.onAltFingerScroll = { _ in false }
        controller.clearCapturedInput()
        (controller.terminalView as? MuxaTerminalView)?.onVerticalFingerScroll?(false, 2)
        let wheel = String(decoding: controller.sentBytes, as: UTF8.self)
        XCTAssertTrue(wheel.contains("<65;"), "Claude, Codex, and Grok wheel-down, got \(wheel)")

        let plain = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let plainHost = TerminalHostViewController(sessionController: plain)
        plainHost.loadViewIfNeeded()
        plainHost.refreshLayout()
        plain.feed("\u{1b}[?1049h")
        plainHost.onAltFingerScroll = { _ in false }
        plain.clearCapturedInput()
        (plain.terminalView as? MuxaTerminalView)?.onVerticalFingerScroll?(true, 6)
        let page = String(decoding: plain.sentBytes, as: UTF8.self)
        XCTAssertTrue(page.contains("\u{1b}[1;2A"), page)
        XCTAssertTrue(page.contains("\u{1b}[5~"), page)
    }

    func testSwiftUIBridgeCreatesAStableUIKitHost() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let bridge = SwiftUITerminalHost(controller: controller)
        let host = bridge.makeHost()

        host.loadViewIfNeeded()
        host.refreshLayout()

        XCTAssertTrue(host.sessionController === controller)
        XCTAssertTrue(host.terminalView === controller.terminalView)
    }

    func testKeyboardAvoidanceUsesKeyboardLayoutGuideAndCanBeToggled() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let host = TerminalHostViewController(sessionController: controller)

        host.loadViewIfNeeded()
        host.refreshLayout()

        XCTAssertTrue(host.isKeyboardAvoidanceActive)
        host.keyboardAvoidanceEnabled = false
        XCTAssertFalse(host.isKeyboardAvoidanceActive)
        host.keyboardAvoidanceEnabled = true
        XCTAssertTrue(host.isKeyboardAvoidanceActive)
    }

    func testKeyboardAvoidanceIsForwardedThroughSwiftUIBridge() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let bridge = SwiftUITerminalHost(
            controller: controller,
            keyboardAvoidanceEnabled: false
        )

        let host = bridge.makeHost()
        host.loadViewIfNeeded()
        host.refreshLayout()

        XCTAssertFalse(host.isKeyboardAvoidanceActive)
    }

    func testHostCanFocusAndResignTerminalInput() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let host = TerminalHostViewController(sessionController: controller)
        host.loadViewIfNeeded()
        host.refreshLayout()

        XCTAssertTrue(host.terminalView.canBecomeFirstResponder)
        _ = host.resignTerminal()
    }
}
