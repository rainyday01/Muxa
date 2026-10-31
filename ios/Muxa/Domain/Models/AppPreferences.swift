import Foundation
import SwiftUI

enum AppearanceMode: String, CaseIterable, Identifiable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"
    case custom = "CUSTOM"

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        case .custom: "Custom"
        }
    }
}

enum AppPreferenceKey {
    static let appearance = "muxa.appearance"
    static let appLanguage = "muxa.appLanguage"
    static let customThemeID = "muxa.customThemeId"
    static let autocompleteEnabled = "muxa.autocompleteEnabled"
    static let openLastSession = "muxa.sessions.openRecentAtLaunch"
    static let terminalFontSize = "muxa.terminal.fontSizePt"
    static let terminalFontSizeAutomatic = "muxa.terminal.fontSizeAutomatic"
    static let terminalSchemeID = "muxa.terminal.schemeId"
    static let termType = "muxa.terminal.termType"
    static let bufferLines = "muxa.terminal.bufferLines"
    static let preventSleeping = "muxa.terminal.preventSleeping"
    static let appLockEnabled = "muxa.security.appLockEnabled"
    static let selectedKeyboardLayoutID = "muxa.keyboard.selectedLayoutId"
    static let hostOrder = "muxa.hostOrder"
    static let sessionOrder = "muxa.sessionOrder"
    static let sessionViewMode = "muxa.sessions.viewMode"
    static let lastOpenedSessionKey = "muxa.sessions.lastOpenedKey"
    static let shortcutBarOrder = "muxa.keyboard.shortcutBarOrder"
    static let composeBoxEnabled = "muxa.terminal.composeBoxEnabled"
    static let composeHistory = "muxa.terminal.composeHistory"
}

enum AppPreferenceDefaults {
    static let autocompleteEnabled = true
    static let openLastSession = true
    static let terminalFontSize = 12
    /// Builds that shipped 13pt as the untouched default. That value means
    /// "fit the screen" rather than an explicit choice.
    static let legacyUntouchedFontSize = 13
    static let minimumFontSize = 8
    static let maximumFontSize = 32
    static let termType = "xterm-256color"
    static let bufferLines = 10000
    static let minimumBufferLines = 200
    static let maximumBufferLines = 50000
    static let preventSleeping = true
    static let termTypes = ["xterm-256color", "xterm", "vt100", "linux"]
}

enum AppVersion {
    static var marketing: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"
    }

    static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    static var display: String {
        "\(marketing) (\(build))"
    }
}
