import Foundation
import Observation

@MainActor
@Observable
final class AppPreferencesStore {
    static let shared = AppPreferencesStore()

    private let defaults: UserDefaults

    var appearanceMode: AppearanceMode {
        didSet { defaults.set(appearanceMode.rawValue, forKey: AppPreferenceKey.appearance) }
    }

    /// In-app UI language override; `.system` follows the device locale.
    var appLanguage: AppLanguage {
        didSet { defaults.set(appLanguage.prefValue, forKey: AppPreferenceKey.appLanguage) }
    }

    var customThemeID: String {
        didSet { defaults.set(customThemeID, forKey: AppPreferenceKey.customThemeID) }
    }

    var autocompleteEnabled: Bool {
        didSet { defaults.set(autocompleteEnabled, forKey: AppPreferenceKey.autocompleteEnabled) }
    }

    var openLastSession: Bool {
        didSet { defaults.set(openLastSession, forKey: AppPreferenceKey.openLastSession) }
    }

    var terminalFontSize: Int {
        didSet { defaults.set(clampedFontSize(terminalFontSize), forKey: AppPreferenceKey.terminalFontSize) }
    }

    /// When true, the terminal uses the size fitted to the current viewport
    /// and the system text-size setting instead of `terminalFontSize`.
    var terminalFontSizeAutomatic: Bool {
        didSet {
            guard !isLoadingPreferences else { return }
            defaults.set(terminalFontSizeAutomatic, forKey: AppPreferenceKey.terminalFontSizeAutomatic)
            refreshResolvedFontSize()
        }
    }

    var terminalSchemeID: String {
        didSet { defaults.set(terminalSchemeID, forKey: AppPreferenceKey.terminalSchemeID) }
    }

    var termType: String {
        didSet { defaults.set(termType, forKey: AppPreferenceKey.termType) }
    }

    var bufferLines: Int {
        didSet { defaults.set(clampedBufferLines(bufferLines), forKey: AppPreferenceKey.bufferLines) }
    }

    var preventSleeping: Bool {
        didSet { defaults.set(preventSleeping, forKey: AppPreferenceKey.preventSleeping) }
    }

    var appLockEnabled: Bool {
        didSet { defaults.set(appLockEnabled, forKey: AppPreferenceKey.appLockEnabled) }
    }

    var selectedKeyboardLayoutID: String {
        didSet { defaults.set(selectedKeyboardLayoutID, forKey: AppPreferenceKey.selectedKeyboardLayoutID) }
    }

    var shortcutBarOrder: [String] {
        didSet { defaults.set(shortcutBarOrder, forKey: AppPreferenceKey.shortcutBarOrder) }
    }

    var composeBoxEnabled: Bool {
        didSet { defaults.set(composeBoxEnabled, forKey: AppPreferenceKey.composeBoxEnabled) }
    }

    var composeHistory: [String] {
        didSet { defaults.set(ComposeHistory.encode(composeHistory), forKey: AppPreferenceKey.composeHistory) }
    }

    var hostOrder: [String] {
        didSet { defaults.set(hostOrder, forKey: AppPreferenceKey.hostOrder) }
    }

    var sessionOrder: [String] {
        didSet { defaults.set(sessionOrder, forKey: AppPreferenceKey.sessionOrder) }
    }

    var sessionViewModeRaw: String {
        didSet { defaults.set(sessionViewModeRaw, forKey: AppPreferenceKey.sessionViewMode) }
    }

    var lastOpenedSessionKey: String {
        didSet { defaults.set(lastOpenedSessionKey, forKey: AppPreferenceKey.lastOpenedSessionKey) }
    }

    /// Width used to fit the terminal font. The terminal viewport wins over the window.
    private(set) var layoutViewportWidth: Double = 390
    private(set) var dynamicTypeMultiplier: Double = 1
    private(set) var resolvedFontSize: Int = AppPreferenceDefaults.terminalFontSize
    private var hasTerminalViewport = false
    private var isLoadingPreferences = true

