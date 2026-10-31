@testable import Muxa
import XCTest

final class ListOrderTests: XCTestCase {
    func testMergeKeepsSavedOrderAndAppendsNewIDs() {
        XCTAssertEqual(
            ListOrder.merge(saved: ["b", "a", "gone"], all: ["a", "b", "c"]),
            ["b", "a", "c"]
        )
    }

    func testMoveSwapsNeighborsAndIgnoresEdges() {
        XCTAssertEqual(ListOrder.move(["a", "b", "c"], id: "b", movingUp: true), ["b", "a", "c"])
        XCTAssertEqual(ListOrder.move(["a", "b", "c"], id: "c", movingUp: false), ["a", "b", "c"])
        XCTAssertEqual(
            ListOrder.sessionKey(hostID: "host", sessionName: "work"),
            "host\twork"
        )
    }
}

@MainActor
final class ThemeAndOrderPreferenceTests: XCTestCase {
    func testResolvesCustomThemesAndPersistsHostOrder() {
        guard let defaults = UserDefaults(suiteName: "muxa.theme-order.\(UUID().uuidString)") else {
            return XCTFail("Could not create a test defaults suite.")
        }
        let store = AppPreferencesStore(defaults: defaults)
        store.appearanceMode = .custom
        store.customThemeID = "paper"
        store.moveHost(id: "b", movingUp: true, allIDs: ["a", "b"])

        XCTAssertEqual(MuxaTheme.all.map(\.id), ["sequoia", "bigsur", "space", "terminal", "vision", "paper"])
        XCTAssertEqual(
            MuxaTheme.resolved(mode: .custom, customID: "paper", system: .dark).displayName,
            "Paper Editorial"
        )
        XCTAssertFalse(MuxaTheme.named("paper").isDark)
        XCTAssertEqual(store.hostOrder, ["b", "a"])

        let reloaded = AppPreferencesStore(defaults: defaults)
        XCTAssertEqual(reloaded.hostOrder, ["b", "a"])
        XCTAssertEqual(reloaded.customThemeID, "paper")
    }
}
