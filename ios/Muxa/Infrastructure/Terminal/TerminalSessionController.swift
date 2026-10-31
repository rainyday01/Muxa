import Combine
import SwiftTerm
import UIKit
import UniformTypeIdentifiers

struct TerminalViewportMetrics: Equatable, Sendable {
    let geometry: TerminalGeometry
    let cellPixelWidth: Int
    let cellPixelHeight: Int
    let viewportPixelWidth: Int
    let viewportPixelHeight: Int
}

struct TerminalLinkRequest: Equatable, Sendable {
    let link: String
    let parameters: [String: String]
}

@MainActor
final class TerminalSessionController: NSObject, ObservableObject {
    @Published private(set) var lastInput = ""
    @Published private(set) var columns = 0
    @Published private(set) var rows = 0
    @Published private(set) var geometry = TerminalGeometry(columns: 0, rows: 0, pixelWidth: 0, pixelHeight: 0)
    @Published private(set) var viewportMetrics = TerminalViewportMetrics(
        geometry: TerminalGeometry(columns: 0, rows: 0, pixelWidth: 0, pixelHeight: 0),
        cellPixelWidth: 0,
        cellPixelHeight: 0,
        viewportPixelWidth: 0,
        viewportPixelHeight: 0
    )
    @Published private(set) var scrollPosition = 0.0
    @Published private(set) var isAtBottom = true
    @Published private(set) var lastOpenedLink: TerminalLinkRequest?
    @Published private(set) var hasMarkedText = false
    @Published private(set) var isTerminalFirstResponder = false
    @Published var keyboardLayout = KeyboardLayout.general
    @Published var latchedModifiers: Set<TerminalKeyboardModifier> = []
    @Published private(set) var isUsingSystemKeyboard = true
    @Published private(set) var isExtendedKeyboardVisible = false

    private(set) var shortcutBar: TerminalShortcutBar!
    private var expandedKeyboardView: TerminalExpandedKeyboardView?

    let terminalView: TerminalView
    var echoesInput = false
    var onOpenLink: (@MainActor (TerminalLinkRequest) -> Void)?
    var onInput: (@MainActor (Data) -> Void)?
    var onSendDraft: (@MainActor (String) -> Bool)?
    var onComposeHistoryChange: (@MainActor ([String]) -> Void)?
    var onComposeEnabledChange: (@MainActor (Bool) -> Void)?
    var onShortcutOrderChange: (@MainActor ([String]) -> Void)?
    private(set) var shortcutKeyOrder: [String] = []

    private(set) var sentBytes: [UInt8] = []

    init(frame: CGRect = .zero, scrollback: Int = 5000, font: UIFont? = nil) {
        terminalView = MuxaTerminalView(frame: frame, font: font)
        terminalView.keyboardAppearance = .dark
        terminalView.overrideUserInterfaceStyle = .dark
        super.init()
        (terminalView as? MuxaTerminalView)?.onHardwareKeyCommand = { [weak self] command in
            self?.performHardwareKeyCommand(command)
        }
        terminalView.terminalDelegate = self
        terminalView.inputDelegate = self
        terminalView.autocapitalizationType = .none
        terminalView.autocorrectionType = .no
        terminalView.spellCheckingType = .no
        terminalView.smartQuotesType = .no
        terminalView.smartDashesType = .no
        terminalView.smartInsertDeleteType = .no
        terminalView.textContentType = .none
        // SwiftTerm installs its own accessory. The app bar is a sibling so it
        // can stay on screen after the software keyboard is dismissed.
        terminalView.inputAccessoryView = nil
        terminalView.inputAssistantItem.leadingBarButtonGroups = []
        terminalView.inputAssistantItem.trailingBarButtonGroups = []
        (terminalView as? MuxaTerminalView)?.installCollapsedKeyboard()
        let shortcutBar = TerminalShortcutBar(actions: TerminalShortcutAction.defaultBarActions)
        self.shortcutBar = shortcutBar
        shortcutBar.onAction = { [weak self] action in
            self?.sendShortcut(action)
        }
        shortcutBar.onOpenSystemKeyboard = { [weak self] in
            self?.showSystemKeyboard()
        }
        shortcutBar.onOpenShortcutPanel = { [weak self] in
            self?.showExtendedKeyboard()
        }
        shortcutBar.onHideKeyboard = { [weak self] in
            self?.setSoftwareKeyboardVisible(false)
        }
        shortcutBar.onToggleCompose = { [weak self] in
            self?.toggleComposeBox()
        }
        shortcutBar.onRequestReorder = { [weak self] in
            self?.presentShortcutReorder()
        }
        shortcutBar.onSendDraft = { [weak self] text in
            self?.onSendDraft?(text) ?? false
        }
        shortcutBar.onComposeHistoryChange = { [weak self] items in
            self?.onComposeHistoryChange?(items)
        }
        shortcutBar.onPaste = { [weak self] in
            self?.pasteClipboard()
        }
        shortcutBar.isSoftwareKeyboardVisible = false
        terminalView.changeScrollback(scrollback)
        terminalView.accessibilityIdentifier = "terminal.viewport"
        refreshGeometry()
    }

