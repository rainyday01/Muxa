import SwiftTerm
import UIKit

@MainActor
final class TerminalHostViewController: UIViewController {
    let sessionController: TerminalSessionController

    var focusOnAppear: Bool {
        didSet {
            guard focusOnAppear, oldValue != focusOnAppear, viewIfLoaded?.window != nil else { return }
            focusTerminal()
        }
    }

    var onGeometryChange: (@MainActor (TerminalGeometry) -> Void)?
    var onAltFingerScroll: (@MainActor (Bool) -> Bool)?
    /// Pinch steps the real terminal font. Positive means larger.
    var onFontStep: (@MainActor (Int) -> Void)?
    var onViewportWidth: (@MainActor (CGFloat) -> Void)?
    var resizeDebounceInterval: Duration = .milliseconds(100)

    /// Keeps the terminal above the software keyboard without replacing the stable UIKit host.
    var keyboardAvoidanceEnabled = true {
        didSet {
            guard oldValue != keyboardAvoidanceEnabled, viewIfLoaded != nil else { return }
            updateKeyboardAvoidanceConstraints()
        }
    }

    private(set) var isKeyboardAvoidanceActive = false

    var terminalAccessibilityIdentifier: String? {
        didSet {
            applyTerminalAccessibilityIdentifier()
        }
    }

    private(set) var geometry = TerminalGeometry(columns: 0, rows: 0, pixelWidth: 0, pixelHeight: 0)
    private var hasInstalledTerminalView = false
    private var hasAttemptedInitialFocus = false
    private var geometryNotificationTask: Task<Void, Never>?
    private var lastNotifiedGeometry: TerminalGeometry?
    private var barBottomConstraint: NSLayoutConstraint?
    private var barKeyboardBottomConstraint: NSLayoutConstraint?
    private var barHeightConstraint: NSLayoutConstraint?
    private var pinchRecognizer: UIPinchGestureRecognizer?
    private var pinchAccumulated: CGFloat = 1
    private var lastReportedViewportWidth: CGFloat = 0

    var terminalView: TerminalView {
        sessionController.terminalView
    }

    init(
        sessionController: TerminalSessionController,
        focusOnAppear: Bool = false,
        terminalAccessibilityIdentifier: String? = nil
    ) {
        self.sessionController = sessionController
        self.focusOnAppear = focusOnAppear
        self.terminalAccessibilityIdentifier = terminalAccessibilityIdentifier
        super.init(nibName: nil, bundle: nil)
        applyTerminalAccessibilityIdentifier()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        let hostView = UIView(frame: .zero)
        hostView.backgroundColor = .black
        hostView.accessibilityIdentifier = "terminal.host"
        view = hostView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        installTerminalViewIfNeeded()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        refreshGeometry()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        updateKeyboardAvoidanceConstraints()
        guard !hasAttemptedInitialFocus else { return }
        hasAttemptedInitialFocus = true
        // Open ready to read. The system keyboard comes up from the shortcut menu
        // (or when a caller explicitly asks to focus with it).
        sessionController.setSoftwareKeyboardVisible(focusOnAppear)
    }

    func refreshLayout() {
        loadViewIfNeeded()
        view.setNeedsLayout()
        view.layoutIfNeeded()
        refreshGeometry()
    }

    func flushGeometryChange() {
        geometryNotificationTask?.cancel()
        geometryNotificationTask = nil
        notifyGeometryIfNeeded()
    }

    @discardableResult
    func focusTerminal() -> Bool {
        loadViewIfNeeded()
        return terminalView.becomeFirstResponder()
    }

    @discardableResult
    func resignTerminal() -> Bool {
        terminalView.resignFirstResponder()
    }

