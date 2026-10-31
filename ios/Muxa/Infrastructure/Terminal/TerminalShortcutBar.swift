import UIKit

struct TerminalShortcutAction: Equatable, Identifiable, Sendable {
    enum Input: Equatable, Sendable {
        case bytes([UInt8])
        case text(String)
        case keyAction(KeyAction)
    }

    let id: String
    let label: String
    let input: Input
}

/// Shortcut row under the terminal. Keys scroll sideways. The trailing menu only
/// switches the system keyboard, the shortcut panel, and the compose box.
@MainActor
final class TerminalShortcutBar: UIView, UITextViewDelegate {
    var actions: [TerminalShortcutAction] = [] {
        didSet { rebuildKeyButtons() }
    }

    var onAction: ((TerminalShortcutAction) -> Void)?
    var onPaste: (() -> Void)?
    var onOpenSystemKeyboard: (() -> Void)?
    var onOpenShortcutPanel: (() -> Void)?
    var onToggleCompose: (() -> Void)?
    var onHideKeyboard: (() -> Void)?
    var onRequestReorder: (() -> Void)?
    var onSendDraft: ((String) -> Bool)?
    var onComposeHistoryChange: (([String]) -> Void)?
    var onPreferredHeightChange: ((CGFloat) -> Void)?

    /// System or shortcut-panel keyboard is on screen. The shortcut bar itself stays.
    var isSoftwareKeyboardVisible = false {
        didSet {
            guard oldValue != isSoftwareKeyboardVisible else { return }
            updateHideButton()
            updateMenu()
        }
    }

    var isUsingSystemKeyboard = true {
        didSet {
            guard oldValue != isUsingSystemKeyboard else { return }
            updateMenu()
        }
    }

    var isExpandedKeyboardVisible = false {
        didSet {
            guard oldValue != isExpandedKeyboardVisible else { return }
            updateHideButton()
            updateMenu()
        }
    }

    var latchedModifiers: Set<TerminalKeyboardModifier> = [] {
        didSet {
            guard oldValue != latchedModifiers else { return }
            rebuildKeyButtons()
        }
    }

    var composeEnabled = false {
        didSet {
            guard oldValue != composeEnabled else { return }
            composeContainer.isHidden = !composeEnabled
            if !composeEnabled {
                historyOpen = false
            }
            updateMenu()
            publishHeight()
        }
    }

    var composeHistory: [String] = [] {
        didSet { rebuildHistory() }
    }

    private let column = UIStackView()
    private let composeContainer = UIStackView()
    private let editorRow = UIStackView()
    private let historyStack = UIStackView()
    private let draftView = UITextView()
    private let historyButton = UIButton(type: .system)
    private let sendButton = UIButton(type: .system)
    private let keyRow = UIView()
    private let scrollView = UIScrollView()
    private let stackView = UIStackView()
    private let hideButton = UIButton(type: .system)
    private let moreButton = UIButton(type: .system)
    private var hideWidth: NSLayoutConstraint?
    private var draftHeight: NSLayoutConstraint?
    private var historyOpen = false
    private var composeFieldHeight: CGFloat = 36
    private let longPressRelay = LongPressRelay()

    var preferredBarHeight: CGFloat {
        var height: CGFloat = 44
        guard composeEnabled else { return height }
        height += composeFieldHeight + 16
        if historyOpen {
            height += CGFloat(min(composeHistory.count, ComposeHistory.limit)) * 32
        }
        return height
    }