    func feed(_ text: String) {
        terminalView.feed(text: text)
    }

    func feed(_ data: Data) {
        terminalView.feed(byteArray: Array(data)[...])
    }

    func send(_ text: String) {
        terminalView.send(txt: text)
    }

    func send(_ data: Data) {
        terminalView.send(data: Array(data)[...])
    }

    func insertComposerText(_ text: String) {
        terminalView.insertComposerText(text)
    }

    func setCursorStyle(_ style: CursorStyle) {
        terminalView.getTerminal().setCursorStyle(style)
    }

    var inputAccessoryActions: [TerminalShortcutAction] {
        shortcutBar.actions
    }

    func setInputAccessoryActions(_ actions: [TerminalShortcutAction]) {
        shortcutBar.actions = actions
        syncExpandedKeyboardActions()
    }

    func applyShortcutOrder(_ order: [String]) {
        shortcutKeyOrder = order
        refreshOrderedBar()
    }

    func toggleComposeBox() {
        shortcutBar.composeEnabled.toggle()
        onComposeEnabledChange?(shortcutBar.composeEnabled)
    }

    func presentShortcutReorder() {
        let items = shortcutBar.actions.map { (id: $0.id, label: $0.label) }
        let reorder = ShortcutReorderViewController(items: items)
        reorder.onChange = { [weak self] ids in
            self?.applyShortcutOrder(ids)
            self?.onShortcutOrderChange?(ids)
        }
        let navigation = UINavigationController(rootViewController: reorder)
        navigation.modalPresentationStyle = .formSheet
        nearestViewController()?.present(navigation, animated: true)
    }

    func refreshOrderedBar() {
        let keys = ShortcutOrder.apply(keyboardLayout.barKeys, order: shortcutKeyOrder) { $0.id }
        shortcutBar.actions = keys.map(TerminalShortcutAction.init)
    }

    func syncExpandedKeyboardActions() {
        guard isExtendedKeyboardVisible else { return }
        expandedKeyboardView?.actions = expandedKeyboardActions
    }

    private func nearestViewController() -> UIViewController? {
        var responder: UIResponder? = terminalView
        while let next = responder {
            if let controller = next as? UIViewController {
                return controller
            }
            responder = next.next
        }
        return terminalView.window?.rootViewController
    }

    @discardableResult
    func activateShortcut(id: String) -> Bool {
        shortcutBar.activateAction(id: id)
    }

    func toggleSystemKeyboard() {
        if isUsingSystemKeyboard {
            showExtendedKeyboard()
        } else {
            showSystemKeyboard()
        }
    }

    func showSystemKeyboard() {
        (terminalView as? MuxaTerminalView)?.clearCollapsedKeyboard()
        terminalView.inputView = nil
        expandedKeyboardView = nil
        isUsingSystemKeyboard = true
        isExtendedKeyboardVisible = false
        shortcutBar.isUsingSystemKeyboard = true
        shortcutBar.isExpandedKeyboardVisible = false
        shortcutBar.isSoftwareKeyboardVisible = true
        terminalView.reloadInputViews()
    }

    /// The shortcut bar stays on screen. This only shows or hides the software keyboard
    /// under it, matching Android's key-bar keyboard button (bar stays, IME toggles).
    /// Hiding keeps first-responder status behind a zero-height input view, so a later
    /// tap scrolls or clicks the terminal instead of summoning the keyboard again.
    func toggleSoftwareKeyboard() {
        setSoftwareKeyboardVisible(!shortcutBar.isSoftwareKeyboardVisible)
    }