    var terminalScheme: TerminalColorScheme {
        TerminalColorCatalog.scheme(id: terminalSchemeID)
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        appearanceMode = AppearanceMode(rawValue: defaults.string(forKey: AppPreferenceKey.appearance) ?? "")
            ?? .system
        appLanguage = AppLanguage.fromPref(defaults.string(forKey: AppPreferenceKey.appLanguage))
        customThemeID = defaults.string(forKey: AppPreferenceKey.customThemeID) ?? "sequoia"
        autocompleteEnabled = defaults.object(forKey: AppPreferenceKey.autocompleteEnabled) as? Bool
            ?? AppPreferenceDefaults.autocompleteEnabled
        openLastSession = defaults.object(forKey: AppPreferenceKey.openLastSession) as? Bool
            ?? AppPreferenceDefaults.openLastSession
        let storedFont = defaults.object(forKey: AppPreferenceKey.terminalFontSize) as? Int
        if defaults.object(forKey: AppPreferenceKey.terminalFontSizeAutomatic) == nil {
            terminalFontSizeAutomatic = storedFont == nil
                || storedFont == AppPreferenceDefaults.legacyUntouchedFontSize
        } else {
            terminalFontSizeAutomatic = defaults.bool(forKey: AppPreferenceKey.terminalFontSizeAutomatic)
        }
        terminalFontSize = Self.clampedFontSize(storedFont ?? AppPreferenceDefaults.terminalFontSize)
        terminalSchemeID = defaults.string(forKey: AppPreferenceKey.terminalSchemeID)
            ?? TerminalColorCatalog.defaultID
        let storedTerm = defaults.string(forKey: AppPreferenceKey.termType) ?? AppPreferenceDefaults.termType
        termType = AppPreferenceDefaults.termTypes.contains(storedTerm) ? storedTerm : AppPreferenceDefaults.termType
        bufferLines = Self.clampedBufferLines(
            defaults.object(forKey: AppPreferenceKey.bufferLines) as? Int
                ?? AppPreferenceDefaults.bufferLines
        )
        preventSleeping = defaults.object(forKey: AppPreferenceKey.preventSleeping) as? Bool
            ?? AppPreferenceDefaults.preventSleeping
        appLockEnabled = defaults.bool(forKey: AppPreferenceKey.appLockEnabled)
        selectedKeyboardLayoutID = defaults.string(forKey: AppPreferenceKey.selectedKeyboardLayoutID) ?? "general"
        shortcutBarOrder = defaults.stringArray(forKey: AppPreferenceKey.shortcutBarOrder) ?? []
        composeBoxEnabled = defaults.object(forKey: AppPreferenceKey.composeBoxEnabled) as? Bool ?? false
        composeHistory = ComposeHistory.decode(defaults.string(forKey: AppPreferenceKey.composeHistory))
        hostOrder = defaults.stringArray(forKey: AppPreferenceKey.hostOrder) ?? []
        sessionOrder = defaults.stringArray(forKey: AppPreferenceKey.sessionOrder) ?? []
        sessionViewModeRaw = defaults.string(forKey: AppPreferenceKey.sessionViewMode) ?? "byHost"
        lastOpenedSessionKey = defaults.string(forKey: AppPreferenceKey.lastOpenedSessionKey) ?? ""
        defaults.set(terminalFontSizeAutomatic, forKey: AppPreferenceKey.terminalFontSizeAutomatic)
        isLoadingPreferences = false
        refreshResolvedFontSize()
    }

    func noteWindowWidth(_ width: Double) {
        guard !hasTerminalViewport, width > 40 else { return }
        guard abs(width - layoutViewportWidth) > 0.5 else { return }
        layoutViewportWidth = width
        refreshResolvedFontSize()
    }

    func noteTerminalWidth(_ width: Double) {
        guard width > 40 else { return }
        hasTerminalViewport = true
        guard abs(width - layoutViewportWidth) > 0.5 else { return }
        layoutViewportWidth = width
        refreshResolvedFontSize()
    }

