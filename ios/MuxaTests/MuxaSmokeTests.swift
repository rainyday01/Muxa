@testable import Muxa
import XCTest

@MainActor
final class MuxaSmokeTests: XCTestCase {
    func testAppModuleLoads() {
        XCTAssertNotNil(RootView())
    }
}
