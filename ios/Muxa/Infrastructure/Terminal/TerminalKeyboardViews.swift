import SwiftTerm
import UIKit

@MainActor
enum TerminalHardwareKeyCommand: Equatable {
    case copy
    case paste
    case selectAll
    case escape
    case interrupt
}

/// Zero-height stand-in so the terminal can stay first responder without a software keyboard.
@MainActor
final class CollapsedKeyboardView: UIView {
    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 0)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: 0)
    }
}

@MainActor
final class MuxaTerminalView: TerminalView, UIGestureRecognizerDelegate {
    var onHardwareKeyCommand: ((TerminalHardwareKeyCommand) -> Void)?
    /// Vertical one-finger drags. `scrollUp` is true when the finger moves down
    /// (toward older context). `rows` is how many cell-rows the finger traveled.
    var onVerticalFingerScroll: ((Bool, Int) -> Void)?

    /// When set, the software keyboard is replaced by a zero-height input view.
    var suppressSoftwareKeyboard = true

    private var contextPanInstalled = false
    private var contextRemainder: CGFloat = 0
    private let contextPan: UIPanGestureRecognizer = {
        let pan = UIPanGestureRecognizer()
        pan.maximumNumberOfTouches = 1
        pan.cancelsTouchesInView = false
        return pan
    }()

    private let collapsedKeyboard: CollapsedKeyboardView = {
        let view = CollapsedKeyboardView(frame: CGRect(x: 0, y: 0, width: 1, height: 0))
        view.autoresizingMask = [.flexibleWidth]
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.accessibilityIdentifier = "terminal.keyboard.collapsed"
        return view
    }()

    /// Insert into the remote input without a trailing Return.
    /// One Enter after this submits in Claude, Agy, Codex, and Grok.
    func insertComposerText(_ text: String) {
        var normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        while normalized.hasSuffix("\n") {
            normalized.removeLast()
        }
        guard !normalized.isEmpty else { return }
        if getTerminal().bracketedPasteMode {
            let pasted = normalized.replacingOccurrences(of: "\n", with: "\r")
            send([0x1B, 0x5B, 0x32, 0x30, 0x30, 0x7E])
            send(data: Array(pasted.utf8)[...])
            send([0x1B, 0x5B, 0x32, 0x30, 0x31, 0x7E])
        } else {
            send(data: Array(normalized.utf8)[...])
        }
        setNeedsDisplay()
    }

    /// SwiftTerm does not open `inputView` for overriding, so suppression assigns the stand-in.
    func installCollapsedKeyboard() {
        suppressSoftwareKeyboard = true
        inputView = collapsedKeyboard
        reloadInputViews()
    }

