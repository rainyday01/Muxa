@testable import Muxa
import XCTest

@MainActor
final class AppPreferencesStoreTests: XCTestCase {
    func testPersistsAndClampsTerminalPreferences() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "muxa.preferences.test.\(UUID().uuidString)"))
        let store = AppPreferencesStore(defaults: defaults)

        store.appearanceMode = .dark
        store.terminalFontSize = 99
        store.bufferLines = 10
        store.termType = "xterm-256color"
        store.autocompleteEnabled = false

        let reloaded = AppPreferencesStore(defaults: defaults)
        XCTAssertEqual(reloaded.appearanceMode, .dark)
        XCTAssertEqual(reloaded.terminalFontSize, AppPreferenceDefaults.maximumFontSize)
        XCTAssertEqual(reloaded.bufferLines, AppPreferenceDefaults.minimumBufferLines)
        XCTAssertEqual(reloaded.termType, "xterm-256color")
        XCTAssertFalse(reloaded.autocompleteEnabled)
        XCTAssertEqual(AppVersion.marketing.isEmpty, false)
    }

    func testPersistsAppLanguageOverride() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "muxa.preferences.test.\(UUID().uuidString)"))
        let store = AppPreferencesStore(defaults: defaults)

        XCTAssertEqual(store.appLanguage, .english)

        store.appLanguage = .simplifiedChinese
        let reloaded = AppPreferencesStore(defaults: defaults)
        XCTAssertEqual(reloaded.appLanguage, .simplifiedChinese)
        XCTAssertEqual(reloaded.appLanguage.prefValue, "zh")
        XCTAssertEqual(reloaded.appLanguage.localeIdentifier, "zh-Hans")

        reloaded.appLanguage = .english
        let english = AppPreferencesStore(defaults: defaults)
        XCTAssertEqual(english.appLanguage, .english)
        XCTAssertEqual(AppLanguage.fromPref("zh-cn"), .simplifiedChinese)
        XCTAssertEqual(AppLanguage.fromPref("garbage"), .english)
        XCTAssertEqual(AppLanguage.fromPref("system"), .system)
        XCTAssertEqual(AppLanguage.fromPref(nil), .english)
    }

    func testResetToDefaultsClearsUserChoicesAndOrdering() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "muxa.preferences.test.\(UUID().uuidString)"))
        let store = AppPreferencesStore(defaults: defaults)
        store.appearanceMode = .dark
        store.appLanguage = .simplifiedChinese
        store.autocompleteEnabled = false
        store.openLastSession = false
        store.terminalFontSize = 24
        store.bufferLines = 500
        store.preventSleeping = false
        store.appLockEnabled = true
        store.selectedKeyboardLayoutID = "custom"
        store.shortcutBarOrder = ["enter", "esc"]
        store.composeBoxEnabled = true
        store.composeHistory = ["ls"]
        store.hostOrder = ["host"]
        store.sessionOrder = ["host\twork"]

        store.resetToDefaults()

        XCTAssertEqual(store.appearanceMode, .system)
        XCTAssertEqual(store.appLanguage, .english)
        XCTAssertTrue(store.autocompleteEnabled)
        XCTAssertTrue(store.openLastSession)
        XCTAssertEqual(store.terminalFontSize, AppPreferenceDefaults.terminalFontSize)
        XCTAssertEqual(store.bufferLines, AppPreferenceDefaults.bufferLines)
        XCTAssertTrue(store.preventSleeping)
        XCTAssertFalse(store.appLockEnabled)
        XCTAssertEqual(store.selectedKeyboardLayoutID, "general")
        XCTAssertTrue(store.shortcutBarOrder.isEmpty)
        XCTAssertFalse(store.composeBoxEnabled)
        XCTAssertTrue(store.composeHistory.isEmpty)
        XCTAssertTrue(store.hostOrder.isEmpty)
        XCTAssertTrue(store.sessionOrder.isEmpty)
    }
}