    init(actions: [TerminalShortcutAction] = []) {
        self.actions = actions
        super.init(frame: .zero)
        accessibilityIdentifier = "terminal.keyboard.shortcut-bar"
        overrideUserInterfaceStyle = .dark
        backgroundColor = TerminalBarChrome.bar
        longPressRelay.bar = self
        translatesAutoresizingMaskIntoConstraints = false
        setupView()
        rebuildKeyButtons()
        updateHideButton()
        updateMenu()
        publishHeight()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: preferredBarHeight)
    }

    func activateAction(id: String) -> Bool {
        guard let action = actions.first(where: { $0.id == id }) else { return false }
        onAction?(action)
        return true
    }

    func textViewDidChange(_ textView: UITextView) {
        updateDraftHeight()
    }

    private func setupView() {
        column.axis = .vertical
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.topAnchor.constraint(equalTo: topAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])

        composeContainer.axis = .vertical
        composeContainer.isHidden = true
        composeContainer.layoutMargins = UIEdgeInsets(top: 6, left: 8, bottom: 6, right: 8)
        composeContainer.isLayoutMarginsRelativeArrangement = true
        composeContainer.spacing = 2

        editorRow.axis = .horizontal
        editorRow.alignment = .center
        editorRow.spacing = 4

        var historyConfig = UIButton.Configuration.plain()
        historyConfig.title = "最近"
        historyConfig.image = UIImage(systemName: "chevron.down")
        historyConfig.imagePlacement = .trailing
        historyConfig.imagePadding = 2
        historyConfig.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(pointSize: 9, weight: .semibold)
        historyConfig.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 4, bottom: 4, trailing: 4)
        historyConfig.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var outgoing = incoming
            outgoing.font = .systemFont(ofSize: 12, weight: .medium)
            return outgoing
        }
        historyButton.configuration = historyConfig
        historyButton.tintColor = TerminalBarChrome.ink
        historyButton.accessibilityIdentifier = "terminal.compose.history"
        historyButton.accessibilityLabel = "最近输入"
        historyButton.addAction(UIAction { [weak self] _ in
            self?.toggleHistory()
        }, for: .primaryActionTriggered)
        historyButton.setContentHuggingPriority(.required, for: .horizontal)

        draftView.font = .systemFont(ofSize: 16)
        draftView.keyboardAppearance = .dark
        draftView.backgroundColor = TerminalBarChrome.field
        draftView.textColor = TerminalBarChrome.ink
        draftView.layer.borderWidth = 1
        draftView.layer.borderColor = TerminalBarChrome.stroke.cgColor
        draftView.layer.cornerRadius = 8
        draftView.textContainerInset = UIEdgeInsets(top: 6, left: 6, bottom: 6, right: 6)
        draftView.isScrollEnabled = true
        draftView.delegate = self
        draftView.accessibilityIdentifier = "terminal.compose.field"
        draftView.accessibilityLabel = "先编辑，再整段发送"
        let height = draftView.heightAnchor.constraint(equalToConstant: composeFieldHeight)
        height.isActive = true
        draftHeight = height

        var sendConfig = UIButton.Configuration.plain()
        sendConfig.title = "发送"
        sendConfig.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 8, bottom: 6, trailing: 8)
        sendButton.configuration = sendConfig
        sendButton.tintColor = TerminalBarChrome.ink
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.accessibilityIdentifier = "terminal.compose.send"
        sendButton.addAction(UIAction { [weak self] _ in
            self?.sendDraft()
        }, for: .touchDown)
        sendButton.setContentHuggingPriority(.required, for: .horizontal)

        editorRow.addArrangedSubview(historyButton)
        editorRow.addArrangedSubview(draftView)
        editorRow.addArrangedSubview(sendButton)

        historyStack.axis = .vertical
        historyStack.isHidden = true

        composeContainer.addArrangedSubview(editorRow)
        composeContainer.addArrangedSubview(historyStack)

        keyRow.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceHorizontal = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .horizontal
        stackView.alignment = .fill
        stackView.spacing = 6
        stackView.translatesAutoresizingMaskIntoConstraints = false

        hideButton.translatesAutoresizingMaskIntoConstraints = false
        hideButton.backgroundColor = TerminalBarChrome.fill
        hideButton.layer.cornerRadius = 7
        hideButton.accessibilityIdentifier = "terminal.keyboard.visibility"
        hideButton.accessibilityLabel = "收起键盘"
        var hideConfig = UIButton.Configuration.plain()
        hideConfig.title = "收起"
        hideConfig.image = UIImage(systemName: "keyboard.chevron.compact.down")
        hideConfig.imagePlacement = .leading
        hideConfig.imagePadding = 2
        hideConfig.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8)
        hideConfig.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(pointSize: 13, weight: .semibold)
        hideConfig.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var outgoing = incoming
            outgoing.font = .systemFont(ofSize: 13, weight: .semibold)
            return outgoing
        }
        hideButton.configuration = hideConfig
        hideButton.tintColor = .systemOrange
        hideButton.addAction(UIAction { [weak self] _ in
            self?.onHideKeyboard?()
        }, for: .primaryActionTriggered)

        moreButton.translatesAutoresizingMaskIntoConstraints = false
        moreButton.backgroundColor = TerminalBarChrome.fill
        moreButton.tintColor = TerminalBarChrome.ink
        moreButton.layer.cornerRadius = 7
        moreButton.accessibilityIdentifier = "terminal.keyboard.more"
        moreButton.accessibilityLabel = "输入法和发送框"
        var moreConfig = UIButton.Configuration.plain()
        moreConfig.image = UIImage(systemName: "ellipsis")
        moreConfig.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 10, bottom: 0, trailing: 10)
        moreButton.configuration = moreConfig
        moreButton.showsMenuAsPrimaryAction = true

        keyRow.addSubview(hideButton)
        keyRow.addSubview(scrollView)
        scrollView.addSubview(stackView)
        keyRow.addSubview(moreButton)
        let hideWidth = hideButton.widthAnchor.constraint(equalToConstant: 0)
        self.hideWidth = hideWidth
        NSLayoutConstraint.activate([
            keyRow.heightAnchor.constraint(equalToConstant: 44),
            hideButton.leadingAnchor.constraint(equalTo: keyRow.leadingAnchor, constant: 6),
            hideButton.centerYAnchor.constraint(equalTo: keyRow.centerYAnchor),
            hideButton.heightAnchor.constraint(equalToConstant: 36),
            hideWidth,
            scrollView.leadingAnchor.constraint(equalTo: hideButton.trailingAnchor, constant: 6),
            scrollView.trailingAnchor.constraint(equalTo: moreButton.leadingAnchor, constant: -6),
            scrollView.topAnchor.constraint(equalTo: keyRow.topAnchor, constant: 4),
            scrollView.bottomAnchor.constraint(equalTo: keyRow.bottomAnchor, constant: -4),
            moreButton.trailingAnchor.constraint(equalTo: keyRow.trailingAnchor, constant: -6),
            moreButton.centerYAnchor.constraint(equalTo: keyRow.centerYAnchor),
            moreButton.heightAnchor.constraint(equalToConstant: 36),
            moreButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
        ])

        column.addArrangedSubview(composeContainer)
        column.addArrangedSubview(keyRow)
    }

    private func updateHideButton() {
        let show = isSoftwareKeyboardVisible && !isExpandedKeyboardVisible
        hideButton.isHidden = !show
        hideWidth?.constant = show ? 72 : 0
    }

    private func updateMenu() {
        let systemOn = isSoftwareKeyboardVisible && !isExpandedKeyboardVisible && isUsingSystemKeyboard
        let ime = UIAction(
            title: "系统输入法",
            image: UIImage(systemName: "keyboard"),
            state: systemOn ? .on : .off
        ) { [weak self] _ in
            self?.onOpenSystemKeyboard?()
        }
        let panel = UIAction(
            title: "快捷键面板",
            image: UIImage(systemName: "square.grid.2x2"),
            state: isExpandedKeyboardVisible ? .on : .off
        ) { [weak self] _ in
            self?.onOpenShortcutPanel?()
        }
        let compose = UIAction(
            title: "发送框",
            image: UIImage(systemName: "text.bubble"),
            state: composeEnabled ? .on : .off
        ) { [weak self] _ in
            self?.onToggleCompose?()
        }
        moreButton.menu = UIMenu(children: [ime, panel, compose])
    }

    private func rebuildKeyButtons() {
        for view in stackView.arrangedSubviews {
            stackView.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        for action in actions {
            stackView.addArrangedSubview(makeKeyButton(action))
        }
        stackView.addArrangedSubview(makeControlButton(
            title: "Paste",
            identifier: "terminal.keyboard.paste",
            label: "Paste clipboard"
        ) { [weak self] in
            self?.onPaste?()
        })
    }

    private func makeKeyButton(_ action: TerminalShortcutAction) -> UIButton {
        let button = UIButton(type: .system)
        button.titleLabel?.font = .monospacedSystemFont(ofSize: 14, weight: .medium)
        var configuration = UIButton.Configuration.plain()
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 11, bottom: 0, trailing: 11)
        button.configuration = configuration
        if isLatched(action) {
            button.setAttributedTitle(Self.underlinedTitle(action.label), for: .normal)
        } else {
            button.setTitle(action.label, for: .normal)
        }
        button.accessibilityIdentifier = "terminal.keyboard.shortcut.\(action.id)"
        button.accessibilityLabel = action.label
        button.accessibilityTraits = .keyboardKey
        button.backgroundColor = TerminalBarChrome.fill
        button.tintColor = TerminalBarChrome.ink
        button.layer.cornerRadius = 7
        button.widthAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        button.addAction(UIAction { [weak self] _ in
            self?.onAction?(action)
        }, for: .primaryActionTriggered)
        let press = UILongPressGestureRecognizer(target: longPressRelay, action: #selector(LongPressRelay.fire(_:)))
        button.addGestureRecognizer(press)
        return button
    }

    @objc fileprivate func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        onRequestReorder?()
    }

    private func toggleHistory() {
        guard !composeHistory.isEmpty else { return }
        historyOpen.toggle()
        historyStack.isHidden = !historyOpen
        publishHeight()
    }

    private func rebuildHistory() {
        for view in historyStack.arrangedSubviews {
            historyStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        historyButton.isEnabled = !composeHistory.isEmpty
        for (index, item) in composeHistory.prefix(ComposeHistory.limit).enumerated() {
            let button = UIButton(type: .system)
            button.contentHorizontalAlignment = .leading
            button.setTitle(item.replacingOccurrences(of: "\n", with: " "), for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 13)
            button.titleLabel?.lineBreakMode = .byTruncatingTail
            button.tintColor = TerminalBarChrome.ink
            button.backgroundColor = TerminalBarChrome.fill
            button.accessibilityIdentifier = "terminal.compose.history.\(index)"
            button.addAction(UIAction { [weak self] _ in
                self?.draftView.text = item
                self?.historyOpen = false
                self?.historyStack.isHidden = true
                self?.updateDraftHeight()
            }, for: .primaryActionTriggered)
            button.heightAnchor.constraint(equalToConstant: 32).isActive = true
            historyStack.addArrangedSubview(button)
        }
        if composeHistory.isEmpty {
            historyOpen = false
            historyStack.isHidden = true
        }
        publishHeight()
    }

    private func sendDraft() {
        let text = draftView.text ?? ""
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let next = ComposeHistory.remember(composeHistory, text: text)
        composeHistory = next
        onComposeHistoryChange?(next)
        if onSendDraft?(ComposeHistory.payload(text)) == true {
            draftView.text = ""
            updateDraftHeight()
        }
    }

    private func updateDraftHeight() {
        let width = max(120, draftView.bounds.width)
        let fitted = draftView.sizeThatFits(CGSize(width: width, height: 140)).height
        let next = min(96, max(36, fitted))
        guard abs(next - composeFieldHeight) > 0.5 else { return }
        composeFieldHeight = next
        draftHeight?.constant = next
        publishHeight()
    }

    private func publishHeight() {
        invalidateIntrinsicContentSize()
        onPreferredHeightChange?(preferredBarHeight)
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
                .font: UIFont.monospacedSystemFont(ofSize: 14, weight: .medium),
                .underlineStyle: NSUnderlineStyle.single.rawValue
            ]
        )
    }

    private func makeControlButton(
        title: String,
        identifier: String,
        label: String,
        action: @escaping () -> Void
    ) -> UIButton {
        let button = UIButton(type: .system)
        var configuration = UIButton.Configuration.plain()
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 11, bottom: 0, trailing: 11)
        button.configuration = configuration
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .monospacedSystemFont(ofSize: 14, weight: .bold)
        button.accessibilityIdentifier = identifier
        button.accessibilityLabel = label
        button.accessibilityTraits = .button
        button.backgroundColor = TerminalBarChrome.fill
        button.tintColor = TerminalBarChrome.ink
        button.layer.cornerRadius = 7
        button.addAction(UIAction { _ in action() }, for: .primaryActionTriggered)
        return button
    }
}