    func clearCollapsedKeyboard() {
        suppressSoftwareKeyboard = false
        if inputView is CollapsedKeyboardView {
            inputView = nil
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        installContextPanIfNeeded()
        claimForeignPans()
        syncContextScrollOwnership()
    }

    override func bufferActivated(source: Terminal) {
        super.bufferActivated(source: source)
        syncContextScrollOwnership()
    }

    override func mouseModeChanged(source: Terminal) {
        super.mouseModeChanged(source: source)
        claimForeignPans()
        syncContextScrollOwnership()
    }

    private func installContextPanIfNeeded() {
        guard !contextPanInstalled else { return }
        contextPanInstalled = true
        contextPan.addTarget(self, action: #selector(handleContextPan(_:)))
        contextPan.delegate = self
        contextPan.isEnabled = false
        addGestureRecognizer(contextPan)
        // While this pan is deciding, the scroll view must not drag the grid.
        panGestureRecognizer.require(toFail: contextPan)
        claimForeignPans()
    }

    private func claimForeignPans() {
        for recognizer in gestureRecognizers ?? [] {
            guard let pan = recognizer as? UIPanGestureRecognizer else { continue }
            guard pan !== panGestureRecognizer, pan !== contextPan else { continue }
            pan.delegate = self
        }
    }

    private func syncContextScrollOwnership() {
        guard contextPanInstalled else { return }
        let alternate = getTerminal().isCurrentBufferAlternate
        // Alt-screen history belongs to the remote app. The scroll view's own
        // pan would move VT noise and fight the context gesture.
        isScrollEnabled = !alternate
        contextPan.isEnabled = alternate
    }

    @objc private func handleContextPan(_ recognizer: UIPanGestureRecognizer) {
        switch recognizer.state {
        case .began:
            contextRemainder = 0
        case .changed:
            deliverContextRows(recognizer.translation(in: self).y)
            recognizer.setTranslation(.zero, in: self)
        case .ended:
            let velocity = recognizer.velocity(in: self).y
            if abs(velocity) > 700 {
                let extra = min(8, Int(abs(velocity) / 700))
                if extra > 0 {
                    onVerticalFingerScroll?(velocity > 0, extra)
                }
            }
            contextRemainder = 0
        default:
            contextRemainder = 0
        }
    }

    private func deliverContextRows(_ deltaY: CGFloat) {
        let rowHeight = max(8, bounds.height / CGFloat(max(getTerminal().rows, 1)))
        contextRemainder += deltaY
        let rows = Int(contextRemainder / rowHeight)
        guard rows != 0 else { return }
        contextRemainder -= CGFloat(rows) * rowHeight
        onVerticalFingerScroll?(rows > 0, abs(rows))
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let pan = gestureRecognizer as? UIPanGestureRecognizer else {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }
        let velocity = pan.velocity(in: self)
        let translation = pan.translation(in: self)
        let verticalTravel = abs(velocity.y) + abs(translation.y)
        let horizontalTravel = abs(velocity.x) + abs(translation.x)
        let vertical = verticalTravel >= horizontalTravel
        if pan === contextPan {
            return vertical && getTerminal().isCurrentBufferAlternate
        }
        // Mouse-drag and selection pans: a vertical finger move is context
        // scrolling, not a block selection.
        if pan !== panGestureRecognizer, vertical {
            return false
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }

    /// Preserve newlines when a multi-line paste is made inside an inline CLI
    /// (for example Antigravity). Sending each newline through SwiftTerm's
    /// normal text-input path turns it into Return and submits every line.
    override func paste(_ sender: Any?) {
        guard let text = UIPasteboard.general.string,
              Self.isMultiline(text),
              getTerminal().isCurrentBufferAlternate
        else {
            super.paste(sender)
            return
        }
        sendMultilinePaste(text)
    }

    override func insertText(_ text: String) {
        guard Self.isMultiline(text), getTerminal().isCurrentBufferAlternate else {
            super.insertText(text)
            return
        }
        sendMultilinePaste(text)
    }

    private func sendMultilinePaste(_ text: String) {
        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        if getTerminal().bracketedPasteMode {
            send([0x1B, 0x5B, 0x32, 0x30, 0x30, 0x7E])
            send(txt: normalized)
            send([0x1B, 0x5B, 0x32, 0x30, 0x31, 0x7E])
        } else {
            send(txt: normalized)
        }
        setNeedsDisplay()
    }

    private static func isMultiline(_ text: String) -> Bool {
        text.count > 1 && text.contains(where: { $0 == "\n" || $0 == "\r" })
    }

    override var keyCommands: [UIKeyCommand]? {
        [
            UIKeyCommand(input: "c", modifierFlags: .command, action: #selector(copyCommand)),
            UIKeyCommand(input: "v", modifierFlags: .command, action: #selector(pasteCommand)),
            UIKeyCommand(input: "a", modifierFlags: .command, action: #selector(selectAllCommand)),
            UIKeyCommand(input: UIKeyCommand.inputEscape, modifierFlags: [], action: #selector(escapeCommand)),
            UIKeyCommand(input: "c", modifierFlags: .control, action: #selector(interruptCommand))
        ]
    }

    @objc private func copyCommand() {
        onHardwareKeyCommand?(.copy)
    }

    @objc private func pasteCommand() {
        onHardwareKeyCommand?(.paste)
    }

    @objc private func selectAllCommand() {
        onHardwareKeyCommand?(.selectAll)
    }

    @objc private func escapeCommand() {
        onHardwareKeyCommand?(.escape)
    }

    @objc private func interruptCommand() {
        onHardwareKeyCommand?(.interrupt)
    }
}

@MainActor
final class TerminalExpandedKeyboardView: UIInputView {
    var actions: [TerminalShortcutAction] = [] {
        didSet {
            rebuildButtons()
        }
    }

    var onAction: ((TerminalShortcutAction) -> Void)?
    var onHide: (() -> Void)?

    /// Sticky modifiers (Ctrl/Shift/Alt) currently latched; latched keys underline.
    var latchedModifiers: Set<TerminalKeyboardModifier> = [] {
        didSet {
            guard oldValue != latchedModifiers else { return }
            rebuildButtons()
        }
    }

    private let stackView = UIStackView(frame: .zero)

    init(actions: [TerminalShortcutAction]) {
        self.actions = actions
        super.init(frame: .zero, inputViewStyle: .keyboard)
        allowsSelfSizing = true
        accessibilityIdentifier = "terminal.keyboard.expanded"
        setupView()
        rebuildButtons()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 216)
    }

    private func setupView() {
        backgroundColor = .systemBackground
        stackView.axis = .vertical
        stackView.alignment = .fill
        stackView.distribution = .fillEqually
        stackView.spacing = 6
        stackView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stackView)
        NSLayoutConstraint.activate([
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        ])
    }

    private func rebuildButtons() {
        for view in stackView.arrangedSubviews {
            stackView.removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        let hideRow = UIStackView()
        hideRow.axis = .horizontal
        hideRow.alignment = .fill
        hideRow.addArrangedSubview(UIView())
        let hideButton = UIButton(type: .system)
        hideButton.setTitle("收起", for: .normal)
        hideButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        hideButton.accessibilityIdentifier = "terminal.keyboard.expanded.hide"
        hideButton.accessibilityLabel = "收起快捷键面板"
        hideButton.addAction(UIAction { [weak self] _ in
            self?.onHide?()
        }, for: .primaryActionTriggered)
        hideRow.addArrangedSubview(hideButton)
        stackView.addArrangedSubview(hideRow)

        for chunk in actions.chunked(into: 4) {
            let row = UIStackView(frame: .zero)
            row.axis = .horizontal
            row.alignment = .fill
            row.distribution = .fillEqually
            row.spacing = 6
            for action in chunk {
                let button = UIButton(type: .system)
                button.titleLabel?.font = .monospacedSystemFont(ofSize: 15, weight: .medium)
                if isLatched(action) {
                    button.setAttributedTitle(Self.underlinedTitle(action.label), for: .normal)
                } else {
                    button.setTitle(action.label, for: .normal)
                }
                button.accessibilityIdentifier = "terminal.keyboard.expanded.\(action.id)"
                button.accessibilityLabel = action.label
                button.accessibilityTraits = .keyboardKey
                button.backgroundColor = .secondarySystemFill
                button.layer.cornerRadius = 8
                button.addAction(UIAction { [weak self] _ in
                    self?.onAction?(action)
                }, for: .primaryActionTriggered)
                row.addArrangedSubview(button)
            }
            for _ in chunk.count ..< 4 {
                row.addArrangedSubview(UIView())
            }
            stackView.addArrangedSubview(row)
        }
    }

    private func isLatched(_ action: TerminalShortcutAction) -> Bool {
        guard case let .keyAction(keyAction) = action.input, keyAction.type == .modifierLatch,
              let name = keyAction.modifier?.lowercased() else { return false }
        switch name {
        case "ctrl", "control", "^":
            return latchedModifiers.contains(.ctrl)
        case "shift":
            return latchedModifiers.contains(.shift)
        case "alt", "meta", "option":
            return latchedModifiers.contains(.alt)
        default:
            return false
        }
    }

    private static func underlinedTitle(_ label: String) -> NSAttributedString {
        NSAttributedString(
            string: label,
            attributes: [
                .font: UIFont.monospacedSystemFont(ofSize: 15, weight: .medium),
                .underlineStyle: NSUnderlineStyle.single.rawValue
            ]
        )
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [] }
        return stride(from: 0, to: count, by: size).map { start in
            Array(self[start ..< Swift.min(start + size, count)])
        }
    }
}