    func setSoftwareKeyboardVisible(_ visible: Bool) {
        let terminal = terminalView as? MuxaTerminalView
        if visible {
            terminal?.clearCollapsedKeyboard()
            if isExtendedKeyboardVisible {
                terminalView.reloadInputViews()
            } else {
                showSystemKeyboard()
            }
            _ = focusInput()
            shortcutBar.isSoftwareKeyboardVisible = true
        } else {
            if isExtendedKeyboardVisible {
                expandedKeyboardView = nil
                isUsingSystemKeyboard = true
                isExtendedKeyboardVisible = false
                shortcutBar.isUsingSystemKeyboard = true
                shortcutBar.isExpandedKeyboardVisible = false
            }
            terminal?.installCollapsedKeyboard()
            _ = focusInput()
            shortcutBar.isSoftwareKeyboardVisible = false
        }
    }

    func toggleExpandedKeyboard() {
        if isExtendedKeyboardVisible {
            showSystemKeyboard()
        } else {
            showExtendedKeyboard()
        }
    }

    func showExtendedKeyboard() {
        let view = TerminalExpandedKeyboardView(actions: expandedKeyboardActions)
        view.onAction = { [weak self] action in
            self?.sendShortcut(action)
        }
        view.onHide = { [weak self] in
            self?.setSoftwareKeyboardVisible(false)
        }
        view.latchedModifiers = latchedModifiers
        expandedKeyboardView = view
        (terminalView as? MuxaTerminalView)?.clearCollapsedKeyboard()
        terminalView.inputView = view
        isUsingSystemKeyboard = false
        isExtendedKeyboardVisible = true
        shortcutBar.isUsingSystemKeyboard = false
        shortcutBar.isExpandedKeyboardVisible = true
        shortcutBar.isSoftwareKeyboardVisible = true
        terminalView.reloadInputViews()
        _ = focusInput()
    }

    /// Mirror latched modifiers (sticky Ctrl/Shift/Alt) onto the key bars so the
    /// one-shot state stays visible, like the Android underline treatment.
    func refreshModifierIndicators() {
        shortcutBar.latchedModifiers = latchedModifiers
        expandedKeyboardView?.latchedModifiers = latchedModifiers
    }

    func performHardwareKeyCommand(_ command: TerminalHardwareKeyCommand) {
        switch command {
        case .copy:
            copySelection()
        case .paste:
            pasteClipboard()
        case .selectAll:
            selectAll()
        case .escape:
            send(Data([0x1B]))
        case .interrupt:
            send(Data([0x03]))
        }
    }

    @discardableResult
    func focusInput() -> Bool {
        let didFocus = terminalView.becomeFirstResponder()
        refreshInputState()
        return didFocus
    }

    @discardableResult
    func resignInput() -> Bool {
        let didResign = terminalView.resignFirstResponder()
        refreshInputState()
        return didResign
    }

    func insertText(_ text: String) {
        terminalView.insertText(text)
        refreshInputState()
    }

    func setMarkedText(_ text: String?, selectedRange: NSRange) {
        terminalView.setMarkedText(text, selectedRange: selectedRange)
        refreshInputState()
    }

    func unmarkText() {
        terminalView.unmarkText()
        refreshInputState()
    }

    func refreshInputState() {
        hasMarkedText = terminalView.markedTextRange != nil
        isTerminalFirstResponder = terminalView.isFirstResponder
        let suppressed = (terminalView as? MuxaTerminalView)?.suppressSoftwareKeyboard == true
        shortcutBar.isSoftwareKeyboardVisible = !suppressed && (isTerminalFirstResponder || isExtendedKeyboardVisible)
    }

    func selectAll() {
        terminalView.selectAll()
    }

    func copySelection() {
        terminalView.copy(nil)
    }

    func pasteClipboard() {
        terminalView.paste(nil)
    }

    func scrollToBottom() {
        terminalView.repositionVisibleFrame()
        terminalView.getTerminal().buffer.yDisp = 0
        terminalView.setContentOffset(.zero, animated: false)
        scrollPosition = 0
        isAtBottom = true
    }