    func noteDynamicTypeMultiplier(_ multiplier: Double) {
        guard abs(multiplier - dynamicTypeMultiplier) > 0.001 else { return }
        dynamicTypeMultiplier = multiplier
        refreshResolvedFontSize()
    }

    func currentFontSpec() -> TerminalFontScale.Spec {
        TerminalFontScale.spec(
            viewportWidth: layoutViewportWidth,
            dynamicTypeMultiplier: dynamicTypeMultiplier
        )
    }

    func setCustomFontSize(_ size: Int) {
        let spec = currentFontSpec()
        terminalFontSizeAutomatic = false
        terminalFontSize = min(spec.maximum, max(spec.minimum, size))
        refreshResolvedFontSize()
    }

    func nudgeResolvedFont(by delta: Int) {
        setCustomFontSize(resolvedFontSize + delta)
    }

    private func refreshResolvedFontSize() {
        let spec = currentFontSpec()
        let next = terminalFontSizeAutomatic
            ? spec.preferred
            : min(spec.maximum, max(spec.minimum, terminalFontSize))
        if next != resolvedFontSize {
            resolvedFontSize = next
        }
    }

    func moveHost(id: String, movingUp: Bool, allIDs: [String]) {
        hostOrder = ListOrder.move(ListOrder.merge(saved: hostOrder, all: allIDs), id: id, movingUp: movingUp)
    }

    func moveSession(hostID: String, sessionName: String, movingUp: Bool, allKeys: [String]) {
        let key = ListOrder.sessionKey(hostID: hostID, sessionName: sessionName)
        let merged = ListOrder.merge(saved: sessionOrder, all: allKeys)
        let sameHost = merged.filter { $0.hasPrefix("\(hostID)\t") }
        if sameHost.count >= 2, sameHost.contains(key) {
            let local = ListOrder.move(sameHost, id: key, movingUp: movingUp)
            var result = [String]()
            var inserted = false
            for item in merged {
                if item.hasPrefix("\(hostID)\t") {
                    if !inserted {
                        result.append(contentsOf: local)
                        inserted = true
                    }
                } else {
                    result.append(item)
                }
            }
            if !inserted {
                result.append(contentsOf: local)
            }
            sessionOrder = result
        } else {
            sessionOrder = ListOrder.move(merged, id: key, movingUp: movingUp)
        }
    }

    func adjustFontSize(by delta: Int) {
        nudgeResolvedFont(by: delta)
    }

    func resetToDefaults() {
        appearanceMode = .system
        appLanguage = .english
        customThemeID = "sequoia"
        autocompleteEnabled = AppPreferenceDefaults.autocompleteEnabled
        openLastSession = AppPreferenceDefaults.openLastSession
        terminalFontSizeAutomatic = true
        terminalFontSize = AppPreferenceDefaults.terminalFontSize
        terminalSchemeID = TerminalColorCatalog.defaultID
        termType = AppPreferenceDefaults.termType
        bufferLines = AppPreferenceDefaults.bufferLines
        preventSleeping = AppPreferenceDefaults.preventSleeping
        appLockEnabled = false
        selectedKeyboardLayoutID = "general"
        shortcutBarOrder = []
        composeBoxEnabled = false
        composeHistory = []
        hostOrder = []
        sessionOrder = []
        sessionViewModeRaw = "byHost"
        lastOpenedSessionKey = ""
        refreshResolvedFontSize()
    }

    private func clampedFontSize(_ value: Int) -> Int {
        Self.clampedFontSize(value)
    }

    private func clampedBufferLines(_ value: Int) -> Int {
        Self.clampedBufferLines(value)
    }

    private static func clampedFontSize(_ value: Int) -> Int {
        min(AppPreferenceDefaults.maximumFontSize, max(AppPreferenceDefaults.minimumFontSize, value))
    }

    private static func clampedBufferLines(_ value: Int) -> Int {
        min(AppPreferenceDefaults.maximumBufferLines, max(AppPreferenceDefaults.minimumBufferLines, value))
    }
}
