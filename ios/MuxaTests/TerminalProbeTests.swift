@testable import Muxa
import SwiftTerm
import XCTest

@MainActor
final class TerminalProbeTests: XCTestCase {
    func testInputIsForwardedAsUTF8() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        controller.send("pwd\r")

        XCTAssertEqual(controller.sentBytes, Array("pwd\r".utf8))
        XCTAssertEqual(controller.lastInput, "pwd\r")
    }

    func testViewportResizeUpdatesTerminalGrid() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        let originalSize = (controller.columns, controller.rows)

        controller.terminalView.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
        controller.terminalView.layoutIfNeeded()

        XCTAssertLessThan(controller.columns, originalSize.0)
        XCTAssertLessThan(controller.rows, originalSize.1)
        XCTAssertEqual(controller.columns, controller.terminalView.getTerminal().cols)
        XCTAssertEqual(controller.rows, controller.terminalView.getTerminal().rows)
    }

    func testCompatibilityFixturePreservesInteractiveApplicationLabels() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 640))
        let terminal = controller.terminalView.getTerminal()

        controller.feed(TerminalCompatibilityFixture.output)

        var rendered = ""
        for row in 0 ..< terminal.rows {
            for col in 0 ..< terminal.cols {
                if let character = terminal.getCharacter(col: col, row: row) {
                    rendered.append(character)
                }
            }
        }
        for application in TerminalCompatibilityFixture.applications {
            XCTAssertTrue(rendered.contains(application), "Missing compatibility fixture: \(application)")
        }
    }

    func testUnicodeTrueColorAndScrollbackFixture() {
        let controller = TerminalSessionController(
            frame: CGRect(x: 0, y: 0, width: 640, height: 160),
            scrollback: 100
        )
        controller.terminalView.layoutIfNeeded()
        let terminal = controller.terminalView.getTerminal()

        controller.feed("中🙂e\u{301}\r\n")
        XCTAssertEqual(terminal.getCharacter(col: 0, row: 0), "中")
        XCTAssertEqual(terminal.getCharData(col: 0, row: 0)?.width, 2)
        XCTAssertEqual(terminal.getCharacter(col: 2, row: 0), "🙂")
        XCTAssertEqual(terminal.getCharacter(col: 4, row: 0), "e\u{301}")

        controller.feed("\u{1B}[38;2;19;49;174mR\u{1B}[0m\r\n")
        XCTAssertEqual(
            terminal.getCharData(col: 0, row: 1)?.attribute.fg,
            Attribute.Color.trueColor(red: 19, green: 49, blue: 174)
        )

        for index in 0 ..< 50 {
            controller.feed("line-\(index)\r\n")
        }
        XCTAssertEqual(terminal.options.scrollback, 100)
        XCTAssertGreaterThan(terminal.buffer.yDisp, 0)
    }

    func testScrollbackCanReturnToBottom() {
        let controller = TerminalSessionController(
            frame: CGRect(x: 0, y: 0, width: 640, height: 160),
            scrollback: 100
        )

        for index in 0 ..< 50 {
            controller.feed("line-\(index)\r\n")
        }
        XCTAssertGreaterThan(controller.terminalView.getTerminal().buffer.yDisp, 0)

        controller.scrollToBottom()
        controller.terminalView.layoutIfNeeded()

        XCTAssertEqual(controller.terminalView.getTerminal().buffer.yDisp, 0)
        XCTAssertTrue(controller.isAtBottom)
    }

    func testSelectAllCopyAndPasteUseSystemClipboard() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.feed("copy me")
        controller.selectAll()
        controller.copySelection()

        XCTAssertEqual(UIPasteboard.general.string, "copy me")

        UIPasteboard.general.string = "pasted text"
        controller.clearCapturedInput()
        controller.pasteClipboard()

        XCTAssertEqual(controller.lastInput, "pasted text")
    }

    func testInputAccessoryShortcutBarIsStableAndConfigurable() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        XCTAssertNil(controller.terminalView.inputAccessoryView)
        XCTAssertEqual(controller.shortcutBar.accessibilityIdentifier, "terminal.keyboard.shortcut-bar")
        XCTAssertFalse(controller.shortcutBar.isSoftwareKeyboardVisible)
        XCTAssertNotNil(controller.shortcutBar.view(identified: "terminal.keyboard.more"))
        XCTAssertNil(controller.shortcutBar.view(identified: "terminal.keyboard.expanded-toggle"))
        XCTAssertNil(controller.shortcutBar.view(identified: "terminal.keyboard.system-toggle"))
        XCTAssertEqual(controller.shortcutBar.view(identified: "terminal.keyboard.visibility")?.isHidden, true)
        XCTAssertEqual(controller.inputAccessoryActions, TerminalShortcutAction.defaultBarActions)

        let customActions = [
            TerminalShortcutAction(id: "custom", label: "Run", input: .text("echo ok\r"))
        ]
        controller.setInputAccessoryActions(customActions)

        XCTAssertEqual(controller.inputAccessoryActions, customActions)
        XCTAssertNil(controller.terminalView.inputAccessoryView)
    }

    func testSoftwareKeyboardCanHideWithoutRemovingTheShortcutBar() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.setSoftwareKeyboardVisible(true)
        controller.setSoftwareKeyboardVisible(false)

        XCTAssertFalse(controller.shortcutBar.isSoftwareKeyboardVisible)
        XCTAssertEqual(
            (controller.terminalView as? MuxaTerminalView)?.suppressSoftwareKeyboard,
            true
        )
        XCTAssertEqual(controller.terminalView.inputView?.accessibilityIdentifier, "terminal.keyboard.collapsed")
        XCTAssertEqual(controller.shortcutBar.accessibilityIdentifier, "terminal.keyboard.shortcut-bar")
    }

    func testInputAccessoryShortcutSendsTerminalBytesOrText() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        XCTAssertTrue(controller.activateShortcut(id: "escape"))
        XCTAssertTrue(controller.activateShortcut(id: "pipe"))
        XCTAssertEqual(controller.sentBytes, [0x1B, 0x7C])
        XCTAssertFalse(controller.activateShortcut(id: "missing"))
    }

    func testExtendedKeyboardAndSystemKeyboardSwitching() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        XCTAssertTrue(controller.isUsingSystemKeyboard)
        XCTAssertFalse(controller.isExtendedKeyboardVisible)
        XCTAssertEqual(controller.terminalView.inputView?.accessibilityIdentifier, "terminal.keyboard.collapsed")

        controller.showSystemKeyboard()
        XCTAssertNil(controller.terminalView.inputView)

        controller.showExtendedKeyboard()
        XCTAssertFalse(controller.isUsingSystemKeyboard)
        XCTAssertTrue(controller.isExtendedKeyboardVisible)
        XCTAssertTrue(controller.terminalView.inputView is TerminalExpandedKeyboardView)

        controller.showSystemKeyboard()
        XCTAssertTrue(controller.isUsingSystemKeyboard)
        XCTAssertFalse(controller.isExtendedKeyboardVisible)
        XCTAssertNil(controller.terminalView.inputView)
    }

    func testHardwareKeyboardCommandsUseTerminalActions() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        let terminal = controller.terminalView as? MuxaTerminalView

        XCTAssertNotNil(terminal)
        XCTAssertGreaterThanOrEqual(terminal?.keyCommands?.count ?? 0, 5)

        controller.performHardwareKeyCommand(.escape)
        controller.performHardwareKeyCommand(.interrupt)

        XCTAssertEqual(controller.sentBytes, [0x1B, 0x03])
    }

    func testBuiltInKeyboardLayoutsEncodeKeysChordsAndModifierLatches() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        controller.setBuiltInKeyboardLayout(.tmux)
        XCTAssertEqual(controller.keyboardLayout.id, BuiltInKeyboardLayout.tmux.rawValue)
        XCTAssertFalse(controller.expandedKeyboardActions.isEmpty)
        XCTAssertTrue(controller.activateShortcut(id: "new-window"))
        XCTAssertEqual(controller.sentBytes, [0x02, 0x63])

        controller.clearCapturedInput()
        controller.setBuiltInKeyboardLayout(.general)
        XCTAssertTrue(controller.activateShortcut(id: "ctrl"))
        XCTAssertEqual(controller.latchedModifiers, [.ctrl])
        XCTAssertTrue(controller.activateShortcut(id: "pipe"))
        XCTAssertEqual(controller.sentBytes, [0x1C])
        XCTAssertTrue(controller.latchedModifiers.isEmpty)
    }

    func testStickyShiftLatchesAndAppliesOneShotSequences() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.setBuiltInKeyboardLayout(.general)

        // Latching Shift mirrors the sticky Ctrl treatment.
        XCTAssertTrue(controller.activateShortcut(id: "shift"))
        XCTAssertEqual(controller.latchedModifiers, [.shift])

        // Shift+Tab is the Backtab sequence, consumed one-shot.
        XCTAssertTrue(controller.activateShortcut(id: "tab"))
        XCTAssertEqual(controller.sentBytes, [0x1B, 0x5B, 0x5A])
        XCTAssertTrue(controller.latchedModifiers.isEmpty)

        // Digits use the US Shift map.
        controller.clearCapturedInput()
        _ = controller.applyKeyAction(
            KeyAction(type: .modifierLatch, keyCode: nil, text: nil, keys: nil, modifier: "shift")
        )
        XCTAssertEqual(
            controller.applyKeyAction(KeyAction(type: .text, keyCode: nil, text: "1", keys: nil, modifier: nil)),
            [UInt8(ascii: "!")]
        )
        XCTAssertTrue(controller.latchedModifiers.isEmpty)

        // Arrows gain the CSI 1;2 shift modifier parameter.
        controller.clearCapturedInput()
        _ = controller.applyKeyAction(
            KeyAction(type: .modifierLatch, keyCode: nil, text: nil, keys: nil, modifier: "shift")
        )
        XCTAssertTrue(controller.activateShortcut(id: "up"))
        XCTAssertEqual(controller.sentBytes, [0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x41])
        XCTAssertTrue(controller.latchedModifiers.isEmpty)
    }

    func testChordBytesShiftTabIsBacktabAndCtrlJIsNewline() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        XCTAssertEqual(
            controller.applyKeyAction(
                KeyAction(type: .chord, keyCode: nil, text: nil, keys: ["Shift", "Tab"], modifier: nil)
            ),
            [0x1B, 0x5B, 0x5A]
        )
        XCTAssertEqual(
            controller.applyKeyAction(
                KeyAction(type: .chord, keyCode: nil, text: nil, keys: ["Control", "j"], modifier: nil)
            ),
            [0x0A]
        )
    }

    func testApplyShiftLatchHelpersMatchAndroidBehavior() {
        XCTAssertEqual(TerminalSessionController.applyShiftLatch([0x09]), [0x1B, 0x5B, 0x5A])
        XCTAssertEqual(TerminalSessionController.applyShiftLatch([0x41]), [0x41])
        XCTAssertEqual(TerminalSessionController.applyUsShift(Int(UInt8(ascii: "a"))), Int(UInt8(ascii: "A")))
        XCTAssertEqual(TerminalSessionController.applyUsShift(Int(UInt8(ascii: "1"))), Int(UInt8(ascii: "!")))
    }

    func testKeyboardActionExecutorSupportsUnicodeTextAndAltLatch() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        XCTAssertEqual(
            controller.applyKeyAction(
                KeyAction(type: .text, keyCode: nil, text: "中文", keys: nil, modifier: nil)
            ),
            Array("中文".utf8)
        )
        XCTAssertEqual(controller.lastInput, "中文")

        controller.clearCapturedInput()
        _ = controller.applyKeyAction(
            KeyAction(type: .modifierLatch, keyCode: nil, text: nil, keys: nil, modifier: "alt")
        )
        XCTAssertEqual(controller.latchedModifiers, [.alt])
        XCTAssertEqual(
            controller.applyKeyAction(
                KeyAction(type: .text, keyCode: nil, text: "x", keys: nil, modifier: nil)
            ),
            [0x1B, 0x78]
        )
        XCTAssertTrue(controller.latchedModifiers.isEmpty)
    }

    func testMarkedTextCompositionUsesSwiftTermUITextInput() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        let input = controller.terminalView

        XCTAssertTrue(input.inputDelegate === controller)
        XCTAssertEqual(input.autocapitalizationType, .none)
        XCTAssertEqual(input.autocorrectionType, .no)
        XCTAssertEqual(input.spellCheckingType, .no)

        controller.setMarkedText("zhong", selectedRange: NSRange(location: 5, length: 0))
        XCTAssertTrue(controller.hasMarkedText)
        XCTAssertNotNil(input.markedTextRange)

        controller.insertText("中")
        XCTAssertFalse(controller.hasMarkedText)
        XCTAssertNil(input.markedTextRange)
        XCTAssertEqual(controller.lastInput, "中")
    }

    func testUnmarkTextCommitsUnresolvedComposition() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))

        controller.setMarkedText("ni", selectedRange: NSRange(location: 2, length: 0))
        XCTAssertTrue(controller.hasMarkedText)

        controller.unmarkText()

        XCTAssertFalse(controller.hasMarkedText)
        XCTAssertEqual(controller.lastInput, "ni")
    }

    func testLinkDelegatePublishesAndForwardsLinkRequest() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        var forwardedRequest: TerminalLinkRequest?
        controller.onOpenLink = { forwardedRequest = $0 }

        controller.requestOpenLink(
            source: controller.terminalView,
            link: "https://example.invalid/docs",
            params: ["id": "fixture"]
        )

        let expected = TerminalLinkRequest(
            link: "https://example.invalid/docs",
            parameters: ["id": "fixture"]
        )
        XCTAssertEqual(controller.lastOpenedLink, expected)
        XCTAssertEqual(forwardedRequest, expected)
    }

    func testGrokFingerScrollSendsWheelInsteadOfAButtonDrag() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.terminalView.layoutIfNeeded()
        controller.feed("\u{1b}[?1049h\u{1b}[?1002h\u{1b}[?1006h")
        let terminal = controller.terminalView.getTerminal()
        XCTAssertTrue(terminal.isCurrentBufferAlternate)
        XCTAssertNotEqual(terminal.mouseMode, .off)
        XCTAssertFalse(controller.terminalView.isScrollEnabled)

        controller.clearCapturedInput()
        controller.sendMouseWheel(scrollUp: true, notches: 1)
        let text = String(decoding: controller.sentBytes, as: UTF8.self)
        XCTAssertTrue(text.contains("<64;"), "expected a wheel-up event, got \(text)")
        XCTAssertFalse(text.contains("<0;"), "vertical scroll must not start a text-selection drag")
    }

    func testAltScreenWithoutMousePagesOnlyAfterAShortDrag() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.terminalView.layoutIfNeeded()
        controller.feed("\u{1b}[?1049h")
        controller.clearCapturedInput()

        let now = Date()
        controller.noteFingerPage(scrollUp: true, rows: 2, now: now)
        XCTAssertTrue(controller.sentBytes.isEmpty)

        controller.noteFingerPage(scrollUp: true, rows: 4, now: now.addingTimeInterval(0.2))
        let text = String(decoding: controller.sentBytes, as: UTF8.self)
        XCTAssertTrue(text.contains("\u{1b}[1;2A"), text)
        XCTAssertTrue(text.contains("\u{1b}[5~"), text)
    }

    func testHideKeyAppearsOnlyWhileTheSystemKeyboardIsUp() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        XCTAssertEqual(controller.shortcutBar.view(identified: "terminal.keyboard.visibility")?.isHidden, true)

        controller.showSystemKeyboard()
        XCTAssertEqual(controller.shortcutBar.view(identified: "terminal.keyboard.visibility")?.isHidden, false)

        controller.showExtendedKeyboard()
        XCTAssertEqual(controller.shortcutBar.view(identified: "terminal.keyboard.visibility")?.isHidden, true)
        XCTAssertNotNil(controller.terminalView.inputView?.view(identified: "terminal.keyboard.expanded.hide"))

        controller.setSoftwareKeyboardVisible(false)
        XCTAssertEqual(controller.shortcutBar.view(identified: "terminal.keyboard.visibility")?.isHidden, true)
        XCTAssertEqual(controller.terminalView.inputView?.accessibilityIdentifier, "terminal.keyboard.collapsed")
        XCTAssertEqual(controller.shortcutBar.accessibilityIdentifier, "terminal.keyboard.shortcut-bar")
    }

    func testShortcutOrderMovesEnterAheadOfTheDefaultBar() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        controller.setBuiltInKeyboardLayout(.general)
        controller.applyShortcutOrder(["enter", "escape"])

        XCTAssertEqual(controller.inputAccessoryActions.first?.id, "enter")
        XCTAssertEqual(controller.inputAccessoryActions.dropFirst().first?.id, "escape")
        XCTAssertTrue(controller.activateShortcut(id: "enter"))
        XCTAssertEqual(controller.sentBytes, [0x0D])
        XCTAssertTrue(controller.inputAccessoryActions.contains { $0.id == "tab" })
    }

    func testComposeBoxGrowsTheBarAndStaysOffByDefault() {
        let controller = TerminalSessionController(frame: CGRect(x: 0, y: 0, width: 640, height: 320))
        XCTAssertFalse(controller.shortcutBar.composeEnabled)
        XCTAssertEqual(controller.shortcutBar.preferredBarHeight, 44)

        controller.toggleComposeBox()
        XCTAssertTrue(controller.shortcutBar.composeEnabled)
        XCTAssertGreaterThan(controller.shortcutBar.preferredBarHeight, 44)
        XCTAssertNotNil(controller.shortcutBar.view(identified: "terminal.compose.field"))
    }
}

@MainActor
private extension UIView {
    func view(identified identifier: String) -> UIView? {
        if accessibilityIdentifier == identifier {
            return self
        }
        for child in subviews {
            if let match = child.view(identified: identifier) {
                return match
            }
        }
        return nil
    }
}
