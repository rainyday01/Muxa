@testable import Muxa
import XCTest

final class TerminalFontScaleTests: XCTestCase {
    func testIPhone15WidthPrefersTheSmallReadableSize() {
        let spec = TerminalFontScale.spec(viewportWidth: 393)
        XCTAssertEqual(spec.preferred, 12)
        XCTAssertLessThanOrEqual(spec.minimum, 9)
        XCTAssertGreaterThan(spec.maximum, spec.preferred)
        XCTAssertLessThan(spec.maximum, 28)
    }

    func testWiderCanvasKeepsGlyphsFromGrowingWithoutBound() {
        let phone = TerminalFontScale.spec(viewportWidth: 393)
        let duoInner = TerminalFontScale.spec(viewportWidth: 951)
        XCTAssertGreaterThan(duoInner.preferred, phone.preferred - 1)
        XCTAssertLessThan(duoInner.preferred, 18)
    }

    func testDynamicTypeShiftsTheFittedSize() {
        let standard = TerminalFontScale.spec(viewportWidth: 393, dynamicTypeMultiplier: 1)
        let larger = TerminalFontScale.spec(viewportWidth: 393, dynamicTypeMultiplier: 1.26)
        XCTAssertGreaterThan(larger.preferred, standard.preferred)
        XCTAssertGreaterThan(larger.minimum, standard.minimum - 1)
    }
}

@MainActor
final class TerminalFontPreferenceTests: XCTestCase {
    func testUntouchedLegacyDefaultFollowsTheScreen() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "muxa.font.legacy.\(UUID().uuidString)"))
        defaults.set(AppPreferenceDefaults.legacyUntouchedFontSize, forKey: AppPreferenceKey.terminalFontSize)
        let store = AppPreferencesStore(defaults: defaults)
        XCTAssertTrue(store.terminalFontSizeAutomatic)
        store.noteWindowWidth(393)
        XCTAssertEqual(store.resolvedFontSize, 12)
    }

    func testExplicitSizeStaysPutUntilTheScreenCannotHoldIt() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "muxa.font.custom.\(UUID().uuidString)"))
        defaults.set(20, forKey: AppPreferenceKey.terminalFontSize)
        defaults.set(false, forKey: AppPreferenceKey.terminalFontSizeAutomatic)
        let store = AppPreferencesStore(defaults: defaults)
        store.noteWindowWidth(393)
        XCTAssertFalse(store.terminalFontSizeAutomatic)
        XCTAssertEqual(store.resolvedFontSize, 20)
        store.nudgeResolvedFont(by: -1)
        XCTAssertEqual(store.resolvedFontSize, 19)
    }
}