    func changeScrollback(_ lineCount: Int?) {
        terminalView.changeScrollback(lineCount)
    }

    var isAlternateScreen: Bool {
        terminalView.getTerminal().isCurrentBufferAlternate
    }

    func visibleSnapshotText() -> String {
        let terminal = terminalView.getTerminal()
        let start = Position(col: 0, row: 0)
        let end = Position(col: max(0, terminal.cols - 1), row: max(0, terminal.rows - 1))
        let visible = terminal.getText(start: start, end: end)
        if !visible.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return visible
        }
        return String(data: terminal.getBufferAsData(), encoding: .utf8) ?? ""
    }

    func applyAppearance(scheme: TerminalColorScheme, fontSize: Int, scrollback: Int) {
        let font = UIFont.monospacedSystemFont(ofSize: CGFloat(fontSize), weight: .regular)
        terminalView.font = font
        if let foreground = UIColor(muxaHex: scheme.foreground) {
            terminalView.nativeForegroundColor = foreground
        }
        if let background = UIColor(muxaHex: scheme.background) {
            terminalView.nativeBackgroundColor = background
            terminalView.backgroundColor = background
        }
        if let cursor = UIColor(muxaHex: scheme.cursor) {
            terminalView.caretColor = cursor
        }
        let ansi = scheme.ansi.compactMap { hex -> SwiftTerm.Color? in
            guard let color = UIColor(muxaHex: hex) else { return nil }
            var red: CGFloat = 0
            var green: CGFloat = 0
            var blue: CGFloat = 0
            var alpha: CGFloat = 0
            color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            return SwiftTerm.Color(
                red: UInt16((red * 65535).rounded()),
                green: UInt16((green * 65535).rounded()),
                blue: UInt16((blue * 65535).rounded())
            )
        }
        if ansi.count == 16 {
            terminalView.installColors(ansi)
        }
        changeScrollback(scrollback)
        refreshGeometry()
    }

    func sendRemotePage(pageUp: Bool) {
        send(Data(pageUp ? [0x1B, 0x5B, 0x35, 0x7E] : [0x1B, 0x5B, 0x36, 0x7E]))
    }

    /// Wheel press only (buttons 64/65). A release event makes some TUIs ignore the scroll.
    func sendMouseWheel(scrollUp: Bool, notches: Int) {
        let terminal = terminalView.getTerminal()
        guard terminal.mouseMode != .off, notches > 0 else { return }
        let flags = terminal.encodeButton(
            button: scrollUp ? 4 : 5,
            release: false,
            shift: false,
            meta: false,
            control: false
        )
        let x = max(0, terminal.cols / 2)
        let y = max(0, terminal.rows / 2)
        for _ in 0 ..< notches {
            terminal.sendEvent(buttonFlags: flags, x: x, y: y)
        }
    }

    private var remotePageRowAcc = 0
    private var remotePageDirectionUp: Bool?
    private var lastRemotePageAt = Date.distantPast

    /// Rate-limited PgUp/PgDn plus Shift+Up/Down for an alt-screen app with mouse tracking off.
    func noteFingerPage(scrollUp: Bool, rows: Int, now: Date = Date()) {
        guard rows > 0 else { return }
        if remotePageDirectionUp != scrollUp {
            remotePageDirectionUp = scrollUp
            remotePageRowAcc = 0
        }
        remotePageRowAcc += rows
        let threshold = max(5, self.rows / 8)
        guard remotePageRowAcc >= threshold else { return }
        guard now.timeIntervalSince(lastRemotePageAt) >= 0.13 else { return }
        remotePageRowAcc = 0
        lastRemotePageAt = now
        send(Data(scrollUp
            ? [0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x41]
            : [0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x42]))
        sendRemotePage(pageUp: scrollUp)
    }

    func snapshotImage() -> UIImage? {
        let bounds = terminalView.bounds
        guard bounds.width > 8, bounds.height > 8 else { return nil }
        let renderer = UIGraphicsImageRenderer(bounds: bounds)
        return renderer.image { _ in
            terminalView.drawHierarchy(in: bounds, afterScreenUpdates: false)
        }
    }

    func clearCapturedInput() {
        sentBytes.removeAll()
        lastInput = ""
    }

    private func sendShortcut(_ action: TerminalShortcutAction) {
        switch action.input {
        case let .bytes(bytes):
            send(Data(bytes))
        case let .text(text):
            send(text)
        case let .keyAction(action):
            _ = applyKeyAction(action)
        }
    }

    func refreshGeometry() {
        let terminal = terminalView.getTerminal()
        let cellSize = terminalView.cellSizeInPixels(source: terminal)
        let traitScale = terminalView.traitCollection.displayScale
        let windowScale = terminalView.window?.windowScene?.screen.scale ?? terminalView.window?.screen.scale ?? 0
        let fallbackScale = traitScale > 0 ? traitScale : (windowScale > 0 ? windowScale : 1)
        let fallbackCellWidth = terminal.cols > 0
            ? Int((terminalView.bounds.width * fallbackScale / CGFloat(terminal.cols)).rounded())
            : 0
        let fallbackCellHeight = terminal.rows > 0
            ? Int((terminalView.bounds.height * fallbackScale / CGFloat(terminal.rows)).rounded())
            : 0
        let cellPixelWidth = max(0, cellSize?.width ?? fallbackCellWidth)
        let cellPixelHeight = max(0, cellSize?.height ?? fallbackCellHeight)
        let pixelWidth = cellPixelWidth * terminal.cols
        let pixelHeight = cellPixelHeight * terminal.rows
        let nextGeometry = TerminalGeometry(
            columns: terminal.cols,
            rows: terminal.rows,
            pixelWidth: max(0, pixelWidth),
            pixelHeight: max(0, pixelHeight)
        )
        let nextMetrics = TerminalViewportMetrics(
            geometry: nextGeometry,
            cellPixelWidth: cellPixelWidth,
            cellPixelHeight: cellPixelHeight,
            viewportPixelWidth: max(0, Int((terminalView.bounds.width * fallbackScale).rounded())),
            viewportPixelHeight: max(0, Int((terminalView.bounds.height * fallbackScale).rounded()))
        )

        columns = terminal.cols
        rows = terminal.rows
        geometry = nextGeometry
        viewportMetrics = nextMetrics
    }
}