private enum TerminalBarChrome {
    static let bar = UIColor(red: 44 / 255, green: 44 / 255, blue: 46 / 255, alpha: 1)
    static let ink = UIColor(red: 229 / 255, green: 229 / 255, blue: 234 / 255, alpha: 1)
    static let fill = UIColor(white: 1, alpha: 0.12)
    static let field = UIColor(white: 1, alpha: 0.08)
    static let stroke = UIColor(red: 99 / 255, green: 99 / 255, blue: 102 / 255, alpha: 1)
}

@MainActor
final class ShortcutReorderViewController: UITableViewController {
    private var items: [(id: String, label: String)]
    var onChange: (([String]) -> Void)?

    init(items: [(id: String, label: String)]) {
        self.items = items
        super.init(style: .insetGrouped)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "快捷键排序"
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(close)
        )
        tableView.setEditing(true, animated: false)
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    override func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
        items.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "key")
            ?? UITableViewCell(style: .default, reuseIdentifier: "key")
        cell.textLabel?.text = items[indexPath.row].label
        cell.accessibilityIdentifier = "terminal.keyboard.reorder.\(items[indexPath.row].id)"
        return cell
    }

    override func tableView(_: UITableView, titleForFooterInSection _: Int) -> String? {
        "拖动调整顺序。向左滑快捷栏可以看到其余按键。"
    }

    override func tableView(_: UITableView, canMoveRowAt _: IndexPath) -> Bool {
        true
    }

    override func tableView(_: UITableView, moveRowAt source: IndexPath, to destination: IndexPath) {
        let item = items.remove(at: source.row)
        items.insert(item, at: destination.row)
        onChange?(items.map(\.id))
    }

    override func tableView(_: UITableView, editingStyleForRowAt _: IndexPath) -> UITableViewCell.EditingStyle {
        .none
    }

    override func tableView(_: UITableView, shouldIndentWhileEditingRowAt _: IndexPath) -> Bool {
        false
    }
}

@MainActor
private final class LongPressRelay: NSObject {
    weak var bar: TerminalShortcutBar?

    @objc nonisolated func fire(_ gesture: UILongPressGestureRecognizer) {
        MainActor.assumeIsolated {
            self.bar?.handleLongPress(gesture)
        }
    }
}

extension TerminalShortcutAction {
    init(key: KeyboardKey) {
        id = key.id
        label = key.label
        input = .keyAction(key.action)
    }

    static let defaultBarActions = KeyboardLayout.general.barKeys.map(TerminalShortcutAction.init)
}