    private func installTerminalViewIfNeeded() {
        guard !hasInstalledTerminalView else { return }
        hasInstalledTerminalView = true

        terminalView.translatesAutoresizingMaskIntoConstraints = false
        applyTerminalAccessibilityIdentifier()
        let shortcutBar = sessionController.shortcutBar!
        shortcutBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(terminalView)
        view.addSubview(shortcutBar)
        shortcutBar.onPreferredHeightChange = { [weak self] height in
            guard let self, let barHeightConstraint = self.barHeightConstraint else { return }
            guard abs(barHeightConstraint.constant - height) > 0.5 else { return }
            barHeightConstraint.constant = height
            self.view.setNeedsLayout()
        }
        let bottomConstraint = shortcutBar.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        let keyboardBottomConstraint = shortcutBar.bottomAnchor.constraint(
            equalTo: view.keyboardLayoutGuide.topAnchor
        )
        barBottomConstraint = bottomConstraint
        barKeyboardBottomConstraint = keyboardBottomConstraint
        // The key bar stays under the terminal whether or not the software
        // keyboard is up. Horizontal safe area keeps glyphs off Duo insets.
        NSLayoutConstraint.activate([
            terminalView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            terminalView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            terminalView.topAnchor.constraint(equalTo: view.topAnchor),
            terminalView.bottomAnchor.constraint(equalTo: shortcutBar.topAnchor),
            shortcutBar.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            shortcutBar.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            bottomConstraint,
            keyboardBottomConstraint
        ])
        let heightConstraint = shortcutBar.heightAnchor.constraint(
            equalToConstant: shortcutBar.preferredBarHeight
        )
        heightConstraint.isActive = true
        barHeightConstraint = heightConstraint
        view.keyboardLayoutGuide.followsUndockedKeyboard = true
        updateKeyboardAvoidanceConstraints()
        installGesturesIfNeeded()
        if let terminal = terminalView as? MuxaTerminalView {
            terminal.onVerticalFingerScroll = { [weak self] scrollUp, rows in
                self?.handleContextScroll(scrollUp: scrollUp, rows: rows)
            }
        }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(softwareKeyboardChanged(_:)),
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(softwareKeyboardChanged(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(softwareKeyboardDidSettle(_:)),
            name: UIResponder.keyboardDidShowNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(softwareKeyboardDidSettle(_:)),
            name: UIResponder.keyboardDidHideNotification,
            object: nil
        )
    }

    @objc private func softwareKeyboardDidSettle(_ notification: Notification) {
        view.layoutIfNeeded()
        sessionController.refreshGeometry()
        let nextGeometry = sessionController.geometry
        if geometry != nextGeometry {
            geometry = nextGeometry
        }
        flushGeometryChange()
    }

    @objc private func softwareKeyboardChanged(_ notification: Notification) {
        // The bar's 收起 key is only for a keyboard that is actually on screen.
        // A collapsed terminal input view reports height 0 and must not count.
        let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect
        let height = frame?.height ?? 0
        let showing = notification.name == UIResponder.keyboardWillShowNotification && height > 1
        sessionController.shortcutBar?.isSoftwareKeyboardVisible = showing
    }

    private func updateKeyboardAvoidanceConstraints() {
        guard let barBottomConstraint, let barKeyboardBottomConstraint else { return }
        // A keyboard layout guide has a zero-sized frame until the controller is
        // attached to a window. Keep the ordinary bottom constraint for detached
        // hosts so geometry can still be measured in unit tests and previews.
        let useKeyboardGuide = keyboardAvoidanceEnabled && view.window != nil
        barBottomConstraint.isActive = !useKeyboardGuide
        barKeyboardBottomConstraint.isActive = useKeyboardGuide
        isKeyboardAvoidanceActive = keyboardAvoidanceEnabled
        view.setNeedsLayout()
    }

    private func installGesturesIfNeeded() {
        if pinchRecognizer == nil {
            let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
            pinch.delegate = self
            view.addGestureRecognizer(pinch)
            pinchRecognizer = pinch
        }
    }

    /// Vertical finger scroll for every attached CLI, matching Android.
    /// Inline history (Antigravity) is paged with tmux copy-mode.
    /// Full-screen mouse apps (Claude, Codex, Grok) get a wheel, not a drag selection.
    /// A full-screen app with mouse tracking off gets Shift+Up/Down and PgUp/PgDn.
    private func handleContextScroll(scrollUp: Bool, rows: Int) {
        guard rows > 0 else { return }
        let terminal = sessionController.terminalView.getTerminal()
        guard terminal.isCurrentBufferAlternate else { return }
        if onAltFingerScroll?(scrollUp) ?? false {
            return
        }
        if terminal.mouseMode != .off {
            sessionController.sendMouseWheel(scrollUp: scrollUp, notches: rows)
            return
        }
        sessionController.noteFingerPage(scrollUp: scrollUp, rows: rows)
    }

    @objc private func handlePinch(_ recognizer: UIPinchGestureRecognizer) {
        switch recognizer.state {
        case .began:
            pinchAccumulated = 1
        case .changed:
            pinchAccumulated *= recognizer.scale
            recognizer.scale = 1
            if pinchAccumulated >= 1.12 {
                pinchAccumulated = 1
                onFontStep?(1)
            } else if pinchAccumulated <= 0.88 {
                pinchAccumulated = 1
                onFontStep?(-1)
            }
        default:
            pinchAccumulated = 1
        }
    }

    private func applyTerminalAccessibilityIdentifier() {
        guard let terminalAccessibilityIdentifier else { return }
        terminalView.accessibilityIdentifier = terminalAccessibilityIdentifier
    }

    private func refreshGeometry() {
        guard isViewLoaded else { return }
        installTerminalViewIfNeeded()
        sessionController.refreshGeometry()
        let width = view.bounds.width
        if width > 40, abs(width - lastReportedViewportWidth) > 0.5 {
            lastReportedViewportWidth = width
            onViewportWidth?(width)
        }

        let nextGeometry = sessionController.geometry
        guard geometry != nextGeometry else { return }
        geometry = nextGeometry
        scheduleGeometryNotification()
    }

    private func scheduleGeometryNotification() {
        geometryNotificationTask?.cancel()
        guard resizeDebounceInterval > .zero else {
            notifyGeometryIfNeeded()
            return
        }

        let debounceInterval = resizeDebounceInterval
        geometryNotificationTask = Task { [weak self] in
            do {
                try await Task.sleep(for: debounceInterval)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            self?.notifyGeometryIfNeeded()
        }
    }

    private func notifyGeometryIfNeeded() {
        guard lastNotifiedGeometry != geometry else { return }
        lastNotifiedGeometry = geometry
        onGeometryChange?(geometry)
    }
}

extension TerminalHostViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer
    ) -> Bool {
        gestureRecognizer === pinchRecognizer
    }
}