extension TerminalSessionController: UITextInputDelegate {
    func selectionWillChange(_: UITextInput?) {
        refreshInputState()
    }

    func selectionDidChange(_: UITextInput?) {
        refreshInputState()
    }

    func textWillChange(_: UITextInput?) {
        refreshInputState()
    }

    func textDidChange(_: UITextInput?) {
        refreshInputState()
    }

    @available(iOS 18.4, *)
    func conversationContext(_: UIConversationContext?, didChange _: UITextInput?) {
        refreshInputState()
    }
}

extension TerminalSessionController: @MainActor TerminalViewDelegate {
    func sizeChanged(source _: TerminalView, newCols: Int, newRows: Int) {
        columns = newCols
        rows = newRows
        refreshGeometry()
    }

    func setTerminalTitle(source _: TerminalView, title _: String) {}

    func hostCurrentDirectoryUpdate(source _: TerminalView, directory _: String?) {}

    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        sentBytes.append(contentsOf: data)
        lastInput = String(bytes: sentBytes, encoding: .utf8) ?? ""
        if let onInput {
            onInput(Data(data))
        } else if echoesInput {
            source.feed(byteArray: data)
        }
    }

    func scrolled(source _: TerminalView, position _: Double) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            updateScrollState(position: terminalView.scrollPosition)
        }
    }

    func requestOpenLink(source _: TerminalView, link: String, params: [String: String]) {
        let request = TerminalLinkRequest(link: link, parameters: params)
        lastOpenedLink = request
        onOpenLink?(request)
    }

    func bell(source _: TerminalView) {}

    func clipboardCopy(source _: TerminalView, content: Data) {
        UIPasteboard.general.setData(content, forPasteboardType: UTType.utf8PlainText.identifier)
    }

    func clipboardRead(source _: TerminalView) -> Data? {
        nil
    }

    func iTermContent(source _: TerminalView, content _: ArraySlice<UInt8>) {}

    func rangeChanged(source _: TerminalView, startY _: Int, endY _: Int) {}

    private func updateScrollState(position: Double) {
        scrollPosition = min(1, max(0, position))
        isAtBottom = scrollPosition <= 0 || terminalView.getTerminal().buffer.yDisp == 0
    }
}
